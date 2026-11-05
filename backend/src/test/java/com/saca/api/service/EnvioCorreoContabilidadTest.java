package com.saca.api.service;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.RegistroRequest;
import com.saca.api.dto.request.ReenvioActivacionRequest;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.TokenActivacion;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Estado del envío de cada correo de activación y su recuento para el límite de cinco cada 24
// horas. MySQL y el SMTP no comparten transacción: el intento se guarda antes de llamar al SMTP y, si el resultado
// no llega a guardarse, cuenta 24 horas como enviado. Los fallos de MySQL se simulan sobre TokenActivacionService
// y el hilo de envío se sustituye por uno controlado que puede retener los trabajos o simular la cola llena
@SpringBootTest(properties = {
        "SACA_CORREO_REMITENTE=saca@pruebas.ues.edu.sv",
        "SACA_FRONTEND_URL=https://saca.pruebas.local"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class EnvioCorreoContabilidadTest {

    private static final String AVISO_PERSISTENCIA = "No se pudo guardar el estado de un correo de activación";

    // En el acto, retenido (para ejecutarlo después, una o más veces, o nunca: como tras un reinicio) o descartado
    static class EnvioControlado extends EnvioCorreoActivacion {
        final List<Runnable> retenidos = new CopyOnWriteArrayList<>();
        volatile boolean retener;
        volatile boolean colaLlena;

        @Override
        public void programar(Runnable envio, Runnable siDescartado) {
            if (colaLlena) {
                ejecutar(siDescartado);
            } else if (retener) {
                retenidos.add(() -> ejecutar(envio));
            } else {
                ejecutar(envio);
            }
        }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean
        JavaMailSender clienteSmtpDePrueba() {
            return mock(JavaMailSender.class);
        }

        @Bean
        @Primary
        EnvioControlado envioControlado() {
            return new EnvioControlado();
        }

        @Bean
        @Primary
        RelojAjustable relojEnvio() {
            return new RelojAjustable(Instant.now(), ZoneId.of("America/El_Salvador"));
        }
    }

    @Autowired
    private RegistroCuentaService registroCuenta;

    @MockitoSpyBean
    private TokenActivacionService tokens;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    @Autowired
    private JavaMailSender clienteSmtp;

    @Autowired
    private EnvioControlado envio;

    @Autowired
    private RelojAjustable reloj;

    private final List<String> enviados = new CopyOnWriteArrayList<>();

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.now());
        envio.retener = false;
        envio.colaLlena = false;
        envio.retenidos.clear();
        enviados.clear();
        reset(clienteSmtp);
        doAnswer(invocacion -> enviados.add(((SimpleMailMessage) invocacion.getArgument(0)).getTo()[0]))
                .when(clienteSmtp).send(any(SimpleMailMessage.class));
    }

    @AfterEach
    void restaurar() {
        reset(tokens);
    }

    // ================= utilidades

    private String registrar() {
        String carnet = CarnetsDePrueba.nuevo("EN");
        RegistroRequest request = new RegistroRequest();
        request.setCarnet(carnet);
        registroCuenta.registrar(request);
        return carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv";
    }

    private void reenviar(String correo) {
        registroCuenta.reenviar(new ReenvioActivacionRequest(correo));
    }

    private Estudiante cuenta(String correo) {
        return estudianteRepository.findByCorreoInstitucional(correo).orElseThrow();
    }

    private List<TokenActivacion> tokensDe(String correo) {
        return tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(cuenta(correo).getId());
    }

    private TokenActivacion ultimo(String correo) {
        List<TokenActivacion> lista = tokensDe(correo);
        return lista.get(lista.size() - 1);
    }

    private long cuentan(String correo) {
        return tokens.correosQueCuentan(cuenta(correo).getId(), LocalDateTime.now(reloj));
    }

    private long llamadasSmtp() {
        return enviados.size();
    }

    private void fijarEn(LocalDateTime instante, Duration desplazamiento) {
        reloj.fijar(instante.atZone(ZoneId.of("America/El_Salvador")).toInstant().plus(desplazamiento));
    }

    // ================= resultado guardado

    @Test
    void unCorreoAceptadoQuedaEnviadoYUnoEnColaNoHastaQueSale() {
        envio.retener = true;
        String correo = registrar();
        TokenActivacion enCola = ultimo(correo);
        assertThat(enCola.getEnvioIntentadoEn()).isNull();
        assertThat(enCola.getEnviadoEn()).isNull();
        assertThat(cuentan(correo)).isEqualTo(1);

        envio.retenidos.get(0).run();

        TokenActivacion enviado = ultimo(correo);
        assertThat(enviado.getEnvioIntentadoEn()).isNotNull();
        assertThat(enviado.getEnviadoEn()).isNotNull();
        assertThat(enviado.getEnvioDescartadoEn()).isNull();
        assertThat(enviados).containsExactly(correo);
    }

    @Test
    void unFalloTransitorioAlGuardarElEnvioSeReintentaSinRepetirElSmtp(CapturedOutput salida) {
        doThrow(new DataAccessResourceFailureException("mysql no disponible"))
                .doThrow(new DataAccessResourceFailureException("mysql no disponible"))
                .doCallRealMethod().when(tokens).registrarEnviado(anyLong());

        String correo = registrar();

        assertThat(ultimo(correo).getEnviadoEn()).isNotNull();
        assertThat(llamadasSmtp()).isEqualTo(1);
        verify(tokens, times(3)).registrarEnviado(anyLong());
        assertThat(salida.getAll()).doesNotContain(AVISO_PERSISTENCIA);
    }

    // Si nunca se guarda el resultado, el intento cuenta 24 horas como enviado: con el inicial sin marcar y cuatro
    // reenvíos enviados ya son cinco, y el sexto no sale aunque solo consten cuatro enviados
    @Test
    void unFalloPermanenteAlGuardarElEnvioCuenta24HorasComoEnviado(CapturedOutput salida) {
        doThrow(new DataAccessResourceFailureException("mysql no disponible")).when(tokens).registrarEnviado(anyLong());
        String correo = registrar();
        TokenActivacion sinResultado = ultimo(correo);
        assertThat(sinResultado.getEnvioIntentadoEn()).isNotNull();
        assertThat(sinResultado.getEnviadoEn()).isNull();
        assertThat(sinResultado.getEnvioDescartadoEn()).isNull();
        assertThat(salida.getAll()).contains(AVISO_PERSISTENCIA).doesNotContain(correo);
        reset(tokens);

        for (int i = 0; i < 4; i++) {
            reloj.avanzar(Duration.ofMinutes(2));
            reenviar(correo);
        }
        assertThat(llamadasSmtp()).isEqualTo(5);
        assertThat(tokensDe(correo).stream().filter(token -> token.getEnviadoEn() != null)).hasSize(4);
        assertThat(cuentan(correo)).isEqualTo(5);

        reloj.avanzar(Duration.ofMinutes(2));
        reenviar(correo);
        assertThat(tokensDe(correo)).hasSize(5);

        // Justo a las 24 horas del intento deja de contar y cabe otro
        fijarEn(sinResultado.getEnvioIntentadoEn(), Duration.ofHours(24).minusMillis(1));
        assertThat(cuentan(correo)).isEqualTo(5);
        fijarEn(sinResultado.getEnvioIntentadoEn(), Duration.ofHours(24));
        assertThat(cuentan(correo)).isEqualTo(4);
        reenviar(correo);
        assertThat(tokensDe(correo)).hasSize(6);
    }

    // ================= reinicios

    // Reinicio después de guardar el intento y antes del resultado (o tras llamar al SMTP): cuenta 24 horas. Uno
    // perdido en la cola, sin intento, solo cuenta sus 15 minutos
    @Test
    void trasUnReinicioElIntentoSinResultadoCuenta24HorasYElQueSeguiaEnColaQuince() {
        envio.retener = true;
        String intentado = registrar();
        String enCola = registrar();
        // El hilo de envío llegó a guardar el intento del primero; después el proceso se detuvo
        assertThat(tokens.registrarIntento(ultimo(intentado).getId())).isTrue();
        envio.retenidos.clear();
        LocalDateTime intento = ultimo(intentado).getEnvioIntentadoEn();
        LocalDateTime creado = ultimo(enCola).getCreadoEn();

        fijarEn(creado, Duration.ofMinutes(15).minusMillis(1));
        assertThat(cuentan(enCola)).isEqualTo(1);
        fijarEn(creado, Duration.ofMinutes(15));
        assertThat(cuentan(enCola)).isZero();
        assertThat(cuentan(intentado)).isEqualTo(1);

        fijarEn(intento, Duration.ofHours(24).minusMillis(1));
        assertThat(cuentan(intentado)).isEqualTo(1);
        fijarEn(intento, Duration.ofHours(24));
        assertThat(cuentan(intentado)).isZero();
        assertThat(llamadasSmtp()).isZero();
    }

    // ================= marca previa

    @Test
    void siFallaLaMarcaPreviaNoSeLlamaAlSmtp(CapturedOutput salida) {
        doThrow(new DataAccessResourceFailureException("mysql no disponible")).when(tokens).registrarIntento(anyLong());

        String correo = registrar();

        verify(clienteSmtp, never()).send(any(SimpleMailMessage.class));
        TokenActivacion token = ultimo(correo);
        assertThat(token.getEnvioIntentadoEn()).isNull();
        assertThat(token.getEnviadoEn()).isNull();
        assertThat(token.getEnvioDescartadoEn()).isNull();
        assertThat(salida.getAll()).contains("No se pudo registrar el intento de un correo de activación");
        // Sigue en cola: cuenta 15 minutos
        assertThat(cuentan(correo)).isEqualTo(1);
        reloj.avanzar(Duration.ofMinutes(15));
        assertThat(cuentan(correo)).isZero();
    }

    // El mismo trabajo ejecutado dos veces solo llama una vez al SMTP, y la segunda no altera el estado
    @Test
    void unTrabajoDuplicadoSoloLlamaUnaVezAlSmtp() {
        envio.retener = true;
        String correo = registrar();
        Runnable trabajo = envio.retenidos.get(0);

        trabajo.run();
        trabajo.run();

        assertThat(llamadasSmtp()).isEqualTo(1);
        TokenActivacion token = ultimo(correo);
        assertThat(token.getEnviadoEn()).isNotNull();
        assertThat(token.getEnvioDescartadoEn()).isNull();
    }

    // ================= fallos y descartes

    // Un fallo del SMTP no se reintenta y descarta el correo (no cuenta); si tampoco se guarda el descarte, el
    // intento cuenta 24 horas
    @Test
    void unFalloDelSmtpDescartaElCorreoYSiNoSeGuardaCuentaComoIntento() {
        doAnswer(invocacion -> {
            throw new MailSendException("smtp no disponible");
        }).when(clienteSmtp).send(any(SimpleMailMessage.class));

        String descartado = registrar();
        verify(clienteSmtp, times(1)).send(any(SimpleMailMessage.class));
        TokenActivacion token = ultimo(descartado);
        assertThat(token.getEnvioIntentadoEn()).isNotNull();
        assertThat(token.getEnvioDescartadoEn()).isNotNull();
        assertThat(token.getEnviadoEn()).isNull();
        assertThat(cuentan(descartado)).isZero();

        doThrow(new DataAccessResourceFailureException("mysql no disponible")).when(tokens).registrarFallido(anyLong());
        String sinDescarte = registrar();
        TokenActivacion intento = ultimo(sinDescarte);
        assertThat(intento.getEnvioIntentadoEn()).isNotNull();
        assertThat(intento.getEnvioDescartadoEn()).isNull();
        assertThat(cuentan(sinDescarte)).isEqualTo(1);
        verify(clienteSmtp, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void conLaColaLlenaElCorreoQuedaDescartadoSinIntentoYNoCuenta() {
        envio.colaLlena = true;

        String correo = registrar();

        TokenActivacion token = ultimo(correo);
        assertThat(token.getEnvioIntentadoEn()).isNull();
        assertThat(token.getEnvioDescartadoEn()).isNotNull();
        assertThat(token.getInvalidadoEn()).isNull();
        assertThat(cuentan(correo)).isZero();
        assertThat(llamadasSmtp()).isZero();
    }

    // Caducidad de 10 minutos antes de llamar al SMTP: un milisegundo antes se envía; a los 10 minutos, no
    @Test
    void unTrabajoDeMasDeDiezMinutosNoSeIntenta() {
        envio.retener = true;
        String aTiempo = registrar();
        String caducado = registrar();
        Runnable trabajoATiempo = envio.retenidos.get(0);
        Runnable trabajoCaducado = envio.retenidos.get(1);
        LocalDateTime creado = ultimo(caducado).getCreadoEn();

        fijarEn(ultimo(aTiempo).getCreadoEn(), Duration.ofMinutes(10).minusMillis(1));
        trabajoATiempo.run();
        fijarEn(creado, Duration.ofMinutes(10));
        trabajoCaducado.run();

        assertThat(enviados).containsExactly(aTiempo);
        assertThat(ultimo(aTiempo).getEnviadoEn()).isNotNull();
        TokenActivacion sinIntento = ultimo(caducado);
        assertThat(sinIntento.getEnvioIntentadoEn()).isNull();
        assertThat(sinIntento.getEnvioDescartadoEn()).isNotNull();
        assertThat(cuentan(caducado)).isZero();
    }

    // ================= límite de cinco con correos en cola

    // Un correo en cola ocupa su plaza: con cuatro enviados y uno en cola, otro reenvío no emite; si el de la cola
    // se descarta, la plaza queda libre
    @Test
    void unCorreoEnColaCuentaParaElLimiteDeCinco() {
        String correo = registrar();
        for (int i = 0; i < 3; i++) {
            reloj.avanzar(Duration.ofMinutes(2));
            reenviar(correo);
        }
        envio.retener = true;
        reloj.avanzar(Duration.ofMinutes(2));
        reenviar(correo);
        assertThat(cuentan(correo)).isEqualTo(5);

        reloj.avanzar(Duration.ofMinutes(2));
        reenviar(correo);
        assertThat(tokensDe(correo)).hasSize(5);

        // El de la cola caduca sin intentarse: deja de contar y se puede volver a enviar
        reloj.avanzar(Duration.ofMinutes(9));
        envio.retenidos.get(0).run();
        assertThat(ultimo(correo).getEnvioDescartadoEn()).isNotNull();
        assertThat(cuentan(correo)).isEqualTo(4);
        envio.retener = false;
        reenviar(correo);
        assertThat(tokensDe(correo)).hasSize(6);
        assertThat(enviados).hasSize(5);
    }
}
