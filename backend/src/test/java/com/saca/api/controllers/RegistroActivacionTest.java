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
import com.saca.api.service.TokenActivacionService;
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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Registro con el carnet y activación desde el enlace del correo, por HTTP y con un servidor
// SMTP simulado que guarda los mensajes. El envío se ejecuta en el acto en lugar de en segundo plano, pero igual
// que en producción después de confirmar la transacción. Los límites por cliente se suben para estas pruebas
// (se prueban en RegistroActivacionLimitesTest)
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
class RegistroActivacionTest {

    static final String CLAVE = "Segura123";
    private static final Pattern TOKEN_EN_ENLACE = Pattern.compile("#token=([A-Za-z0-9_-]{43})");
    private static final String MENSAJE_REGISTRO =
            "Si el carnet no tiene una cuenta activa, recibirás en tu correo institucional un enlace para activarla.";

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
        RelojAjustable relojRegistro() {
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

    @MockitoSpyBean
    private TokenActivacionService tokenActivacionService;

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

    private MvcResult publicar(String ruta, Map<String, ?> cuerpo) throws Exception {
        return mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo))).andReturn();
    }

    private MvcResult registrar(String carnet) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("carnet", carnet);
        return publicar("/api/auth/registro", cuerpo);
    }

    private MvcResult registrar(String carnet, String correo) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("carnet", carnet);
        cuerpo.put("correoInstitucional", correo);
        return publicar("/api/auth/registro", cuerpo);
    }

    private MvcResult consultar(String token) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("token", token);
        return publicar("/api/auth/activacion/consultar", cuerpo);
    }

    private MvcResult activar(String token, String nombre, String contrasenia) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("token", token);
        cuerpo.put("nombre", nombre);
        cuerpo.put("contrasenia", contrasenia);
        return publicar("/api/auth/activacion", cuerpo);
    }

    private MvcResult login(String correo, String contrasenia) throws Exception {
        return publicar("/api/auth/login", Map.of("correoInstitucional", correo, "contrasenia", contrasenia));
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String correoDe(String carnet) {
        return carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv";
    }

    private void assertRespuestaGenerica(MvcResult resultado, String carnet) throws Exception {
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("mensaje").asText()).isEqualTo(MENSAJE_REGISTRO);
        assertThat(cuerpo.get("correoInstitucional").asText()).isEqualTo(correoDe(carnet));
        assertThat(cuerpo.size()).isEqualTo(2);
    }

    private Estudiante cuenta(String carnet) {
        return estudianteRepository.findByCorreoInstitucional(correoDe(carnet)).orElseThrow();
    }

    private List<TokenActivacion> tokensDe(Estudiante estudiante) {
        return tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(estudiante.getId());
    }

    // Token del último correo enviado a ese carnet
    private String tokenEnviadoA(String carnet) {
        SimpleMailMessage ultimo = null;
        for (SimpleMailMessage mensaje : enviados) {
            if (correoDe(carnet).equals(mensaje.getTo()[0])) {
                ultimo = mensaje;
            }
        }
        assertThat(ultimo).as("correo enviado a %s", carnet).isNotNull();
        Matcher token = TOKEN_EN_ENLACE.matcher(ultimo.getText());
        assertThat(token.find()).isTrue();
        return token.group(1);
    }

    private long enviadosA(String carnet) {
        return enviados.stream().filter(mensaje -> correoDe(carnet).equals(mensaje.getTo()[0])).count();
    }

    private static String sha256(String valor) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII)));
    }

    private Estudiante existente(String carnet, EstadoCorreo estado) {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Cuenta Existente");
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(correoDe(carnet));
        estudiante.setContrasenia(passwordEncoder.encode(CLAVE));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(1);
        estudiante.setEstadoCorreo(estado);
        return estudianteRepository.save(estudiante);
    }

    // ================= registro

    @Test
    void unCarnetValidoCreaUnaCuentaPendienteYEnviaElEnlace(CapturedOutput salida) throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");

        MvcResult resultado = registrar(carnet);

        assertRespuestaGenerica(resultado, carnet);
        Estudiante creada = cuenta(carnet);
        assertThat(creada.getCarnet()).isEqualTo(carnet);
        assertThat(creada.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(creada.getNombre()).isNull();
        assertThat(creada.getCuentaActiva()).isTrue();
        assertThat(creada.getIntentosFallidos()).isZero();
        assertThat(creada.getBloqueadoHasta()).isNull();
        assertThat(creada.getCorreoConfirmadoEn()).isNull();
        assertThat(creada.getContrasenia()).startsWith("$2");

        // Un solo token, de 24 horas, guardado como SHA-256 del valor que viaja en el enlace
        List<TokenActivacion> tokens = tokensDe(creada);
        assertThat(tokens).hasSize(1);
        String token = tokenEnviadoA(carnet);
        assertThat(tokens.get(0).getHashToken()).isEqualTo(sha256(token)).isNotEqualTo(token);
        assertThat(Duration.between(tokens.get(0).getCreadoEn(), tokens.get(0).getExpiraEn())).isEqualTo(Duration.ofHours(24));
        assertThat(enviados).hasSize(1);
        assertThat(enviados.get(0).getText()).contains("https://saca.pruebas.local/#token=" + token);

        // Ni la respuesta ni los registros del servidor contienen el token o su hash
        String cuerpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).doesNotContain(token).doesNotContain(tokens.get(0).getHashToken());
        assertThat(salida.getAll()).doesNotContain(token).doesNotContain(tokens.get(0).getHashToken());
    }

    @Test
    void elCarnetSeNormalizaYElCorreoSeDerivaDeEl() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        String otro = CarnetsDePrueba.nuevo("RA");

        assertRespuestaGenerica(registrar("  " + carnet.toLowerCase(Locale.ROOT) + " "), carnet);
        // El correo explícito que coincide se acepta, sin importar mayúsculas ni espacios
        assertRespuestaGenerica(registrar("r" + otro.substring(1), " " + correoDe(otro).toUpperCase(Locale.ROOT) + " "), otro);

        assertThat(cuenta(carnet).getCarnet()).isEqualTo(carnet);
        assertThat(cuenta(otro).getCarnet()).isEqualTo(otro);
        assertThat(enviados).extracting(mensaje -> mensaje.getTo()[0]).containsExactly(correoDe(carnet), correoDe(otro));
    }

    @Test
    void unCarnetInvalidoOVacioSeRechazaSinCrearNada() throws Exception {
        String[] invalidos = {"12", "A123456", "ABC1234", "RC1234", "RC123456", "R C1234", "RC 12345", "RC-1234",
                "ÑA12345", "RC12A45", "1234567", "RCABCDE"};
        for (String invalido : invalidos) {
            MvcResult resultado = registrar(invalido);
            assertThat(resultado.getResponse().getStatus()).as(invalido).isEqualTo(400);
            assertThat(json(resultado).get("message").asText()).as(invalido)
                    .isEqualTo("El carnet debe tener dos letras y cinco números, por ejemplo AP23001");
        }
        for (String vacio : new String[] {"", "   ", null}) {
            MvcResult resultado = registrar(vacio);
            assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
            assertThat(json(resultado).get("message").asText()).isEqualTo("Carnet obligatorio");
        }
        assertThat(enviados).isEmpty();
    }

    @Test
    void unCorreoQueNoEsElDelCarnetSeRechazaSinCrearNada() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");

        for (String correo : new String[] {"otro@ues.edu.sv", correoDe(carnet).replace("@ues.edu.sv", "@gmail.com"),
                "x" + correoDe(carnet)}) {
            MvcResult resultado = registrar(carnet, correo);
            assertThat(resultado.getResponse().getStatus()).as(correo).isEqualTo(400);
            assertThat(json(resultado).get("message").asText())
                    .isEqualTo("El correo institucional debe ser el de tu carnet: " + correoDe(carnet));
        }

        assertThat(estudianteRepository.findByCorreoInstitucional(correoDe(carnet))).isEmpty();
        assertThat(enviados).isEmpty();
    }

    @Test
    void unaCuentaConfirmadaOAnteriorNoSeTocaNiRecibeCorreo() throws Exception {
        for (EstadoCorreo estado : new EstadoCorreo[] {EstadoCorreo.CONFIRMADO, null}) {
            String carnet = CarnetsDePrueba.nuevo("RA");
            Estudiante antes = existente(carnet, estado);

            assertRespuestaGenerica(registrar(carnet), carnet);
            assertRespuestaGenerica(registrar(carnet.toLowerCase(Locale.ROOT)), carnet);

            Estudiante despues = cuenta(carnet);
            assertThat(despues.getEstadoCorreo()).isEqualTo(estado);
            assertThat(despues.getNombre()).isEqualTo(antes.getNombre());
            assertThat(despues.getContrasenia()).isEqualTo(antes.getContrasenia());
            assertThat(despues.getIntentosFallidos()).isEqualTo(1);
            assertThat(tokensDe(despues)).isEmpty();
            assertThat(enviadosA(carnet)).isZero();
        }
    }

    // Un carnet anterior guardado en minúsculas, con otro correo, también cuenta como existente
    @Test
    void unCarnetAnteriorEnMinusculasConOtroCorreoNoCreaOtraCuenta() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        Estudiante anterior = existente(carnet, null);
        anterior.setCarnet(carnet.toLowerCase(Locale.ROOT));
        anterior.setCorreoInstitucional("anterior." + carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv");
        estudianteRepository.save(anterior);

        assertRespuestaGenerica(registrar(carnet), carnet);

        assertThat(estudianteRepository.findByCorreoInstitucional(correoDe(carnet))).isEmpty();
        assertThat(enviados).isEmpty();
    }

    @Test
    void unaCuentaPendienteConTokenVigenteNoRecibeOtro() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        reloj.avanzar(Duration.ofHours(23));

        assertRespuestaGenerica(registrar(carnet), carnet);
        assertRespuestaGenerica(registrar(carnet.toLowerCase(Locale.ROOT)), carnet);

        assertThat(tokensDe(cuenta(carnet))).hasSize(1);
        assertThat(enviadosA(carnet)).isEqualTo(1);
    }

    @Test
    void unaCuentaPendienteConElTokenVencidoRecibeUnoNuevo() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String primero = tokenEnviadoA(carnet);

        reloj.avanzar(Duration.ofHours(24).plusSeconds(1));
        assertRespuestaGenerica(registrar(carnet), carnet);

        String segundo = tokenEnviadoA(carnet);
        assertThat(segundo).isNotEqualTo(primero);
        assertThat(tokensDe(cuenta(carnet))).hasSize(2);
        assertThat(enviadosA(carnet)).isEqualTo(2);
        assertThat(json(consultar(primero)).get("estado").asText()).isEqualTo("EXPIRADO");
        assertThat(json(consultar(segundo)).get("estado").asText()).isEqualTo("VALIDO");
        assertThat(cuenta(carnet).getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
    }

    // Registros simultáneos del mismo carnet nuevo, y después del mismo carnet pendiente con el token vencido:
    // una cuenta, un token por vez y un correo por token
    @Test
    void losRegistrosSimultaneosDelMismoCarnetCreanUnaCuentaYUnTokenPorVez() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tareas.add(() -> registrar(carnet).getResponse().getStatus());
        }

        assertThat(simultaneos(tareas)).containsOnly(200);
        assertThat(estudianteRepository.findAll().stream().filter(e -> carnet.equals(e.getCarnet()))).hasSize(1);
        assertThat(tokensDe(cuenta(carnet))).hasSize(1);
        assertThat(enviadosA(carnet)).isEqualTo(1);

        reloj.avanzar(Duration.ofHours(25));
        assertThat(simultaneos(tareas)).containsOnly(200);
        assertThat(tokensDe(cuenta(carnet))).hasSize(2);
        assertThat(enviadosA(carnet)).isEqualTo(2);
    }

    private static <T> List<T> simultaneos(List<Callable<T>> tareas) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        try {
            List<Future<T>> pendientes = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                pendientes.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> pendiente : pendientes) {
                resultados.add(pendiente.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    // ================= correo después del commit

    // El correo se envía con la cuenta ya confirmada en la base; si falla, la respuesta es la misma y la cuenta
    // queda pendiente con su token vigente para el reenvío
    @Test
    void unFalloDelEnvioDespuesDelCommitDejaLaCuentaPendienteConSuToken(CapturedOutput salida) throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        List<Boolean> confirmadaAlEnviar = new CopyOnWriteArrayList<>();
        doAnswer(invocacion -> {
            confirmadaAlEnviar.add(estudianteRepository.findByCorreoInstitucional(correoDe(carnet)).isPresent());
            throw new MailSendException("smtp caido para " + correoDe(carnet));
        }).when(clienteSmtp).send(any(SimpleMailMessage.class));

        assertRespuestaGenerica(registrar(carnet), carnet);

        assertThat(confirmadaAlEnviar).containsExactly(true);
        Estudiante pendiente = cuenta(carnet);
        assertThat(pendiente.getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        List<TokenActivacion> tokens = tokensDe(pendiente);
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getUsadoEn()).isNull();
        assertThat(tokens.get(0).getInvalidadoEn()).isNull();
        assertThat(salida.getAll()).contains("No se pudo enviar un correo de activación (MailSendException)")
                .doesNotContain(correoDe(carnet)).doesNotContain(tokens.get(0).getHashToken());
    }

    // Un error antes del commit no deja nada: ni cuenta ni token ni correo
    @Test
    void unFalloAntesDelCommitNoDejaLaCuentaCreada() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        doThrow(new IllegalStateException("fallo de prueba")).when(tokenActivacionService).generar(any());
        try {
            MvcResult resultado = registrar(carnet);
            assertThat(resultado.getResponse().getStatus()).isEqualTo(500);
        } finally {
            reset(tokenActivacionService);
        }

        assertThat(estudianteRepository.findByCorreoInstitucional(correoDe(carnet))).isEmpty();
        assertThat(enviados).isEmpty();
        // Y el carnet puede registrarse después con normalidad
        assertRespuestaGenerica(registrar(carnet), carnet);
        assertThat(enviadosA(carnet)).isEqualTo(1);
    }

    // ================= consulta del enlace

    @Test
    void laConsultaDeUnEnlaceValidoMuestraLaCuentaSinConsumirlo() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);

        for (int i = 0; i < 3; i++) {
            JsonNode consulta = json(consultar(token));
            assertThat(consulta.get("estado").asText()).isEqualTo("VALIDO");
            assertThat(consulta.get("carnet").asText()).isEqualTo(carnet);
            assertThat(consulta.get("correoInstitucional").asText()).isEqualTo(correoDe(carnet));
        }
        assertThat(tokensDe(cuenta(carnet)).get(0).getUsadoEn()).isNull();
        assertThat(activar(token, "Nombre Prueba", CLAVE).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void laConsultaDeEnlacesInvalidosVencidosOUsadosNoDevuelveDatos() throws Exception {
        String vencido = CarnetsDePrueba.nuevo("RA");
        String usado = CarnetsDePrueba.nuevo("RA");
        registrar(vencido);
        registrar(usado);
        String tokenUsado = tokenEnviadoA(usado);
        activar(tokenUsado, "Nombre Prueba", CLAVE);
        reloj.avanzar(Duration.ofHours(24));

        Map<String, String> casos = new HashMap<>();
        casos.put(tokenEnviadoA(vencido), "EXPIRADO");
        casos.put(tokenUsado, "USADO");
        casos.put("A".repeat(43), "INVALIDO");
        casos.put("corto", "INVALIDO");
        casos.put("", "INVALIDO");
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            MvcResult resultado = consultar(caso.getKey());
            assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
            JsonNode consulta = json(resultado);
            assertThat(consulta.get("estado").asText()).isEqualTo(caso.getValue());
            assertThat(consulta.get("carnet").isNull()).isTrue();
            assertThat(consulta.get("correoInstitucional").isNull()).isTrue();
        }
        assertThat(json(publicar("/api/auth/activacion/consultar", Map.of())).get("estado").asText()).isEqualTo("INVALIDO");
    }

    // ================= activación

    @Test
    void laActivacionGuardaNombreYContraseniaConfirmaElCorreoYNoIniciaSesion() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);

        MvcResult resultado = activar(token, "  Ana María Pérez ", "Segura123");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("mensaje").asText()).isEqualTo("Cuenta activada. Ya puedes iniciar sesión.");
        assertThat(cuerpo.get("correoInstitucional").asText()).isEqualTo(correoDe(carnet));
        // Sin JWT ni datos internos
        assertThat(cuerpo.size()).isEqualTo(2);
        assertThat(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(token).doesNotContain("eyJ");

        Estudiante activada = cuenta(carnet);
        assertThat(activada.getEstadoCorreo()).isEqualTo(EstadoCorreo.CONFIRMADO);
        assertThat(activada.getNombre()).isEqualTo("Ana María Pérez");
        assertThat(passwordEncoder.matches("Segura123", activada.getContrasenia())).isTrue();
        assertThat(activada.getCorreoConfirmadoEn()).isNotNull();
        assertThat(tokensDe(activada).get(0).getUsadoEn()).isNotNull();

        // El inicio de sesión es aparte, con el correo devuelto
        JsonNode sesion = json(login(correoDe(carnet), "Segura123"));
        assertThat(sesion.get("autenticado").asBoolean()).isTrue();
        assertThat(sesion.get("token").asText()).isNotBlank();
    }

    @Test
    void unEnlaceYaUsadoNoActivaOtraVez() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);
        activar(token, "Primer Nombre", "Segura123");

        MvcResult repetida = activar(token, "Otro Nombre", "Distinta456");

        assertThat(repetida.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(repetida).get("message").asText())
                .isEqualTo("Este enlace ya se utilizó. Inicia sesión con tu correo institucional y tu contraseña.");
        Estudiante activada = cuenta(carnet);
        assertThat(activada.getNombre()).isEqualTo("Primer Nombre");
        assertThat(passwordEncoder.matches("Segura123", activada.getContrasenia())).isTrue();
    }

    @Test
    void lasActivacionesSimultaneasDelMismoEnlaceSoloActivanUnaVez() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String nombre = "Nombre " + "ABCDEFGH".charAt(i);
            tareas.add(() -> activar(token, nombre, "Segura123").getResponse().getStatus());
        }

        List<Integer> estados = simultaneos(tareas);

        assertThat(estados.stream().filter(estado -> estado == 200)).hasSize(1);
        assertThat(estados.stream().filter(estado -> estado == 400)).hasSize(7);
        assertThat(cuenta(carnet).getEstadoCorreo()).isEqualTo(EstadoCorreo.CONFIRMADO);
    }

    @Test
    void unEnlaceVencidoOInvalidoNoActiva() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);
        reloj.avanzar(Duration.ofHours(24));

        MvcResult vencido = activar(token, "Nombre Prueba", "Segura123");
        MvcResult invalido = activar("B".repeat(43), "Nombre Prueba", "Segura123");

        assertThat(vencido.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(vencido).get("message").asText())
                .isEqualTo("El enlace de activación venció. Solicita uno nuevo desde la página de inicio de sesión.");
        assertThat(invalido.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(invalido).get("message").asText()).isEqualTo("El enlace de activación no es válido.");
        assertThat(cuenta(carnet).getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
    }

    // Nombre y contraseña se validan antes de consumir el enlace: tras un error, el mismo enlace sigue sirviendo
    @Test
    void unaContraseniaOUnNombreInvalidosNoConsumenElEnlace() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);

        Map<String, String[]> casos = Map.of(
                "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número",
                new String[] {"Nombre Prueba", "corta1A"},
                "El nombre solo puede contener letras y espacios", new String[] {"Nombre 123", "Segura123"},
                "Nombre obligatorio", new String[] {"   ", "Segura123"},
                "El nombre no debe superar 120 caracteres", new String[] {"A".repeat(121), "Segura123"});
        for (Map.Entry<String, String[]> caso : casos.entrySet()) {
            MvcResult resultado = activar(token, caso.getValue()[0], caso.getValue()[1]);
            assertThat(resultado.getResponse().getStatus()).as(caso.getKey()).isEqualTo(400);
            assertThat(json(resultado).get("message").asText()).isEqualTo(caso.getKey());
        }
        for (String contrasenia : new String[] {"sinmayuscula1", "SINNUMEROS", "Corta1", "", null}) {
            assertThat(activar(token, "Nombre Prueba", contrasenia).getResponse().getStatus()).isEqualTo(400);
        }

        assertThat(json(consultar(token)).get("estado").asText()).isEqualTo("VALIDO");
        assertThat(cuenta(carnet).getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(activar(token, "Nombre Prueba", "Segura123").getResponse().getStatus()).isEqualTo(200);
    }

    // ================= login y bloqueo

    @Test
    void unaCuentaPendienteNoPuedeIniciarSesion() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);

        for (String contrasenia : new String[] {"Segura123", "", tokenEnviadoA(carnet)}) {
            JsonNode sesion = json(login(correoDe(carnet), contrasenia));
            assertThat(sesion.get("autenticado").asBoolean()).isFalse();
            assertThat(sesion.get("mensaje").asText()).isEqualTo("Credenciales incorrectas");
            assertThat(sesion.has("token") && !sesion.get("token").isNull()).isFalse();
        }
    }

    // La activación no reinicia ni cambia el contador de fallos ni el bloqueo
    @Test
    void laActivacionNoModificaLosContadoresDeBloqueo() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("RA");
        registrar(carnet);
        String token = tokenEnviadoA(carnet);
        Estudiante pendiente = cuenta(carnet);
        Timestamp bloqueo = Timestamp.valueOf(LocalDateTime.now().plusMinutes(3).withNano(0));
        pendiente.setIntentosFallidos(3);
        pendiente.setBloqueadoHasta(bloqueo);
        estudianteRepository.save(pendiente);

        assertThat(activar(token, "Nombre Prueba", "Segura123").getResponse().getStatus()).isEqualTo(200);

        Estudiante activada = cuenta(carnet);
        assertThat(activada.getEstadoCorreo()).isEqualTo(EstadoCorreo.CONFIRMADO);
        assertThat(activada.getIntentosFallidos()).isEqualTo(3);
        assertThat(activada.getBloqueadoHasta()).isEqualTo(bloqueo);
    }
}
