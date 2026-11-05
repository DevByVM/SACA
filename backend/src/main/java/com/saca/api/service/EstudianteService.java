package com.saca.api.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    @Autowired
    private VerificadorContrasenias verificadorContrasenias;

    @Autowired
    private Clock relojAplicacion;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Formato oficial del carnet (dos letras y cinco números), validado después de pasar a mayúsculas
    private static final Pattern FORMATO_CARNET = Pattern.compile("^[A-Z]{2}[0-9]{5}$");
    static final String MENSAJE_CARNET_OBLIGATORIO = "Carnet obligatorio";
    static final String MENSAJE_FORMATO_CARNET = "El carnet debe tener dos letras y cinco números, por ejemplo AP23001";
    // Único mensaje de cualquier inicio de sesión fallido
    static final String MENSAJE_CREDENCIALES_INCORRECTAS = "Credenciales incorrectas";
    static final String MENSAJE_CARNET_Y_CORREO_FIJOS = "El carnet y el correo institucional no se pueden modificar desde el perfil";
    static final String MENSAJE_NOMBRE_INVALIDO = "El nombre solo puede contener letras y espacios";
    static final String MENSAJE_CONTRASENIA_INVALIDA =
            "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número";
    static final String MENSAJE_CONTRASENIA_LARGA =
            "La contraseña es demasiado larga: admite hasta 72 bytes (las letras con tilde y la ñ cuentan doble)";

    // Compartidas con RegistroCuentaService (registro y activación)
    static boolean nombreValido(String nombre) {
    return nombre != null
            && nombre.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+(?: [a-zA-ZáéíóúÁÉÍÓÚñÑ]+)*$");
}

    // Todos los fallos responden igual, para no revelar si la cuenta existe, está inactiva, bloqueada
    // o pendiente de activación, ni si la contraseña era la incorrecta. Cada intento hace una comparación BCrypt
    // (real o ficticia) para que el tiempo de respuesta tampoco lo delate. El bloqueo se mantiene: tres fallos
    // seguidos bloquean la cuenta cinco minutos, aunque la respuesta no lo indique.
    //
    // El fallo se reserva con un UPDATE atómico antes de comparar la contraseña y se deshace si resulta correcta
    // (el acierto reinicia el contador). Así, de muchos intentos simultáneos contra una cuenta, como mucho tres
    // llegan a compararse antes del bloqueo. La comparación se hace sin ningún bloqueo de base de datos, y el
    // login nunca guarda la entidad completa.
    public LoginResponse login(LoginRequest request) {

        String correoInstitucional = normalizarCorreo(request.getCorreoInstitucional());

        Optional<Estudiante> estudianteOpt =
                repository.findByCorreoInstitucional(correoInstitucional);

        if (estudianteOpt.isEmpty()) {
            verificadorContrasenias.compararConFicticio(request.getContrasenia());
            return credencialesIncorrectas();
        }

        Estudiante estudiante = estudianteOpt.get();

        if (!Boolean.TRUE.equals(estudiante.getCuentaActiva())) {
            verificadorContrasenias.compararConFicticio(request.getContrasenia());
            return credencialesIncorrectas();
        }

        // Misma zona que los bloqueos ya guardados (la del sistema); el reloj de la aplicación permite probarlo.
        // En milisegundos, que la columna guarda sin redondear: así el fin del bloqueo se compara exacto
        LocalDateTime ahora = LocalDateTime.ofInstant(relojAplicacion.instant(), ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.MILLIS);
        Timestamp bloqueoHasta = Timestamp.valueOf(ahora.plusMinutes(5));

        // Durante el bloqueo no se reserva nada: ni siquiera la contraseña correcta entra, y el intento no se cuenta
        if (repository.reservarIntentoFallido(estudiante.getId(), Timestamp.valueOf(ahora), bloqueoHasta) == 0) {
            verificadorContrasenias.compararConFicticio(request.getContrasenia());
            return credencialesIncorrectas();
        }

        boolean passwordCorrecta = verificadorContrasenias.coincide(
                request.getContrasenia(), estudiante.getContrasenia());

        // El fallo ya quedó contado en la reserva
        if (!passwordCorrecta) {
            return credencialesIncorrectas();
        }

        // Una cuenta pendiente con contraseña conocida (tras una corrección administrativa futura) tampoco entra, y
        // como antes su contraseña correcta no cuenta como fallo. Se comprueba después de la contraseña para que su
        // respuesta y su tiempo sean los de cualquier fallo. La compensación exige que nada más reinicie el
        // contador de una cuenta pendiente mientras dura este inicio de sesión (ver compensarIntentoFallido)
        if (estudiante.estaPendienteDeActivacion()) {
            repository.compensarIntentoFallido(estudiante.getId());
            return credencialesIncorrectas();
        }

        repository.reiniciarIntentos(estudiante.getId());

        // Solo se llega aquí con cuenta activa, sin bloqueo, no pendiente y contraseña correcta
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

    private static LoginResponse credencialesIncorrectas() {
        return LoginResponse.error(MENSAJE_CREDENCIALES_INCORRECTAS);
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
                return MENSAJE_CONTRASENIA_INVALIDA;
            }
            // BCrypt no admite más de 72 bytes: 400, antes de comprobar nada ni escribir
            exigirLongitudContrasenia(request.getContrasenia());

            // Quien solo tenga el token (p. ej. una sesión abierta ajena) no puede cambiar la contraseña
            if (request.getContraseniaActual() == null || request.getContraseniaActual().isBlank()) {
                return "Debe indicar la contraseña actual para cambiarla";
            }

            // El verificador rechaza hashes que no son BCrypt y entradas de más de 72 bytes
            if (!verificadorContrasenias.coincide(request.getContraseniaActual(), estudianteOpt.get().getContrasenia())) {
                return "La contraseña actual es incorrecta";
            }
        }

        // Solo se escriben el nombre y, si cambia, la contraseña, para no deshacer escrituras simultáneas de otras
        // columnas (por ejemplo, el contador de fallos del login). La contraseña solo se sustituye si sigue siendo
        // la que se acaba de comprobar
        if (request.getContrasenia() != null
                && !request.getContrasenia().isBlank()) {

            String nueva = passwordEncoder.encode(request.getContrasenia());
            if (repository.actualizarNombreYContrasenia(
                    id, nombre, estudianteOpt.get().getContrasenia(), nueva) == 0) {
                return "La contraseña actual es incorrecta";
            }
        } else {
            repository.actualizarNombre(id, nombre);
        }

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

    private static String normalizarTexto(String texto) {
        return texto == null ? "" : texto.trim();
    }

    static String normalizarCarnet(String carnet) {
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
    static String validarFormatoCarnet(String carnet) {
        if (carnet.isEmpty()) {
            return MENSAJE_CARNET_OBLIGATORIO;
        }
        return FORMATO_CARNET.matcher(carnet).matches() ? null : MENSAJE_FORMATO_CARNET;
    }

    // Para toda contraseña que se fija (activación y perfil)
    static void exigirLongitudContrasenia(String contrasenia) {
        if (VerificadorContrasenias.excedeLimite(contrasenia)) {
            throw new ValidacionNegocioException(MENSAJE_CONTRASENIA_LARGA);
        }
    }

    static boolean contraseniaValida(String contrasenia) {
        return contrasenia != null
                && contrasenia.matches("^(?=.*[A-Z])(?=.*\\d).{8,}$");
    }
}
