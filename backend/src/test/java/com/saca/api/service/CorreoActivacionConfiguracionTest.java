package com.saca.api.service;

import com.saca.api.service.CorreoActivacionService.CorreoNoDisponibleException;
import com.saca.api.service.CorreoActivacionService.CorreoNoEnviadoException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

// La configuración SMTP se toma solo de las propiedades; sin servidor no hay cliente SMTP
// y el servicio se declara no disponible
class CorreoActivacionConfiguracionTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class SinServidorSmtp {

        @Autowired
        private CorreoActivacionService service;

        @Autowired
        private ObjectProvider<JavaMailSender> clienteSmtp;

        @Test
        void laAplicacionArrancaSinClienteSmtpYElCorreoNoEstaDisponible() {
            assertThat(clienteSmtp.getIfAvailable()).isNull();
            assertThat(service.disponible()).isFalse();
            assertThatThrownBy(() -> service.enviarActivacion("ab12345@ues.edu.sv",
                    "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE", LocalDateTime.now()))
                    .isInstanceOf(CorreoNoDisponibleException.class);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class ConServidorSmtpInaccesible {

        // Un puerto local libre en el momento de la prueba: nadie escucha en él
        private static final int PUERTO_CERRADO = puertoLibre();

        @DynamicPropertySource
        static void smtp(DynamicPropertyRegistry registro) {
            registro.add("spring.mail.host", () -> "127.0.0.1");
            registro.add("spring.mail.port", () -> PUERTO_CERRADO);
            registro.add("saca.correo.remitente", () -> "SACA UES <no-responder@saca.prueba>");
            registro.add("saca.correo.url-frontend", () -> "http://localhost:5173");
        }

        @Autowired
        private CorreoActivacionService service;

        @Autowired
        private JavaMailSender clienteSmtp;

        @Test
        void conServidorConfiguradoSeCreaElClienteConLosTiemposDeEspera() {
            assertThat(service.disponible()).isTrue();

            JavaMailSenderImpl cliente = (JavaMailSenderImpl) clienteSmtp;
            assertThat(cliente.getHost()).isEqualTo("127.0.0.1");
            assertThat(cliente.getDefaultEncoding()).isEqualTo("UTF-8");
            assertThat(cliente.getJavaMailProperties())
                    .containsEntry("mail.smtp.connectiontimeout", "5000")
                    .containsEntry("mail.smtp.timeout", "10000")
                    .containsEntry("mail.smtp.writetimeout", "10000")
                    .containsEntry("mail.smtp.auth", "false");
        }

        @Test
        void siElServidorNoRespondeElEnvioFallaSinQuedarBloqueado() {
            assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
                    assertThatThrownBy(() -> service.enviarActivacion("ab12345@ues.edu.sv",
                            "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE", LocalDateTime.now().plusHours(24)))
                            .isInstanceOf(CorreoNoEnviadoException.class));
        }

        private static int puertoLibre() {
            try (ServerSocket socket = new ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
