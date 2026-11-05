package com.saca.api.controllers;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.dto.request.ReenvioActivacionRequest;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
import com.saca.api.service.EnvioCorreoActivacion;
import com.saca.api.service.RegistroCuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

// Límite global de correos por hora (aquí SACA_LIMITE_CORREO_HORA=2) en el envío de activación.
// El envío se ejecuta en el acto, después del commit, como en producción pero sin el hilo de fondo (la cola se
// prueba en EnvioCorreoActivacionColaTest). Cada prueba adelanta el reloj dos horas para empezar con el cupo libre
@SpringBootTest(properties = {
        "SACA_CORREO_REMITENTE=saca@pruebas.ues.edu.sv",
        "SACA_FRONTEND_URL=https://saca.pruebas.local",
        "SACA_LIMITE_CORREO_HORA=2"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LimiteCorreoActivacionTest {

    private static final String AVISO_LIMITE = "Límite global de correos alcanzado: no se envía un correo de activación";
    private static final String AVISO_SMTP = "No se pudo enviar un correo de activación";

    @TestConfiguration
    static class Configuracion {
        @Bean
        JavaMailSender clienteSmtpDePrueba() {
            return mock(JavaMailSender.class);
        }

        @Bean
        @Primary
        EnvioCorreoActivacion envioInmediato() {
            return new EnvioCorreoActivacion() {
                @Override
                public void programar(Runnable envio, Runnable siDescartado) {
                    ejecutar(envio);
                }
            };
        }

        @Bean
        @Primary
        RelojAjustable relojCorreo() {
            return new RelojAjustable(Instant.now(), ZoneId.of("America/El_Salvador"));
        }
    }

    @Autowired
    private RegistroCuentaService registroCuenta;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    @Autowired
    private JavaMailSender clienteSmtp;

    @Autowired
    private RelojAjustable reloj;

    private final List<String> enviados = new CopyOnWriteArrayList<>();

    @BeforeEach
    void preparar() {
        reloj.avanzar(Duration.ofHours(2));
        enviados.clear();
        reset(clienteSmtp);
        doAnswer(invocacion -> enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));
    }

    private String registrar() {
        String carnet = CarnetsDePrueba.nuevo("LC");
        RegistroRequest request = new RegistroRequest();
        request.setCarnet(carnet);
        registroCuenta.registrar(request);
        return carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv";
    }

    private void reenviar(String correo) {
        registroCuenta.reenviar(new ReenvioActivacionRequest(correo));
    }

    private void assertPendienteConTokenVigente(String correo) {
        Estudiante estudiante = estudianteRepository.findByCorreoInstitucional(correo).orElseThrow();
        assertThat(estudiante.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(estudiante.getId()))
                .singleElement().satisfies(token -> {
                    assertThat(token.getUsadoEn()).isNull();
                    assertThat(token.getInvalidadoEn()).isNull();
                });
    }

    private static int veces(CapturedOutput salida, String texto) {
        String todo = salida.getAll();
        int veces = 0;
        for (int i = todo.indexOf(texto); i >= 0; i = todo.indexOf(texto, i + texto.length())) {
            veces++;
        }
        return veces;
    }

    // Con el cupo agotado no se envía ni se aplaza: la cuenta queda pendiente con su token para el reenvío, y el
    // aviso, sin datos de la cuenta, sale como mucho una vez por minuto
    @Test
    void conElCupoAgotadoNoSeEnviaYLaCuentaQuedaPendiente(CapturedOutput salida) {
        String primero = registrar();
        String segundo = registrar();
        String tercero = registrar();
        String cuarto = registrar();

        assertThat(enviados).containsExactly(primero, segundo);
        assertPendienteConTokenVigente(tercero);
        assertPendienteConTokenVigente(cuarto);
        assertThat(veces(salida, AVISO_LIMITE)).isEqualTo(1);
        assertThat(salida.getAll()).doesNotContain(tercero).doesNotContain(cuarto);

        reloj.avanzar(Duration.ofMinutes(1));
        registrar();
        assertThat(veces(salida, AVISO_LIMITE)).isEqualTo(2);
        assertThat(enviados).hasSize(2);
    }

    // El cupo es una ventana deslizante de una hora: al cumplirse desde los primeros envíos, se vuelve a enviar
    @Test
    void unaHoraDespuesSeVuelveAEnviar() {
        registrar();
        reloj.avanzar(Duration.ofMinutes(20));
        registrar();
        assertThat(enviados).hasSize(2);

        reloj.avanzar(Duration.ofMinutes(39));
        String sinCupo = registrar();
        assertThat(enviados).hasSize(2);
        assertPendienteConTokenVigente(sinCupo);

        reloj.avanzar(Duration.ofMinutes(1));
        String conCupo = registrar();
        assertThat(enviados).hasSize(3).last().isEqualTo(conCupo);
    }

    // Un fallo del SMTP consume su cupo (no se reintenta) y deja un solo aviso
    @Test
    void unFalloDelSmtpConsumeCupoYDejaUnSoloAviso(CapturedOutput salida) {
        doAnswer(invocacion -> {
            throw new MailSendException("smtp no disponible");
        }).doAnswer(invocacion -> enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));

        String fallido = registrar();
        String enviado = registrar();
        String sinCupo = registrar();

        assertThat(enviados).containsExactly(enviado);
        assertPendienteConTokenVigente(fallido);
        assertPendienteConTokenVigente(sinCupo);
        assertThat(veces(salida, AVISO_SMTP)).isEqualTo(1);
        assertThat(salida.getAll()).doesNotContain("Falló un envío de correo de activación");
        assertThat(veces(salida, AVISO_LIMITE)).isEqualTo(1);
    }

    // El reenvío usa el mismo cupo horario: sin cupo emite el token pero no lo envía ni lo aplaza. Queda descartado
    // (no cuenta en el límite por cuenta) y vigente, y la cuenta sigue pendiente
    @Test
    void elReenvioRespetaElLimiteGlobalPorHora(CapturedOutput salida) {
        String primero = registrar();
        String segundo = registrar();
        reloj.avanzar(Duration.ofMinutes(2));

        reenviar(primero);

        assertThat(enviados).containsExactly(primero, segundo);
        Estudiante pendiente = estudianteRepository.findByCorreoInstitucional(primero).orElseThrow();
        assertThat(pendiente.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(pendiente.getId()))
                .hasSize(2).last().satisfies(token -> {
                    assertThat(token.getInvalidadoEn()).isNull();
                    assertThat(token.getEnvioIntentadoEn()).isNull();
                    assertThat(token.getEnvioDescartadoEn()).isNotNull();
                });
        assertThat(veces(salida, AVISO_LIMITE)).isEqualTo(1);

        // Al liberarse el cupo, el siguiente reenvío permitido sí sale
        reloj.avanzar(Duration.ofHours(1));
        reenviar(primero);
        assertThat(enviados).containsExactly(primero, segundo, primero);
    }

    // Un reenvío cuyo SMTP falla consume cupo horario, deja un solo aviso y la cuenta conserva vigente el token
    // emitido, que queda descartado (no cuenta en el límite por cuenta)
    @Test
    void unReenvioConFalloDelSmtpConsumeCupoYDejaUnSoloAviso(CapturedOutput salida) {
        String cuenta = registrar();
        reloj.avanzar(Duration.ofMinutes(2));
        doAnswer(invocacion -> {
            throw new MailSendException("smtp no disponible");
        }).when(clienteSmtp).send(any(SimpleMailMessage.class));

        reenviar(cuenta);
        reset(clienteSmtp);
        doAnswer(invocacion -> enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));
        String sinCupo = registrar();

        assertThat(enviados).containsExactly(cuenta);
        assertThat(veces(salida, AVISO_SMTP)).isEqualTo(1);
        assertThat(salida.getAll()).doesNotContain("Falló un envío de correo de activación");
        Estudiante pendiente = estudianteRepository.findByCorreoInstitucional(cuenta).orElseThrow();
        assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(pendiente.getId()))
                .hasSize(2).last().satisfies(token -> {
                    assertThat(token.getInvalidadoEn()).isNull();
                    assertThat(token.getEnvioIntentadoEn()).isNotNull();
                    assertThat(token.getEnvioDescartadoEn()).isNotNull();
                });
        assertPendienteConTokenVigente(sinCupo);
    }
}
