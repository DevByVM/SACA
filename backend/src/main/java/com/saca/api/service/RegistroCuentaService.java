package com.saca.api.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.saca.api.dto.RegistroRequest;
import com.saca.api.dto.request.ActivacionCuentaRequest;
import com.saca.api.dto.request.ConsultaActivacionRequest;
import com.saca.api.dto.request.ReenvioActivacionRequest;
import com.saca.api.dto.response.ConsultaActivacionResponse;
import com.saca.api.dto.response.CuentaResponse;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.proteccion.ProteccionSolicitudes;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.CorreoActivacionService.CorreoNoDisponibleException;
import com.saca.api.service.CorreoActivacionService.CorreoNoEnviadoException;
import com.saca.api.service.TokenActivacionService.EstadoToken;
import com.saca.api.service.TokenActivacionService.ResultadoToken;
import com.saca.api.service.TokenActivacionService.TokenGenerado;

// Registro con el carnet y activación de la cuenta desde el enlace enviado al correo institucional.
//
// Registro: el correo se deriva del carnet. La respuesta es la misma exista o no la cuenta, y su tiempo también:
// toda solicitud válida calcula el hash pendiente (BCrypt) y el correo se envía fuera de la solicitud.
// - Sin cuenta: se crea PENDIENTE, sin nombre y con el hash de un secreto aleatorio, junto con su token.
// - PENDIENTE: si su último token sigue vigente no se hace nada; si no, se emite otro (invalida el anterior).
// - CONFIRMADO o anterior a la confirmación (NULL): no se toca ni se le escribe.
// Cuenta y token se guardan en una transacción; el correo se programa solo después de confirmarla, así que un
// fallo previo no deja nada a medias y un fallo del envío deja la cuenta pendiente con un token válido.
//
// Reenvío: con el carnet o el correo institucional, la misma respuesta haya o no cuenta. Solo una cuenta PENDIENTE
// recibe otro token (que invalida los anteriores), y como mucho uno por minuto y cinco cada 24 horas contando el
// inicial (ver TokenActivacionService.admiteOtraEmision). Una solicitud que no emite no consume esos cupos. El
// correo se envía igual que el del registro. Sin correo configurado responde lo mismo sin consultar la base.
//
// Envío (registro y reenvío, en el hilo de envío): cupo horario global; marca previa del intento en MySQL (si no
// afecta a una fila, no se llama al SMTP); una sola llamada al SMTP, nunca repetida; y marca del resultado, con
// reintentos breves. Sin la marca del resultado el intento sigue contando 24 horas como si se hubiera enviado:
// MySQL y el SMTP no comparten transacción, y así un fallo entre ambos nunca permite superar el límite.
//
// Activación: nombre y contraseña se validan antes de consumir el token; el consumo y la escritura de la cuenta
// van en la misma transacción. No toca intentos_fallidos ni bloqueado_hasta (condición de diseño de la
// compensación del login, ver EstudianteRepository.compensarIntentoFallido) y no inicia sesión.
//
// Bloqueos: registro, reenvío y activación bloquean primero la fila de la cuenta y después tocan sus tokens.
// Con el mismo orden en las tres, dos de ellas simultáneas sobre la misma cuenta se esperan en lugar de
// interbloquearse (InnoDB abortaría una). El login solo actualiza la fila de la cuenta, sin bloqueos previos.
@Service
public class RegistroCuentaService {

    private static final Logger log = LoggerFactory.getLogger(RegistroCuentaService.class);
    private static final long INTERVALO_AVISOS_MS = 60_000;
    private static final long[] ESPERAS_REINTENTO_MS = {0, 50, 200};

    static final String DOMINIO = "@ues.edu.sv";
    static final String MENSAJE_REGISTRO =
            "Si el carnet no tiene una cuenta activa, recibirás en tu correo institucional un enlace para activarla.";
    static final String MENSAJE_CORREO_NO_CORRESPONDE = "El correo institucional debe ser el de tu carnet: %s";
    static final String MENSAJE_REENVIO =
            "Si hay una cuenta pendiente de activación con esos datos, recibirás un nuevo enlace en tu correo institucional.";
    static final String MENSAJE_IDENTIFICADOR_INVALIDO =
            "Escribe tu carnet (por ejemplo AP23001) o tu correo institucional (por ejemplo ap23001@ues.edu.sv).";
    static final String MENSAJE_ACTIVADA = "Cuenta activada. Ya puedes iniciar sesión.";
    static final String MENSAJE_TOKEN_INVALIDO = "El enlace de activación no es válido.";
    static final String MENSAJE_TOKEN_EXPIRADO =
            "El enlace de activación venció. Solicita uno nuevo desde la página de inicio de sesión.";
    static final String MENSAJE_TOKEN_USADO =
            "Este enlace ya se utilizó. Inicia sesión con tu correo institucional y tu contraseña.";

    private final EstudianteRepository repository;
    private final TokenActivacionService tokens;
    private final ContraseniaPendienteService contraseniaPendiente;
    private final CorreoActivacionService correo;
    private final EnvioCorreoActivacion envio;
    private final ProteccionSolicitudes proteccion;
    private final TransactionTemplate transaccion;
    private final TransactionTemplate transaccionRegistro;
    private final Clock relojAplicacion;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AtomicLong ultimoAvisoLimite = new AtomicLong(Long.MIN_VALUE / 2);
    private final AtomicLong ultimoAvisoSinCorreo = new AtomicLong(Long.MIN_VALUE / 2);

    public RegistroCuentaService(EstudianteRepository repository, TokenActivacionService tokens,
            ContraseniaPendienteService contraseniaPendiente, CorreoActivacionService correo,
            EnvioCorreoActivacion envio, ProteccionSolicitudes proteccion,
            PlatformTransactionManager gestorTransacciones, Clock relojAplicacion) {
        this.repository = repository;
        this.tokens = tokens;
        this.contraseniaPendiente = contraseniaPendiente;
        this.correo = correo;
        this.envio = envio;
        this.proteccion = proteccion;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
        // READ COMMITTED: tras esperar el bloqueo de la fila, la comprobación del token vigente debe ver el que
        // acaba de confirmar otra solicitud. Con REPEATABLE READ (el nivel por defecto de MySQL) seguiría viendo la
        // instantánea de la primera lectura y se emitirían dos tokens
        this.transaccionRegistro = new TransactionTemplate(gestorTransacciones);
        this.transaccionRegistro.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.relojAplicacion = relojAplicacion;
    }

    // ================= registro

    public CuentaResponse registrar(RegistroRequest request) {
        String carnet = EstudianteService.normalizarCarnet(request.getCarnet());
        String errorCarnet = EstudianteService.validarFormatoCarnet(carnet);
        if (errorCarnet != null) {
            throw new ValidacionNegocioException(errorCarnet);
        }
        String correoInstitucional = carnet.toLowerCase(Locale.ROOT) + DOMINIO;
        String correoRecibido = request.getCorreoInstitucional();
        if (correoRecibido != null && !correoRecibido.isBlank()
                && !correoRecibido.trim().toLowerCase(Locale.ROOT).equals(correoInstitucional)) {
            throw new ValidacionNegocioException(MENSAJE_CORREO_NO_CORRESPONDE.formatted(correoInstitucional));
        }
        // Sin correo no se crea nada: la cuenta quedaría pendiente sin forma de activarse
        if (!correo.disponible()) {
            throw new CorreoNoDisponibleException();
        }

        // Fuera de la transacción y siempre, para que el tiempo no dependa de si la cuenta existe
        String hashPendiente = contraseniaPendiente.generarHash();
        try {
            TokenGenerado token = transaccionRegistro.execute(
                    estado -> prepararCuenta(carnet, correoInstitucional, hashPendiente));
            if (token != null) {
                programarEnvio(correoInstitucional, token);
            }
        } catch (DataIntegrityViolationException e) {
            // Otra solicitud creó la cuenta a la vez (carnet y correo son únicos): esa envía el correo
        }
        return new CuentaResponse(MENSAJE_REGISTRO, correoInstitucional);
    }

    // Devuelve el token que hay que enviar, o null si no hay que enviar nada
    private TokenGenerado prepararCuenta(String carnet, String correoInstitucional, String hashPendiente) {
        List<Long> encontrados = repository.idsPorCarnetOCorreo(carnet, correoInstitucional);
        if (encontrados.isEmpty()) {
            Estudiante nuevo = new Estudiante();
            nuevo.setCarnet(carnet);
            nuevo.setCorreoInstitucional(correoInstitucional);
            nuevo.setContrasenia(hashPendiente);
            nuevo.setCuentaActiva(true);
            nuevo.setIntentosFallidos(0);
            nuevo.setEstadoCorreo(EstadoCorreo.PENDIENTE);
            return tokens.generar(repository.saveAndFlush(nuevo));
        }
        // Carnet y correo en cuentas distintas (datos anteriores): no se toca ninguna
        if (encontrados.size() > 1) {
            return null;
        }
        // La cuenta se carga ya bloqueada (bloqueo breve, sin BCrypt dentro): así se decide con su estado actual y
        // dos registros simultáneos de la misma cuenta pendiente no emiten dos tokens
        Estudiante existente = repository.bloquearPorId(encontrados.get(0)).orElseThrow();
        if (!existente.estaPendienteDeActivacion() || !correoInstitucional.equals(existente.getCorreoInstitucional())
                || tokens.tieneVigente(existente)) {
            return null;
        }
        return tokens.generar(existente);
    }

    // ================= reenvío

    public CuentaResponse reenviar(ReenvioActivacionRequest request) {
        String carnet = carnetDeIdentificador(request == null ? null : request.identificador());
        String correoInstitucional = carnet.toLowerCase(Locale.ROOT) + DOMINIO;
        // Sin correo configurado no se emite nada ni se consulta la cuenta: la respuesta es la de siempre y no
        // depende de ella (a diferencia del registro, que responde 503)
        if (!correo.disponible()) {
            avisar(ultimoAvisoSinCorreo, "Reenvío de activación sin correo configurado: no se envía nada");
            return new CuentaResponse(MENSAJE_REENVIO, correoInstitucional);
        }
        TokenGenerado token = transaccionRegistro.execute(estado -> prepararReenvio(carnet, correoInstitucional));
        if (token != null) {
            programarEnvio(correoInstitucional, token);
        }
        return new CuentaResponse(MENSAJE_REENVIO, correoInstitucional);
    }

    // El carnet, o el correo institucional derivado de él (carnet en minúsculas + @ues.edu.sv); nunca otro correo
    static String carnetDeIdentificador(String identificador) {
        String texto = identificador == null ? "" : identificador.trim();
        if (texto.isEmpty()) {
            throw new ValidacionNegocioException(MENSAJE_IDENTIFICADOR_INVALIDO);
        }
        String candidato = texto;
        if (texto.indexOf('@') >= 0) {
            String correoRecibido = texto.toLowerCase(Locale.ROOT);
            if (!correoRecibido.endsWith(DOMINIO)) {
                throw new ValidacionNegocioException(MENSAJE_IDENTIFICADOR_INVALIDO);
            }
            candidato = correoRecibido.substring(0, correoRecibido.length() - DOMINIO.length());
            // En un correo no se admiten espacios (la normalización del carnet los recortaría)
            if (!candidato.equals(candidato.strip())) {
                throw new ValidacionNegocioException(MENSAJE_IDENTIFICADOR_INVALIDO);
            }
        }
        String carnet = EstudianteService.normalizarCarnet(candidato);
        if (EstudianteService.validarFormatoCarnet(carnet) != null) {
            throw new ValidacionNegocioException(MENSAJE_IDENTIFICADOR_INVALIDO);
        }
        return carnet;
    }

    // Devuelve el token que hay que enviar, o null. Con la cuenta bloqueada, las comprobaciones de estado y de
    // cupos y la emisión no pueden intercalarse con otro reenvío, un registro o una activación de la misma cuenta
    private TokenGenerado prepararReenvio(String carnet, String correoInstitucional) {
        List<Long> encontrados = repository.idsPorCarnetOCorreo(carnet, correoInstitucional);
        if (encontrados.size() != 1) {
            return null;
        }
        Estudiante cuenta = repository.bloquearPorId(encontrados.get(0)).orElseThrow();
        if (!cuenta.estaPendienteDeActivacion() || !correoInstitucional.equals(cuenta.getCorreoInstitucional())
                || !tokens.admiteOtraEmision(cuenta)) {
            return null;
        }
        return tokens.generar(cuenta);
    }

    // Siempre después del commit. Si la cola no lo admite, el token queda descartado (no cuenta) y vigente
    private void programarEnvio(String correoInstitucional, TokenGenerado token) {
        envio.programar(() -> enviar(correoInstitucional, token),
                () -> persistir(() -> tokens.registrarDescartadoSinIntento(token.id())));
    }

    // En el hilo de envío. El cupo horario se reserva justo antes, así que cuenta envíos reales (también los que el
    // SMTP rechaza). Sin cupo, o caducado, no se envía ni se aplaza: el token queda descartado y vigente, y la
    // cuenta pendiente, para el reenvío
    private void enviar(String correoInstitucional, TokenGenerado token) {
        if (!proteccion.reservarEnvioCorreo()) {
            avisar(ultimoAvisoLimite, "Límite global de correos alcanzado: no se envía un correo de activación");
            persistir(() -> tokens.registrarDescartadoSinIntento(token.id()));
            return;
        }
        // La marca previa no se reintenta: si falla, no se llama al SMTP y el correo sigue en cola (cuenta 15
        // minutos). Si no afecta a la fila (caducado o ya intentado por otra ejecución del mismo trabajo), tampoco
        boolean intento;
        try {
            intento = tokens.registrarIntento(token.id());
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el intento de un correo de activación ({}): no se envía",
                    e.getClass().getSimpleName());
            return;
        }
        if (!intento) {
            persistir(() -> tokens.registrarDescartadoSinIntento(token.id()));
            return;
        }
        try {
            correo.enviarActivacion(correoInstitucional, token.valor(), token.expiraEn());
        } catch (CorreoNoEnviadoException e) {
            persistir(() -> tokens.registrarFallido(token.id()));
            throw e;
        }
        persistir(() -> tokens.registrarEnviado(token.id()));
    }

    // Reintentos breves de una marca idempotente. Si ninguno lo consigue, el estado anterior se conserva: un intento
    // sin resultado cuenta 24 horas, que es el lado seguro
    private void persistir(Runnable marca) {
        RuntimeException ultimo = null;
        for (long espera : ESPERAS_REINTENTO_MS) {
            try {
                if (espera > 0) {
                    Thread.sleep(espera);
                }
                marca.run();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                ultimo = e;
            }
        }
        log.warn("No se pudo guardar el estado de un correo de activación ({}): se conserva el anterior",
                ultimo == null ? "InterruptedException" : ultimo.getClass().getSimpleName());
    }

    // Como mucho un aviso por minuto de cada tipo, sin datos de la cuenta
    private void avisar(AtomicLong ultimoAviso, String mensaje) {
        long ahora = relojAplicacion.millis();
        long anterior = ultimoAviso.get();
        if (ahora - anterior >= INTERVALO_AVISOS_MS && ultimoAviso.compareAndSet(anterior, ahora)) {
            log.warn(mensaje);
        }
    }

    // ================= activación

    public ConsultaActivacionResponse consultar(ConsultaActivacionRequest request) {
        ResultadoToken resultado = tokens.consultar(request == null ? null : request.token());
        if (resultado.estado() == EstadoToken.VALIDO) {
            Estudiante estudiante = resultado.estudiante();
            if (!estudiante.estaPendienteDeActivacion()) {
                return ConsultaActivacionResponse.sinDatos(EstadoToken.INVALIDO.name());
            }
            return new ConsultaActivacionResponse(EstadoToken.VALIDO.name(), estudiante.getCarnet(),
                    estudiante.getCorreoInstitucional());
        }
        return ConsultaActivacionResponse.sinDatos(resultado.estado().name());
    }

    public CuentaResponse activar(ActivacionCuentaRequest request) {
        String nombre = request.nombre() == null ? "" : request.nombre().trim();
        if (nombre.isEmpty()) {
            throw new ValidacionNegocioException("Nombre obligatorio");
        }
        // La columna admite 120 caracteres
        if (nombre.length() > 120) {
            throw new ValidacionNegocioException("El nombre no debe superar 120 caracteres");
        }
        if (!EstudianteService.nombreValido(nombre)) {
            throw new ValidacionNegocioException(EstudianteService.MENSAJE_NOMBRE_INVALIDO);
        }
        if (!EstudianteService.contraseniaValida(request.contrasenia())) {
            throw new ValidacionNegocioException(EstudianteService.MENSAJE_CONTRASENIA_INVALIDA);
        }
        EstudianteService.exigirLongitudContrasenia(request.contrasenia());
        // BCrypt antes de la transacción: el consumo del token y la escritura de la cuenta duran milisegundos
        String hash = passwordEncoder.encode(request.contrasenia());

        String correoInstitucional = transaccion.execute(estado -> {
            // Primero la fila de la cuenta, como el registro y el reenvío (ver Bloqueos)
            ResultadoToken previo = tokens.consultar(request.token());
            if (previo.estudiante() == null) {
                throw new ValidacionNegocioException(mensajeToken(previo.estado()));
            }
            repository.bloquearPorId(previo.estudiante().getId());
            ResultadoToken resultado = tokens.consumir(request.token());
            if (resultado.estado() != EstadoToken.CONSUMIDO) {
                throw new ValidacionNegocioException(mensajeToken(resultado.estado()));
            }
            Estudiante estudiante = resultado.estudiante();
            LocalDateTime ahora = LocalDateTime.now(relojAplicacion).truncatedTo(ChronoUnit.MICROS);
            // Solo si sigue pendiente; si no, la excepción deshace también el consumo del token
            if (repository.activar(estudiante.getId(), nombre, hash, ahora) == 0) {
                throw new ValidacionNegocioException(MENSAJE_TOKEN_INVALIDO);
            }
            return estudiante.getCorreoInstitucional();
        });
        return new CuentaResponse(MENSAJE_ACTIVADA, correoInstitucional);
    }

    private static String mensajeToken(EstadoToken estado) {
        return switch (estado) {
            case EXPIRADO -> MENSAJE_TOKEN_EXPIRADO;
            case USADO -> MENSAJE_TOKEN_USADO;
            default -> MENSAJE_TOKEN_INVALIDO;
        };
    }
}
