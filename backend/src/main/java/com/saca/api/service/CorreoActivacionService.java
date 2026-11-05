package com.saca.api.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Envío del correo de activación de una cuenta nueva. Si falta la configuración SMTP, el remitente
// o la dirección del frontend, el servicio se declara no disponible y rechaza cualquier envío: nunca
// simula uno. Ni el token ni el enlace se registran; tampoco el destinatario ni el detalle del fallo SMTP,
// que podría incluirlo.
@Service
public class CorreoActivacionService {

    private static final Logger log = LoggerFactory.getLogger(CorreoActivacionService.class);

    static final String ASUNTO = "Activa tu cuenta de SACA UES";
    private static final DateTimeFormatter FORMATO_VENCIMIENTO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.forLanguageTag("es-SV"));

    private final JavaMailSender clienteSmtp;
    private final String remitente;
    private final String urlFrontend;
    private final List<String> faltantes;

    @Autowired
    public CorreoActivacionService(
            ObjectProvider<JavaMailSender> clienteSmtp,
            @Value("${saca.correo.remitente:}") String remitente,
            @Value("${saca.correo.url-frontend:}") String urlFrontend) {
        this(clienteSmtp.getIfAvailable(), remitente, urlFrontend);
    }

    CorreoActivacionService(JavaMailSender clienteSmtp, String remitente, String urlFrontend) {
        this.clienteSmtp = clienteSmtp;
        this.remitente = remitente == null ? "" : remitente.trim();
        this.urlFrontend = normalizarUrl(urlFrontend);
        this.faltantes = calcularFaltantes();
        if (!faltantes.isEmpty()) {
            // Solo los nombres de las variables, nunca sus valores
            log.warn("Correo de activación no disponible: falta configurar {}", String.join(", ", faltantes));
        }
    }

    public boolean disponible() {
        return faltantes.isEmpty();
    }

    public void enviarActivacion(String correoDestino, String token, LocalDateTime expiraEn) {
        if (!disponible()) {
            throw new CorreoNoDisponibleException();
        }
        if (correoDestino == null || correoDestino.isBlank() || token == null || token.isBlank() || expiraEn == null) {
            throw new IllegalArgumentException("Faltan datos para el correo de activación");
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(correoDestino);
        mensaje.setSubject(ASUNTO);
        mensaje.setText(cuerpo(enlace(token), expiraEn));

        try {
            clienteSmtp.send(mensaje);
        } catch (MailException e) {
            // El mensaje de la excepción puede incluir la dirección; solo se registra el tipo de fallo
            log.warn("No se pudo enviar un correo de activación ({})", e.getClass().getSimpleName());
            throw new CorreoNoEnviadoException(e);
        }
    }

    // El token va en el fragmento (#): el navegador no lo envía al servidor ni en el encabezado Referer
    String enlace(String token) {
        return urlFrontend + "/#token=" + token;
    }

    private static String cuerpo(String enlace, LocalDateTime expiraEn) {
        return """
                Hola:

                Recibimos una solicitud para crear una cuenta en SACA UES con este correo institucional.

                Para activarla, abre el siguiente enlace. Allí podrás escribir tu nombre y elegir tu contraseña:

                %s

                El enlace solo puede usarse una vez y vence el %s (hora de El Salvador). Si vence, puedes
                solicitar uno nuevo desde la página de inicio de sesión.

                Si no solicitaste esta cuenta, ignora este mensaje: nadie podrá usarla sin abrir este enlace.

                SACA UES - Sistema de Análisis de Carga Académica
                """.formatted(enlace, FORMATO_VENCIMIENTO.format(expiraEn));
    }

    private List<String> calcularFaltantes() {
        List<String> lista = new ArrayList<>();
        if (clienteSmtp == null) {
            lista.add("SPRING_MAIL_HOST");
        }
        if (remitente.isEmpty()) {
            lista.add("SACA_CORREO_REMITENTE");
        }
        if (urlFrontend.isEmpty()) {
            lista.add("SACA_FRONTEND_URL");
        }
        return List.copyOf(lista);
    }

    // Solo una URL absoluta http(s) sin consulta ni fragmento propios; se quita la barra final
    private static String normalizarUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String limpia = url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        try {
            URI uri = new URI(limpia);
            boolean esHttp = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!esHttp || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawUserInfo() != null) {
                return "";
            }
            return limpia;
        } catch (URISyntaxException e) {
            return "";
        }
    }

    public static class CorreoNoDisponibleException extends IllegalStateException {
        public CorreoNoDisponibleException() {
            super("El envío de correos no está configurado");
        }
    }

    public static class CorreoNoEnviadoException extends RuntimeException {
        public CorreoNoEnviadoException(Throwable causa) {
            super("No se pudo enviar el correo de activación", causa);
        }
    }
}
