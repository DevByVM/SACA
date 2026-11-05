package com.saca.api.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.saca.api.dto.ActualizarPerfilRequest;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.dto.PerfilResponse;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.entity.Estudiante;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.EstudianteRepository;

@Service
public class EstudianteService {

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private TokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Formato oficial del carnet (dos letras y cinco números), validado después de pasar a mayúsculas
    private static final Pattern FORMATO_CARNET = Pattern.compile("^[A-Z]{2}[0-9]{5}$");
    static final String MENSAJE_CARNET_OBLIGATORIO = "Carnet obligatorio";
    static final String MENSAJE_FORMATO_CARNET = "El carnet debe tener dos letras y cinco números, por ejemplo AP23001";
    static final String MENSAJE_CARNET_Y_CORREO_FIJOS = "El carnet y el correo institucional no se pueden modificar desde el perfil";

    private boolean nombreValido(String nombre) {
    return nombre != null
            && nombre.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+(?: [a-zA-ZáéíóúÁÉÍÓÚñÑ]+)*$");
}

    public String registrar(RegistroRequest request) {

        String nombre = normalizarTexto(request.getNombre());
        String correoInstitucional = normalizarCorreo(request.getCorreoInstitucional());
        String carnet = normalizarCarnet(request.getCarnet());

        if (nombre.isBlank()) {
            throw new ValidacionNegocioException("Nombre obligatorio");
        }

        if (!nombreValido(nombre)) {
            throw new ValidacionNegocioException("El nombre solo puede contener letras y espacios");
        }

        if (!correoInstitucional.endsWith("@ues.edu.sv")) {
            throw new ValidacionNegocioException("Correo institucional inválido");
        }

        String errorCarnet = validarFormatoCarnet(carnet);
        if (errorCarnet != null) {
            throw new ValidacionNegocioException(errorCarnet);
        }

        // AP23001 y ap23001 son el mismo carnet
        if (repository.existsByCarnetIgnoreCase(carnet)) {
            throw new ValidacionNegocioException("Carnet ya registrado");
        }

        if (repository.existsByCorreoInstitucional(correoInstitucional)) {
            throw new ValidacionNegocioException("Correo ya registrado");
        }

        if (!contraseniaValida(request.getContrasenia())) {
            throw new ValidacionNegocioException(
                    "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número");
        }

        Estudiante estudiante = new Estudiante();
        estudiante.setNombre(nombre);
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(correoInstitucional);
        estudiante.setContrasenia(passwordEncoder.encode(request.getContrasenia()));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(null);

        repository.save(estudiante);

        return "Usuario registrado correctamente";
    }

    public LoginResponse login(LoginRequest request) {

        String correoInstitucional = normalizarCorreo(request.getCorreoInstitucional());

        Optional<Estudiante> estudianteOpt =
                repository.findByCorreoInstitucional(correoInstitucional);

        if (estudianteOpt.isEmpty()) {
            return LoginResponse.error("Usuario no encontrado");
        }

        Estudiante estudiante = estudianteOpt.get();

        if (!Boolean.TRUE.equals(estudiante.getCuentaActiva())) {
            return LoginResponse.error("Cuenta no activada");
        }

        if (estudiante.getBloqueadoHasta() != null
                && estudiante.getBloqueadoHasta().after(
                        Timestamp.valueOf(LocalDateTime.now()))) {

            return LoginResponse.error("Cuenta bloqueada temporalmente");
        }

        boolean passwordCorrecta =
                esHashBCrypt(estudiante.getContrasenia())
                        && passwordEncoder.matches(
                                request.getContrasenia(),
                                estudiante.getContrasenia());

        if (!passwordCorrecta) {

            int intentosFallidos = estudiante.getIntentosFallidos() == null
                    ? 0
                    : estudiante.getIntentosFallidos();

            estudiante.setIntentosFallidos(intentosFallidos + 1);

            if (estudiante.getIntentosFallidos() >= 3) {
                estudiante.setBloqueadoHasta(
                        Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));

                repository.save(estudiante);

                return LoginResponse.error(
                        "Cuenta bloqueada por 5 minutos");
            }

            repository.save(estudiante);

            return LoginResponse.error("Credenciales incorrectas");
        }

        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(null);

        repository.save(estudiante);

        // Solo se llega aquí con cuenta activa, sin bloqueo y contraseña correcta
        TokenService.TokenEmitido token = tokenService.emitir(estudiante);

        return new LoginResponse(
                true,
                "LOGIN_OK",
                estudiante.getId(),
                estudiante.getNombre(),
                estudiante.getCarnet(),
                estudiante.getCorreoInstitucional(),
                token.valor(),
                token.expiraEn());
    }

    public String actualizarPerfil(Long id, ActualizarPerfilRequest request) {

        Optional<Estudiante> estudianteOpt = repository.findById(id);

        if (estudianteOpt.isEmpty()) {
            return "Usuario no encontrado";
        }

        String nombre = normalizarTexto(request.getNombre());

        // El correo deriva del carnet y ninguno de los dos se modifica desde el perfil. Si se envían, deben
        // coincidir con los guardados (sin distinguir mayúsculas); así un carnet anterior vacío, null o sin el
        // formato oficial se conserva tal cual
        if (modificaCarnetOCorreo(estudianteOpt.get(), request)) {
            return MENSAJE_CARNET_Y_CORREO_FIJOS;
        }

        if (nombre.isBlank()) {
            return "Nombre obligatorio";
        }

        if (!nombreValido(nombre)) {
            return "El nombre solo puede contener letras, espacios, guiones y apóstrofes";
        }

        if (request.getContrasenia() != null
                && !request.getContrasenia().isBlank()) {

            if (!contraseniaValida(request.getContrasenia())) {
                return "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número";
            }

            // Quien solo tenga el token (p. ej. una sesión abierta ajena) no puede cambiar la contraseña
            if (request.getContraseniaActual() == null || request.getContraseniaActual().isBlank()) {
                return "Debe indicar la contraseña actual para cambiarla";
            }

            String hashActual = estudianteOpt.get().getContrasenia();
            if (!esHashBCrypt(hashActual)
                    || !passwordEncoder.matches(request.getContraseniaActual(), hashActual)) {
                return "La contraseña actual es incorrecta";
            }
        }

        Estudiante estudiante = estudianteOpt.get();

        estudiante.setNombre(nombre);

        if (request.getContrasenia() != null
                && !request.getContrasenia().isBlank()) {

            estudiante.setContrasenia(
                    passwordEncoder.encode(request.getContrasenia()));
        }

        repository.save(estudiante);

        return "Perfil actualizado correctamente";
    }

    public PerfilResponse obtenerPerfil(Long id) {
        return repository.findById(id)
                .map(PerfilResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado"));
    }

    private String normalizarCorreo(String correo) {
        return normalizarTexto(correo).toLowerCase();
    }

    private String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim();
    }

    private String normalizarCarnet(String carnet) {
        return normalizarTexto(carnet).toUpperCase(Locale.ROOT);
    }

    // Un campo ausente (null) no cuenta como modificación; null y vacío son el mismo carnet ausente
    private boolean modificaCarnetOCorreo(Estudiante estudiante, ActualizarPerfilRequest request) {
        boolean cambiaCarnet = request.getCarnet() != null
                && !normalizarCarnet(request.getCarnet()).equals(normalizarCarnet(estudiante.getCarnet()));
        boolean cambiaCorreo = request.getCorreoInstitucional() != null
                && !normalizarCorreo(request.getCorreoInstitucional())
                        .equals(normalizarCorreo(estudiante.getCorreoInstitucional()));
        return cambiaCarnet || cambiaCorreo;
    }

    // Devuelve el mensaje de error o null si el carnet (ya normalizado) es válido
    private static String validarFormatoCarnet(String carnet) {
        if (carnet.isEmpty()) {
            return MENSAJE_CARNET_OBLIGATORIO;
        }
        return FORMATO_CARNET.matcher(carnet).matches() ? null : MENSAJE_FORMATO_CARNET;
    }

    private boolean contraseniaValida(String contrasenia) {
        return contrasenia != null
                && contrasenia.matches("^(?=.*[A-Z])(?=.*\\d).{8,}$");
    }

    private boolean esHashBCrypt(String contraseniaAlmacenada) {
        return contraseniaAlmacenada != null
                && contraseniaAlmacenada.length() >= 60
                && contraseniaAlmacenada.startsWith("$2");
    }
}
