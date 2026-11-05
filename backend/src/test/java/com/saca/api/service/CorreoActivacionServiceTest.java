package com.saca.api.service;

import com.saca.api.service.CorreoActivacionService.CorreoNoDisponibleException;
import com.saca.api.service.CorreoActivacionService.CorreoNoEnviadoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// Contenido del correo de activación y comportamiento sin configuración, sin SMTP real
@ExtendWith(OutputCaptureExtension.class)
class CorreoActivacionServiceTest {

    private static final String REMITENTE = "SACA UES <no-responder@saca.prueba>";
    private static final String URL = "https://saca.prueba";
    private static final String DESTINO = "ab12345@ues.edu.sv";
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";
    private static final LocalDateTime VENCE = LocalDateTime.of(2026, 11, 4, 15, 30);

    private final JavaMailSender clienteSmtp = mock(JavaMailSender.class);

    private SimpleMailMessage enviado() {
        ArgumentCaptor<SimpleMailMessage> captura = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(clienteSmtp).send(captura.capture());
        return captura.getValue();
    }

    @Test
    void enviaElCorreoConElEnlaceEnElFragmentoYLaFechaDeVencimiento() {
        CorreoActivacionService service = new CorreoActivacionService(clienteSmtp, REMITENTE, URL);

        assertThat(service.disponible()).isTrue();
        service.enviarActivacion(DESTINO, TOKEN, VENCE);

        SimpleMailMessage mensaje = enviado();
        assertThat(mensaje.getFrom()).isEqualTo(REMITENTE);
        assertThat(mensaje.getTo()).containsExactly(DESTINO);
        assertThat(mensaje.getSubject()).isEqualTo("Activa tu cuenta de SACA UES");
        assertThat(mensaje.getText())
                .contains("https://saca.prueba/#token=" + TOKEN)
                .doesNotContain("?token")
                .contains("04/11/2026 a las 15:30 (hora de El Salvador)")
                .contains("solo puede usarse una vez")
                .contains("Si no solicitaste esta cuenta, ignora este mensaje");
    }

    @Test
    void laBarraFinalYUnaRutaDelFrontendSeRespetan() {
        CorreoActivacionService service = new CorreoActivacionService(clienteSmtp, REMITENTE, " https://saca.prueba/app/ ");

        assertThat(service.enlace(TOKEN)).isEqualTo("https://saca.prueba/app/#token=" + TOKEN);
    }

    @Test
    void sinClienteSmtpNoEstaDisponibleYNoSimulaElEnvio() {
        CorreoActivacionService service = new CorreoActivacionService((JavaMailSender) null, REMITENTE, URL);

        assertThat(service.disponible()).isFalse();
        assertThatThrownBy(() -> service.enviarActivacion(DESTINO, TOKEN, VENCE))
                .isInstanceOf(CorreoNoDisponibleException.class);
    }

    @Test
    void sinRemitenteOConUnaUrlNoValidaNoEstaDisponible() {
        assertThat(new CorreoActivacionService(clienteSmtp, "", URL).disponible()).isFalse();
        assertThat(new CorreoActivacionService(clienteSmtp, null, URL).disponible()).isFalse();

        for (String url : new String[] {null, "", "  ", "saca.prueba", "ftp://saca.prueba", "javascript:alert(1)",
                "https://saca.prueba/?a=1", "https://saca.prueba/#otro", "https://usuario:clave@saca.prueba", "https://"}) {
            CorreoActivacionService service = new CorreoActivacionService(clienteSmtp, REMITENTE, url);
            assertThat(service.disponible()).as(url).isFalse();
            assertThatThrownBy(() -> service.enviarActivacion(DESTINO, TOKEN, VENCE)).as(url)
                    .isInstanceOf(CorreoNoDisponibleException.class);
        }
        verifyNoInteractions(clienteSmtp);
    }

    @Test
    void elAvisoDeConfiguracionNombraLasVariablesSinMostrarValores(CapturedOutput salida) {
        new CorreoActivacionService((JavaMailSender) null, "remitente-secreto@saca.prueba", "");

        assertThat(salida.getAll())
                .contains("Correo de activación no disponible")
                .contains("SPRING_MAIL_HOST")
                .contains("SACA_FRONTEND_URL")
                .doesNotContain("SACA_CORREO_REMITENTE")
                .doesNotContain("remitente-secreto");
    }

    @Test
    void unFalloSmtpSeInformaSinRegistrarElTokenNiElDestinatario(CapturedOutput salida) {
        doThrow(new MailSendException("Fallo al entregar a " + DESTINO + " con " + TOKEN))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));
        CorreoActivacionService service = new CorreoActivacionService(clienteSmtp, REMITENTE, URL);

        assertThatThrownBy(() -> service.enviarActivacion(DESTINO, TOKEN, VENCE))
                .isInstanceOf(CorreoNoEnviadoException.class)
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining(DESTINO);

        assertThat(salida.getAll())
                .contains("No se pudo enviar un correo de activación (MailSendException)")
                .doesNotContain(TOKEN)
                .doesNotContain(DESTINO);
    }

    @Test
    void unEnvioCorrectoNoRegistraElEnlace(CapturedOutput salida) {
        new CorreoActivacionService(clienteSmtp, REMITENTE, URL).enviarActivacion(DESTINO, TOKEN, VENCE);

        assertThat(salida.getAll()).doesNotContain(TOKEN).doesNotContain(DESTINO);
    }

    @Test
    void conDatosIncompletosNoSeEnviaNada() {
        CorreoActivacionService service = new CorreoActivacionService(clienteSmtp, REMITENTE, URL);

        assertThatThrownBy(() -> service.enviarActivacion(" ", TOKEN, VENCE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarActivacion(DESTINO, null, VENCE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.enviarActivacion(DESTINO, TOKEN, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(clienteSmtp);
    }
}
