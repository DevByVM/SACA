package com.saca.api.service;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.dto.request.ReenvioActivacionRequest;
import com.saca.api.dto.response.CuentaResponse;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

// La cola de envíos y el límite horario son controles independientes. Con el hilo de envío real
// y una cola de un solo elemento, un envío retenido en el SMTP llena la cola y el siguiente se descarta sin error
// y sin consumir cupo horario (aquí SACA_LIMITE_CORREO_HORA=3). Cada prueba adelanta el reloj dos horas para
// empezar con el cupo libre
@SpringBootTest(properties = {
        "SACA_CORREO_REMITENTE=saca@pruebas.ues.edu.sv",
        "SACA_FRONTEND_URL=https://saca.pruebas.local",
        "SACA_LIMITE_CORREO_HORA=3"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class EnvioCorreoActivacionColaTest {

    private static final String AVISO_COLA = "Cola de correos de activación llena: se descarta un envío";

    @TestConfiguration
    static class Configuracion {
        @Bean
        JavaMailSender clienteSmtpDePrueba() {
            return mock(JavaMailSender.class);
        }

        @Bean
        @Primary
        EnvioCorreoActivacion colaDeUnElemento() {
            return new EnvioCorreoActivacion(1);
        }

        @Bean
        @Primary
        RelojAjustable relojCola() {
            return new RelojAjustable(Instant.now(), ZoneId.of("America/El_Salvador"));
        }
    }

    @Autowired
    private RegistroCuentaService registroCuenta;

    @Autowired
    private EnvioCorreoActivacion envio;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    @Autowired
    private JavaMailSender clienteSmtp;

    @Autowired
    private RelojAjustable reloj;

    @BeforeEach
    void preparar() {
        reloj.avanzar(Duration.ofHours(2));
        reset(clienteSmtp);
    }

    private String registrar() {
        String carnet = CarnetsDePrueba.nuevo("EC");
        RegistroRequest request = new RegistroRequest();
        request.setCarnet(carnet);
        registroCuenta.registrar(request);
        return carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv";
    }

    // Un solo hilo y en orden: cuando corre esta marca, ya terminaron los envíos programados antes. Con la cola de
    // un elemento ocupada la marca se descarta, así que se vuelve a programar hasta que entre
    private void esperarEnviosProgramados() throws InterruptedException {
        CountDownLatch fin = new CountDownLatch(1);
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        do {
            envio.programar(fin::countDown);
        } while (!fin.await(100, TimeUnit.MILLISECONDS) && System.nanoTime() < limite);
        assertThat(fin.getCount()).isZero();
    }

    // Prueba unitaria de la cola: con un trabajo en curso y otro esperando, el siguiente se descarta con un aviso y
    // sin lanzar nada a quien lo programa
    @Test
    void conLaColaLlenaElSiguienteTrabajoSeDescartaSinExcepcion(CapturedOutput salida) throws Exception {
        EnvioCorreoActivacion cola = new EnvioCorreoActivacion(1);
        List<String> ejecutados = new CopyOnWriteArrayList<>();
        CountDownLatch enCurso = new CountDownLatch(1);
        CountDownLatch seguir = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(2);
        try {
            cola.programar(() -> {
                enCurso.countDown();
                try {
                    seguir.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ejecutados.add("primero");
                terminados.countDown();
            });
            assertThat(enCurso.await(30, TimeUnit.SECONDS)).isTrue();
            cola.programar(() -> {
                ejecutados.add("segundo");
                terminados.countDown();
            });
            cola.programar(() -> ejecutados.add("tercero"));

            seguir.countDown();
            assertThat(terminados.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(ejecutados).containsExactly("primero", "segundo");
            assertThat(salida.getAll()).contains(AVISO_COLA);
        } finally {
            seguir.countDown();
            cola.detener();
        }
    }

    // Por el camino real del registro: A queda retenido en el SMTP (ya con su cupo), B espera en la cola y C se
    // descarta. Si C hubiera consumido cupo, D (el tercero del límite) no se enviaría; E ya no tiene cupo
    @Test
    void unEnvioDescartadoPorLaColaNoConsumeCupoHorario(CapturedOutput salida) throws Exception {
        BlockingQueue<String> enviados = new LinkedBlockingQueue<>();
        CountDownLatch enSmtp = new CountDownLatch(1);
        CountDownLatch seguir = new CountDownLatch(1);
        doAnswer(invocacion -> {
            enSmtp.countDown();
            seguir.await(30, TimeUnit.SECONDS);
            enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]);
            return null;
        }).when(clienteSmtp).send(any(SimpleMailMessage.class));

        String a = registrar();
        assertThat(enSmtp.await(30, TimeUnit.SECONDS)).isTrue();
        String b = registrar();
        String c = registrar();
        assertThat(salida.getAll()).contains(AVISO_COLA);

        // Hasta que no salgan A y B la cola sigue ocupada: solo entonces se puede programar la marca de espera
        seguir.countDown();
        assertThat(enviados.poll(30, TimeUnit.SECONDS)).isEqualTo(a);
        assertThat(enviados.poll(30, TimeUnit.SECONDS)).isEqualTo(b);
        esperarEnviosProgramados();
        String d = registrar();
        assertThat(enviados.poll(30, TimeUnit.SECONDS)).isEqualTo(d);
        esperarEnviosProgramados();
        String e = registrar();
        esperarEnviosProgramados();

        assertThat(enviados).isEmpty();
        // C (cola llena) y E (sin cupo horario) quedan descartados sin intento y con su token vigente
        for (String sinEnviar : new String[] {c, e}) {
            Estudiante pendiente = estudianteRepository.findByCorreoInstitucional(sinEnviar).orElseThrow();
            assertThat(pendiente.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
            assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(pendiente.getId())).singleElement()
                    .satisfies(token -> {
                        assertThat(token.getEnvioIntentadoEn()).isNull();
                        assertThat(token.getEnvioDescartadoEn()).isNotNull();
                        assertThat(token.getInvalidadoEn()).isNull();
                    });
        }
        assertThat(salida.getAll()).contains("Límite global de correos alcanzado");
    }

    // Un reenvío que encuentra la cola llena responde igual y sin error; su token queda emitido, vigente y descartado
    // (no cuenta en el límite por cuenta)
    @Test
    void unReenvioConLaColaLlenaRespondeIgualYConservaSuToken(CapturedOutput salida) throws Exception {
        List<String> enviados = new CopyOnWriteArrayList<>();
        doAnswer(invocacion -> enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));
        String cuenta = registrar();
        esperarEnviosProgramados();
        reloj.avanzar(Duration.ofMinutes(2));

        CountDownLatch enCurso = new CountDownLatch(1);
        CountDownLatch seguir = new CountDownLatch(1);
        CuentaResponse respuesta;
        try {
            envio.programar(() -> {
                enCurso.countDown();
                try {
                    seguir.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(enCurso.await(30, TimeUnit.SECONDS)).isTrue();
            envio.programar(() -> { });

            respuesta = registroCuenta.reenviar(new ReenvioActivacionRequest(cuenta));
        } finally {
            seguir.countDown();
        }
        esperarEnviosProgramados();

        assertThat(respuesta.correoInstitucional()).isEqualTo(cuenta);
        assertThat(salida.getAll()).contains(AVISO_COLA);
        assertThat(enviados).containsExactly(cuenta);
        Estudiante pendiente = estudianteRepository.findByCorreoInstitucional(cuenta).orElseThrow();
        assertThat(pendiente.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(pendiente.getId()))
                .hasSize(2).last().satisfies(token -> {
                    assertThat(token.getInvalidadoEn()).isNull();
                    assertThat(token.getEnvioDescartadoEn()).isNotNull();
                });
    }
}
