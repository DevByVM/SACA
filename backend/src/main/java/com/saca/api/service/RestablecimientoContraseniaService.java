package com.saca.api.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.RestablecerContraseniaRequest;
import com.saca.api.dto.response.CuentaResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.TokenRestablecimiento;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.proteccion.LimiteSolicitudesExcedidoException;
import com.saca.api.proteccion.ProteccionSolicitudes;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenRestablecimientoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

@Service
public class RestablecimientoContraseniaService {
    @PersistenceContext
    private EntityManager entityManager;
    // Ventana breve para una operación solicitada por administración.
    static final Duration VIGENCIA = Duration.ofHours(1);
    private final EstudianteRepository estudiantes;
    private final TokenRestablecimientoRepository tokens;
    private final CorreoActivacionService correo;
    private final Clock reloj;
    private final ProteccionSolicitudes proteccion;
    private final SecureRandom aleatorio = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public RestablecimientoContraseniaService(EstudianteRepository estudiantes,
            TokenRestablecimientoRepository tokens, CorreoActivacionService correo, Clock reloj,
            ProteccionSolicitudes proteccion) {
        this.estudiantes = estudiantes;
        this.tokens = tokens;
        this.correo = correo;
        this.reloj = reloj;
        this.proteccion = proteccion;
    }

    @Transactional
    public CuentaResponse solicitar(Long id) {
        Estudiante estudiante = estudiantes.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la cuenta solicitada"));
        if (estudiante.estaPendienteDeActivacion()) {
            throw new ValidacionNegocioException("La cuenta debe activarse antes de restablecer su contraseña.");
        }
        if (!correo.disponible()) throw new CorreoActivacionService.CorreoNoDisponibleException();
        if (!proteccion.reservarEnvioCorreo()) {
            throw new LimiteSolicitudesExcedidoException("Se alcanzó el límite de correos. Inténtalo más tarde.", Duration.ofHours(1));
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        tokens.invalidar(id, ahora);
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TokenRestablecimiento token = tokens.save(new TokenRestablecimiento(estudiante,
                TokenActivacionService.hash(valor), ahora.plus(VIGENCIA)));
        // El SMTP es síncrono: un rechazo revierte la emisión sin cambiar contraseña ni estado.
        correo.enviarRestablecimiento(estudiante.getCorreoInstitucional(), valor, token.getExpiraEn());
        return new CuentaResponse("Se envió el enlace de restablecimiento al correo institucional.",
                estudiante.getCorreoInstitucional());
    }

    @Transactional(readOnly = true)
    public String consultar(String valor) {
        return estado(buscar(valor));
    }

    @Transactional
    public CuentaResponse restablecer(RestablecerContraseniaRequest request) {
        if (!EstudianteService.contraseniaValida(request.contrasenia())) {
            throw new ValidacionNegocioException(EstudianteService.MENSAJE_CONTRASENIA_INVALIDA);
        }
        EstudianteService.exigirLongitudContrasenia(request.contrasenia());
        String hash = encoder.encode(request.contrasenia());
        TokenRestablecimiento previo = buscar(request.token());
        if (previo == null) throw enlaceInvalido();
        // Todos los cambios de cuenta y tokens toman primero el bloqueo de la cuenta.
        Estudiante estudiante = estudiantes.bloquearPorId(previo.getEstudiante().getId()).orElseThrow();
        // Refresca el token después del bloqueo: otra transacción puede haberlo consumido o invalidado.
        entityManager.refresh(estudiante, LockModeType.PESSIMISTIC_WRITE);
        TokenRestablecimiento token = tokens.findById(previo.getId()).orElseThrow();
        entityManager.refresh(token, LockModeType.PESSIMISTIC_WRITE);
        if (!"VALIDO".equals(estado(token))) throw enlaceInvalido();
        if (estudiante.estaPendienteDeActivacion()) throw enlaceInvalido();
        if (encoder.matches(request.contrasenia(), estudiante.getContrasenia())) {
            throw new ValidacionNegocioException("La nueva contraseña debe ser diferente de la anterior.");
        }
        token.usar(LocalDateTime.now(reloj));
        estudiante.setContrasenia(hash);
        // No cambia habilitación, activación ni contadores/bloqueo de login.
        return new CuentaResponse("Contraseña restablecida. Ya puedes iniciar sesión.", estudiante.getCorreoInstitucional());
    }

    private TokenRestablecimiento buscar(String valor) {
        if (valor == null || !valor.matches("^[A-Za-z0-9_-]{43}$")) return null;
        return tokens.findByHashToken(TokenActivacionService.hash(valor)).orElse(null);
    }

    private String estado(TokenRestablecimiento token) {
        if (token == null) return "INVALIDO";
        if (token.getUsadoEn() != null) return "USADO";
        if (token.getInvalidadoEn() != null) return "INVALIDO";
        return token.getExpiraEn().isAfter(LocalDateTime.now(reloj)) ? "VALIDO" : "EXPIRADO";
    }

    private ValidacionNegocioException enlaceInvalido() {
        return new ValidacionNegocioException("El enlace no es válido, ya se utilizó o venció. Solicita otro a administración.");
    }
}
