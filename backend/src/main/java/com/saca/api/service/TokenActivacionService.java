package com.saca.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.entity.Estudiante;
import com.saca.api.entity.TokenActivacion;
import com.saca.api.repository.TokenActivacionRepository;

// Tokens de activación de cuentas nuevas. El valor en claro se devuelve una sola vez, al generarlo,
// para construir el enlace; en la base solo queda su SHA-256. Este servicio no registra tokens ni hashes.
@Service
public class TokenActivacionService {

    static final Duration VIGENCIA = Duration.ofHours(24);
    // Límites por cuenta del reenvío. El de un minuto cuenta emisiones de tokens; el de cinco cada 24 horas,
    // correos (ver contarCorreos), el inicial incluido
    static final Duration INTERVALO_MINIMO_EMISIONES = Duration.ofMinutes(1);
    static final Duration VENTANA_CORREOS = Duration.ofHours(24);
    static final int MAXIMO_CORREOS_POR_VENTANA = 5;
    // Un correo en cola cuenta 15 minutos; el hilo de envío no lo intenta pasados 10, así que nunca se envía uno
    // que ya dejó de contar (el margen cubre los tiempos de espera del SMTP)
    static final Duration HORIZONTE_EN_COLA = Duration.ofMinutes(15);
    static final Duration CADUCIDAD_ENVIO = Duration.ofMinutes(10);
    private static final int BYTES_TOKEN = 32;
    // 32 bytes en Base64 URL-safe sin relleno son exactamente 43 caracteres de ese alfabeto
    private static final Pattern FORMATO_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Base64.Encoder CODIFICADOR = Base64.getUrlEncoder().withoutPadding();

    private final TokenActivacionRepository repository;
    private final Clock relojAplicacion;
    private final SecureRandom aleatorio = new SecureRandom();

    public TokenActivacionService(TokenActivacionRepository repository, Clock relojAplicacion) {
        this.repository = repository;
        this.relojAplicacion = relojAplicacion;
    }

    // Emite un token nuevo e invalida, en la misma transacción, los que el estudiante aún podía usar
    @Transactional
    public TokenGenerado generar(Estudiante estudiante) {
        LocalDateTime ahora = ahora();
        repository.invalidarVigentes(estudiante.getId(), ahora);

        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        String valor = CODIFICADOR.encodeToString(bytes);

        TokenActivacion token = new TokenActivacion(estudiante, hash(valor), ahora, ahora.plus(VIGENCIA));
        repository.save(token);
        return new TokenGenerado(token.getId(), valor, token.getExpiraEn());
    }

    // Si el estudiante tiene un token que aún puede usarse (sin usar, sin sustituir y sin vencer)
    @Transactional(readOnly = true)
    public boolean tieneVigente(Estudiante estudiante) {
        return repository.existsByEstudianteIdAndUsadoEnIsNullAndInvalidadoEnIsNullAndExpiraEnAfter(
                estudiante.getId(), ahora());
    }

    // Si la cuenta puede recibir otro correo: ninguna emisión en el último minuto y menos de cinco correos que
    // cuenten (enviados, intentados sin resultado o en cola), en ventanas móviles. Quien llama debe tener
    // bloqueada la cuenta para que dos solicitudes no lo comprueben a la vez. Como cada correo pasa de en cola a
    // intentado y a enviado sin dejar de contar entre medias, una comprobación nunca lo pierde de vista aunque el
    // hilo de envío (de esta u otra instancia) lo esté procesando
    @Transactional(readOnly = true)
    public boolean admiteOtraEmision(Estudiante estudiante) {
        LocalDateTime ahora = ahora();
        Long id = estudiante.getId();
        return repository.countByEstudianteIdAndCreadoEnAfter(id, ahora.minus(INTERVALO_MINIMO_EMISIONES)) == 0
                && correosQueCuentan(id, ahora) < MAXIMO_CORREOS_POR_VENTANA;
    }

    long correosQueCuentan(Long estudianteId, LocalDateTime ahora) {
        return repository.contarCorreos(estudianteId, ahora.minus(VENTANA_CORREOS), ahora.minus(HORIZONTE_EN_COLA));
    }

    // ================= estado del envío (una sentencia cada uno; los reintentos los decide quien llama)

    // true si el correo puede intentarse: no estaba intentado ni descartado y no ha caducado
    public boolean registrarIntento(Long tokenId) {
        LocalDateTime ahora = ahora();
        return repository.marcarIntento(tokenId, ahora, ahora.minus(CADUCIDAD_ENVIO)) == 1;
    }

    public void registrarEnviado(Long tokenId) {
        repository.marcarEnviado(tokenId, ahora());
    }

    public void registrarFallido(Long tokenId) {
        repository.marcarFallido(tokenId, ahora());
    }

    public void registrarDescartadoSinIntento(Long tokenId) {
        repository.marcarDescartadoSinIntento(tokenId, ahora());
    }

    // Informa del estado del token sin consumirlo
    @Transactional(readOnly = true)
    public ResultadoToken consultar(String valor) {
        if (!formatoValido(valor)) {
            return ResultadoToken.de(EstadoToken.INVALIDO);
        }
        return clasificar(repository.findByHashToken(hash(valor)), ahora());
    }

    // Consume el token si sigue vigente. Solo una llamada puede devolver CONSUMIDO para un mismo token
    @Transactional
    public ResultadoToken consumir(String valor) {
        if (!formatoValido(valor)) {
            return ResultadoToken.de(EstadoToken.INVALIDO);
        }
        String hashToken = hash(valor);
        LocalDateTime ahora = ahora();

        if (repository.consumir(hashToken, ahora) == 1) {
            TokenActivacion token = repository.findByHashToken(hashToken).orElseThrow();
            return new ResultadoToken(EstadoToken.CONSUMIDO, token.getEstudiante());
        }
        return clasificar(repository.findByHashToken(hashToken), ahora);
    }

    // Un token usado se informa como USADO aunque además haya vencido o se haya invalidado después
    private static ResultadoToken clasificar(Optional<TokenActivacion> encontrado, LocalDateTime ahora) {
        if (encontrado.isEmpty()) {
            return ResultadoToken.de(EstadoToken.INVALIDO);
        }
        TokenActivacion token = encontrado.get();
        if (token.getUsadoEn() != null) {
            return new ResultadoToken(EstadoToken.USADO, token.getEstudiante());
        }
        if (token.getInvalidadoEn() != null) {
            return ResultadoToken.de(EstadoToken.INVALIDO);
        }
        if (!token.getExpiraEn().isAfter(ahora)) {
            return new ResultadoToken(EstadoToken.EXPIRADO, token.getEstudiante());
        }
        return new ResultadoToken(EstadoToken.VALIDO, token.getEstudiante());
    }

    // Las fechas se guardan con microsegundos (datetime(6)); truncar evita comparar con nanosegundos que la base descarta
    private LocalDateTime ahora() {
        return LocalDateTime.now(relojAplicacion).truncatedTo(ChronoUnit.MICROS);
    }

    // También evita calcular el hash de entradas arbitrariamente largas
    private static boolean formatoValido(String valor) {
        return valor != null && FORMATO_TOKEN.matcher(valor).matches();
    }

    static String hash(String valor) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM debe incluir SHA-256
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // El valor no debe registrarse: toString lo omite
    public record TokenGenerado(Long id, String valor, LocalDateTime expiraEn) {
        @Override
        public String toString() {
            return "TokenGenerado[id=" + id + ", expiraEn=" + expiraEn + "]";
        }
    }

    public enum EstadoToken {
        VALIDO,
        CONSUMIDO,
        USADO,
        EXPIRADO,
        INVALIDO
    }

    // Un token inválido (inexistente o sustituido) no revela a qué cuenta pertenecía
    public record ResultadoToken(EstadoToken estado, Estudiante estudiante) {
        static ResultadoToken de(EstadoToken estado) {
            return new ResultadoToken(estado, null);
        }
    }
}
