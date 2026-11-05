package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.TokenActivacion;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
import com.saca.api.service.EnvioCorreoActivacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Reenvío del enlace de activación por HTTP, con el carnet o el correo institucional. Misma
// respuesta en todos los casos, solo cuentas PENDIENTE, como mucho un correo por minuto y cinco cada 24 horas
// (ventanas móviles, contando el inicial). SMTP simulado y envío en el acto después del commit; los límites por
// cliente se suben aquí (se prueban en RegistroActivacionLimitesTest)
@SpringBootTest(properties = {
        "SACA_CORREO_REMITENTE=saca@pruebas.ues.edu.sv",
        "SACA_FRONTEND_URL=https://saca.pruebas.local",
        "SACA_LIMITE_REGISTRO_10_MIN=1000000",
        "SACA_LIMITE_REGISTRO_24_H=1000000",
        "SACA_LIMITE_ACTIVACION_MIN=1000000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ReenvioActivacionTest {

    private static final String MENSAJE_REENVIO =
            "Si hay una cuenta pendiente de activación con esos datos, recibirás un nuevo enlace en tu correo institucional.";
    private static final Pattern TOKEN_EN_ENLACE = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

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
        RelojAjustable relojReenvio() {
            return new RelojAjustable(Instant.now(), ZoneId.of("America/El_Salvador"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    @Autowired
    private JavaMailSender clienteSmtp;

    @Autowired
    private RelojAjustable reloj;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final List<SimpleMailMessage> enviados = new CopyOnWriteArrayList<>();

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.now());
        enviados.clear();
        reset(clienteSmtp);
        doAnswer(invocacion -> enviados.add(invocacion.getArgument(0))).when(clienteSmtp).send(any(SimpleMailMessage.class));
    }

    // ================= utilidades

    private MockHttpServletResponse publicar(String ruta, Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo))).andReturn().getResponse();
    }

    private MockHttpServletResponse reenviar(String identificador) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("identificador", identificador);
        return publicar("/api/auth/reenviar-activacion", cuerpo);
    }

    private String registrar() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RE");
        assertThat(publicar("/api/auth/registro", Map.of("carnet", carnet)).getStatus()).isEqualTo(200);
        return carnet;
    }

    private static String correoDe(String carnet) {
        return carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv";
    }

    private JsonNode json(MockHttpServletResponse respuesta) throws Exception {
        return objectMapper.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
    }

    // Exactamente el mismo cuerpo en todos los casos: solo cambia el correo, que se deriva del dato escrito
    private void assertRespuestaGenerica(MockHttpServletResponse respuesta, String carnet) throws Exception {
        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(cuerpoEsperado(carnet));
    }

    private String cuerpoEsperado(String carnet) {
        return "{\"mensaje\":\"" + MENSAJE_REENVIO + "\",\"correoInstitucional\":\"" + correoDe(carnet) + "\"}";
    }

    private Estudiante cuenta(String carnet) {
        return estudianteRepository.findByCorreoInstitucional(correoDe(carnet)).orElseThrow();
    }

    private List<TokenActivacion> tokensDe(String carnet) {
        return tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(cuenta(carnet).getId());
    }

    private long enviadosA(String carnet) {
        return enviados.stream().filter(mensaje -> correoDe(carnet).equals(mensaje.getTo()[0])).count();
    }

    private String ultimoTokenEnviadoA(String carnet) {
        SimpleMailMessage ultimo = null;
        for (SimpleMailMessage mensaje : enviados) {
            if (correoDe(carnet).equals(mensaje.getTo()[0])) {
                ultimo = mensaje;
            }
        }
        assertThat(ultimo).isNotNull();
        Matcher token = TOKEN_EN_ENLACE.matcher(ultimo.getText());
        assertThat(token.find()).isTrue();
        return token.group(1);
    }

    private String estadoDe(String token) throws Exception {
        return json(publicar("/api/auth/activacion/consultar", Map.of("token", token))).get("estado").asText();
    }

    private Estudiante existente(EstadoCorreo estado) {
        String carnet = CarnetsDePrueba.nuevo("RE");
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Cuenta Existente");
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(correoDe(carnet));
        estudiante.setContrasenia(passwordEncoder.encode("Segura123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(1);
        estudiante.setEstadoCorreo(estado);
        return estudianteRepository.save(estudiante);
    }

    // ================= reenvío

    @Test
    void unaCuentaPendienteRecibeOtroEnlacePorCarnetYPorCorreoYElAnteriorDejaDeValer(CapturedOutput salida) throws Exception {
        String carnet = registrar();
        String inicial = ultimoTokenEnviadoA(carnet);

        reloj.avanzar(Duration.ofMinutes(2));
        assertRespuestaGenerica(reenviar(carnet), carnet);
        String porCarnet = ultimoTokenEnviadoA(carnet);

        reloj.avanzar(Duration.ofMinutes(2));
        assertRespuestaGenerica(reenviar("  " + correoDe(carnet).toUpperCase(Locale.ROOT) + " "), carnet);
        String porCorreo = ultimoTokenEnviadoA(carnet);

        assertThat(enviadosA(carnet)).isEqualTo(3);
        assertThat(List.of(inicial, porCarnet, porCorreo)).doesNotHaveDuplicates();
        assertThat(estadoDe(inicial)).isEqualTo("INVALIDO");
        assertThat(estadoDe(porCarnet)).isEqualTo("INVALIDO");
        assertThat(estadoDe(porCorreo)).isEqualTo("VALIDO");
        List<TokenActivacion> tokens = tokensDe(carnet);
        assertThat(tokens).hasSize(3);
        assertThat(tokens.stream().filter(token -> token.getInvalidadoEn() == null)).hasSize(1);
        assertThat(cuenta(carnet).getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        // Nada sensible en los registros del servidor
        assertThat(salida.getAll()).doesNotContain(porCorreo).doesNotContain(correoDe(carnet))
                .doesNotContain(tokens.get(2).getHashToken());
    }

    @Test
    void elCarnetSeNormaliza() throws Exception {
        String carnet = registrar();
        reloj.avanzar(Duration.ofMinutes(2));

        assertRespuestaGenerica(reenviar("  " + carnet.toLowerCase(Locale.ROOT) + "\t"), carnet);

        assertThat(enviadosA(carnet)).isEqualTo(2);
    }

    @Test
    void unIdentificadorQueNoEsCarnetNiCorreoInstitucionalSeRechaza() throws Exception {
        String carnet = registrar();
        reloj.avanzar(Duration.ofMinutes(2));
        String local = carnet.toLowerCase(Locale.ROOT);
        String[] invalidos = {"", "   ", null, "AB1234", "ABC12345", local + "@gmail.com", local + "@ues.edu.sv.otro.com",
                "x." + local + "@ues.edu.sv", local + "@", "@ues.edu.sv", local + "@estudiantes.ues.edu.sv",
                local + " @ues.edu.sv", "nombre apellido"};
        for (String invalido : invalidos) {
            MockHttpServletResponse respuesta = reenviar(invalido);
            assertThat(respuesta.getStatus()).as(String.valueOf(invalido)).isEqualTo(400);
            assertThat(json(respuesta).get("message").asText()).isEqualTo(
                    "Escribe tu carnet (por ejemplo AP23001) o tu correo institucional (por ejemplo ap23001@ues.edu.sv).");
        }
        assertThat(enviadosA(carnet)).isEqualTo(1);
    }

    // Sin cuenta, confirmada o anterior (NULL): la misma respuesta, sin correo, sin tokens y sin crear ni tocar nada
    @Test
    void sinCuentaPendienteLaRespuestaEsLaMismaYNoCambiaNada() throws Exception {
        String inexistente = CarnetsDePrueba.nuevo("RE");
        assertRespuestaGenerica(reenviar(inexistente), inexistente);
        assertRespuestaGenerica(reenviar(correoDe(inexistente)), inexistente);
        assertThat(estudianteRepository.findByCorreoInstitucional(correoDe(inexistente))).isEmpty();

        for (EstadoCorreo estado : new EstadoCorreo[] {EstadoCorreo.CONFIRMADO, null}) {
            Estudiante antes = existente(estado);
            assertRespuestaGenerica(reenviar(antes.getCarnet()), antes.getCarnet());
            assertRespuestaGenerica(reenviar(antes.getCorreoInstitucional()), antes.getCarnet());

            Estudiante despues = cuenta(antes.getCarnet());
            assertThat(despues.getEstadoCorreo()).isEqualTo(estado);
            assertThat(despues.getContrasenia()).isEqualTo(antes.getContrasenia());
            assertThat(despues.getNombre()).isEqualTo(antes.getNombre());
            assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(despues.getId())).isEmpty();
        }
        assertThat(enviados).isEmpty();
    }

    @Test
    void elNuevoEnlaceVale24Horas() throws Exception {
        String carnet = registrar();
        reloj.avanzar(Duration.ofMinutes(5));
        reenviar(carnet);
        String token = ultimoTokenEnviadoA(carnet);
        TokenActivacion emitido = tokensDe(carnet).get(1);
        assertThat(Duration.between(emitido.getCreadoEn(), emitido.getExpiraEn())).isEqualTo(Duration.ofHours(24));

        reloj.avanzar(Duration.ofHours(24).minusMillis(1));
        assertThat(estadoDe(token)).isEqualTo("VALIDO");
        reloj.avanzar(Duration.ofMillis(1));
        assertThat(estadoDe(token)).isEqualTo("EXPIRADO");
    }

    // Un minuto exacto desde la última emisión (el registro incluido); los intentos rechazados no cuentan
    @Test
    void entreDosCorreosDebePasarUnMinuto() throws Exception {
        String carnet = registrar();

        reloj.avanzar(Duration.ofSeconds(60).minusMillis(1));
        for (int i = 0; i < 5; i++) {
            assertRespuestaGenerica(reenviar(carnet), carnet);
        }
        assertThat(enviadosA(carnet)).isEqualTo(1);
        assertThat(tokensDe(carnet)).hasSize(1);

        reloj.avanzar(Duration.ofMillis(1));
        assertRespuestaGenerica(reenviar(carnet), carnet);
        assertThat(enviadosA(carnet)).isEqualTo(2);
        assertRespuestaGenerica(reenviar(carnet), carnet);
        assertThat(enviadosA(carnet)).isEqualTo(2);
    }

    // Cinco correos por ventana móvil de 24 horas contando el inicial; el sexto no sale ni cuenta, y el cupo vuelve
    // justo cuando el primero sale de la ventana
    @Test
    void comoMuchoCincoCorreosCada24HorasContandoElInicial() throws Exception {
        Instant inicio = reloj.instant();
        String carnet = registrar();
        for (int i = 1; i <= 4; i++) {
            reloj.fijar(inicio.plus(Duration.ofMinutes(10L * i)));
            assertRespuestaGenerica(reenviar(carnet), carnet);
        }
        assertThat(enviadosA(carnet)).isEqualTo(5);

        for (int minutos : new int[] {60, 600, 23 * 60}) {
            reloj.fijar(inicio.plus(Duration.ofMinutes(minutos)));
            assertRespuestaGenerica(reenviar(carnet), carnet);
        }
        reloj.fijar(inicio.plus(Duration.ofHours(24)).minusMillis(1));
        assertRespuestaGenerica(reenviar(carnet), carnet);
        assertThat(enviadosA(carnet)).isEqualTo(5);
        assertThat(tokensDe(carnet)).hasSize(5);

        // A las 24 horas sale el inicial: cabe uno; el siguiente espera a que salga el de los 10 minutos
        reloj.fijar(inicio.plus(Duration.ofHours(24)));
        assertRespuestaGenerica(reenviar(carnet), carnet);
        assertThat(enviadosA(carnet)).isEqualTo(6);
        reloj.fijar(inicio.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(5)));
        reenviar(carnet);
        assertThat(enviadosA(carnet)).isEqualTo(6);
        reloj.fijar(inicio.plus(Duration.ofHours(24)).plus(Duration.ofMinutes(10)));
        reenviar(carnet);
        assertThat(enviadosA(carnet)).isEqualTo(7);
        assertThat(estadoDe(ultimoTokenEnviadoA(carnet))).isEqualTo("VALIDO");
    }

    // El reenvío no toca la fila de la cuenta: ni contadores de bloqueo ni nombre, contraseña, estado o fechas
    @Test
    void elReenvioNoModificaLaCuenta() throws Exception {
        String carnet = registrar();
        Estudiante pendiente = cuenta(carnet);
        Timestamp bloqueo = Timestamp.valueOf(LocalDateTime.now().plusMinutes(3).withNano(0));
        pendiente.setIntentosFallidos(3);
        pendiente.setBloqueadoHasta(bloqueo);
        estudianteRepository.save(pendiente);
        Estudiante antes = cuenta(carnet);

        reloj.avanzar(Duration.ofMinutes(2));
        assertRespuestaGenerica(reenviar(carnet), carnet);

        Estudiante despues = cuenta(carnet);
        assertThat(enviadosA(carnet)).isEqualTo(2);
        assertThat(despues.getIntentosFallidos()).isEqualTo(3);
        assertThat(despues.getBloqueadoHasta()).isEqualTo(bloqueo);
        assertThat(despues.getContrasenia()).isEqualTo(antes.getContrasenia());
        assertThat(despues.getNombre()).isNull();
        assertThat(despues.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(despues.getCorreoConfirmadoEn()).isNull();
        assertThat(despues.getCarnet()).isEqualTo(carnet);
    }

    // Tras la activación ya no hay reenvío: el enlace usado sigue USADO y no se emite otro
    @Test
    void unaCuentaYaActivadaNoRecibeMasEnlaces() throws Exception {
        String carnet = registrar();
        String token = ultimoTokenEnviadoA(carnet);
        assertThat(publicar("/api/auth/activacion",
                Map.of("token", token, "nombre", "Nombre Prueba", "contrasenia", "Segura123")).getStatus()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(2));

        assertRespuestaGenerica(reenviar(carnet), carnet);

        assertThat(enviadosA(carnet)).isEqualTo(1);
        assertThat(tokensDe(carnet)).hasSize(1);
        assertThat(estadoDe(token)).isEqualTo("USADO");
    }
}
