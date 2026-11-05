package com.saca.api.service;

import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.TokenActivacion;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
import com.saca.api.service.TokenActivacionService.EstadoToken;
import com.saca.api.service.TokenActivacionService.ResultadoToken;
import com.saca.api.service.TokenActivacionService.TokenGenerado;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Tokens de activación con vigencia de 24 horas, un solo uso, invalidación al emitir otro
// y almacenamiento exclusivo del SHA-256
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class TokenActivacionServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/El_Salvador");
    private static final Instant INICIO = Instant.parse("2026-11-03T15:00:00Z");

    @TestConfiguration
    static class ConfiguracionReloj {
        @Bean
        @Primary
        RelojAjustable relojDePrueba() {
            return new RelojAjustable(INICIO, ZONA);
        }
    }

    @Autowired
    private TokenActivacionService service;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private ContraseniaPendienteService contraseniaPendiente;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reiniciarReloj() {
        reloj.fijar(INICIO);
    }

    private Estudiante pendiente() {
        Estudiante estudiante = new Estudiante();
        estudiante.setCarnet(CarnetsDePrueba.nuevo("TA"));
        estudiante.setCorreoInstitucional("token." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv");
        estudiante.setContrasenia(contraseniaPendiente.generarHash());
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setEstadoCorreo(EstadoCorreo.PENDIENTE);
        return estudianteRepository.save(estudiante);
    }

    private TokenActivacion fila(String valor) {
        return tokenRepository.findByHashToken(sha256(valor)).orElseThrow();
    }

    // Cálculo independiente del servicio, para no validar el hash con el mismo código que lo genera
    private static String sha256(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ================= generación y almacenamiento

    @Test
    void generaTokensDe32BytesEnBase64UrlSinRellenoYDistintosEntreSi() {
        Estudiante estudiante = pendiente();
        Set<String> valores = new HashSet<>();

        for (int i = 0; i < 20; i++) {
            String valor = service.generar(estudiante).valor();
            assertThat(valor).matches("^[A-Za-z0-9_-]{43}$");
            assertThat(Base64.getUrlDecoder().decode(valor)).hasSize(32);
            valores.add(valor);
        }

        assertThat(valores).hasSize(20);
    }

    @Test
    void soloSeGuardaElSha256DelToken() {
        Estudiante estudiante = pendiente();
        TokenGenerado generado = service.generar(estudiante);

        TokenActivacion token = fila(generado.valor());
        assertThat(token.getHashToken()).isEqualTo(sha256(generado.valor())).hasSize(64).isNotEqualTo(generado.valor());

        Set<String> columnas = new HashSet<>(jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'tokens_activacion'", String.class));
        // Ninguna columna para el valor en claro; las de estado del envío solo guardan fechas
        assertThat(columnas).containsExactlyInAnyOrder(
                "id", "estudiante_id", "hash_token", "creado_en", "expira_en", "usado_en", "invalidado_en",
                "envio_intentado_en", "enviado_en", "envio_descartado_en");

        for (Map<String, Object> filaGuardada : jdbc.queryForList("select * from tokens_activacion")) {
            assertThat(filaGuardada.values()).noneMatch(valor -> String.valueOf(valor).contains(generado.valor()));
        }
    }

    @Test
    void niElTokenNiSuHashAparecenEnLosRegistros(CapturedOutput salida) {
        Estudiante estudiante = pendiente();
        TokenGenerado generado = service.generar(estudiante);
        service.consultar(generado.valor());
        service.consumir(generado.valor());
        service.consumir(generado.valor());

        assertThat(salida.getAll()).doesNotContain(generado.valor()).doesNotContain(sha256(generado.valor()));
        assertThat(generado.toString()).doesNotContain(generado.valor());
    }

    // ================= vigencia

    @Test
    void venceExactamenteALas24Horas() {
        Estudiante estudiante = pendiente();
        TokenGenerado generado = service.generar(estudiante);

        assertThat(generado.expiraEn()).isEqualTo(LocalDateTime.ofInstant(INICIO, ZONA).plusHours(24));

        reloj.fijar(INICIO.plus(Duration.ofHours(24)).minusNanos(1_000));
        assertThat(service.consultar(generado.valor()).estado()).isEqualTo(EstadoToken.VALIDO);

        reloj.fijar(INICIO.plus(Duration.ofHours(24)));
        assertThat(service.consultar(generado.valor()).estado()).isEqualTo(EstadoToken.EXPIRADO);
        assertThat(service.consumir(generado.valor()).estado()).isEqualTo(EstadoToken.EXPIRADO);
        assertThat(fila(generado.valor()).getUsadoEn()).isNull();
    }

    @Test
    void unMicrosegundoAntesDeVencerAunSePuedeConsumir() {
        Estudiante estudiante = pendiente();
        TokenGenerado generado = service.generar(estudiante);

        reloj.fijar(INICIO.plus(Duration.ofHours(24)).minusNanos(1_000));
        ResultadoToken resultado = service.consumir(generado.valor());

        assertThat(resultado.estado()).isEqualTo(EstadoToken.CONSUMIDO);
        assertThat(resultado.estudiante().getId()).isEqualTo(estudiante.getId());
    }

    // ================= un solo uso e invalidación

    @Test
    void unTokenUsadoNoSePuedeUsarOtraVez() {
        Estudiante estudiante = pendiente();
        TokenGenerado generado = service.generar(estudiante);

        ResultadoToken primero = service.consumir(generado.valor());
        reloj.avanzar(Duration.ofMinutes(5));
        ResultadoToken segundo = service.consumir(generado.valor());

        assertThat(primero.estado()).isEqualTo(EstadoToken.CONSUMIDO);
        assertThat(primero.estudiante().getId()).isEqualTo(estudiante.getId());
        assertThat(segundo.estado()).isEqualTo(EstadoToken.USADO);
        assertThat(service.consultar(generado.valor()).estado()).isEqualTo(EstadoToken.USADO);
        assertThat(fila(generado.valor()).getUsadoEn()).isEqualTo(LocalDateTime.ofInstant(INICIO, ZONA));
    }

    @Test
    void emitirOtroTokenInvalidaElAnteriorSinAfectarAOtrosEstudiantes() {
        Estudiante estudiante = pendiente();
        Estudiante otro = pendiente();
        TokenGenerado anterior = service.generar(estudiante);
        TokenGenerado delOtro = service.generar(otro);

        reloj.avanzar(Duration.ofMinutes(2));
        TokenGenerado nuevo = service.generar(estudiante);

        ResultadoToken resultadoAnterior = service.consumir(anterior.valor());
        assertThat(resultadoAnterior.estado()).isEqualTo(EstadoToken.INVALIDO);
        assertThat(resultadoAnterior.estudiante()).isNull();
        assertThat(fila(anterior.valor()).getInvalidadoEn()).isEqualTo(LocalDateTime.ofInstant(INICIO.plus(Duration.ofMinutes(2)), ZONA));
        assertThat(fila(anterior.valor()).getUsadoEn()).isNull();

        assertThat(service.consumir(nuevo.valor()).estado()).isEqualTo(EstadoToken.CONSUMIDO);
        assertThat(service.consumir(delOtro.valor()).estado()).isEqualTo(EstadoToken.CONSUMIDO);
    }

    @Test
    void unTokenVencidoSigueConstandoComoExpiradoAunqueSeEmitaOtro() {
        Estudiante estudiante = pendiente();
        TokenGenerado vencido = service.generar(estudiante);

        reloj.avanzar(Duration.ofHours(25));
        service.generar(estudiante);

        assertThat(service.consultar(vencido.valor()).estado()).isEqualTo(EstadoToken.EXPIRADO);
        assertThat(fila(vencido.valor()).getInvalidadoEn()).isNull();
    }

    @Test
    void losTokensUsadosVencidosEInvalidadosSeConservanParaAuditoria() {
        Estudiante estudiante = pendiente();
        TokenGenerado vencido = service.generar(estudiante);
        reloj.avanzar(Duration.ofHours(25));
        TokenGenerado invalidado = service.generar(estudiante);
        reloj.avanzar(Duration.ofMinutes(1));
        TokenGenerado usado = service.generar(estudiante);
        service.consumir(usado.valor());

        List<TokenActivacion> tokens = tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(estudiante.getId());
        assertThat(tokens).extracting(TokenActivacion::getHashToken)
                .containsExactly(sha256(vencido.valor()), sha256(invalidado.valor()), sha256(usado.valor()));
        assertThat(tokens.get(0).getInvalidadoEn()).isNull();
        assertThat(tokens.get(1).getInvalidadoEn()).isNotNull();
        assertThat(tokens.get(2).getUsadoEn()).isNotNull();
    }

    @Test
    void unTokenInexistenteOMalFormadoEsInvalido() {
        byte[] bytes = new byte[32];
        String desconocido = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        for (String valor : new String[] {null, "", "abc", desconocido, desconocido + "=", desconocido.replace('A', '+'), "x".repeat(10_000)}) {
            assertThat(service.consultar(valor).estado()).isEqualTo(EstadoToken.INVALIDO);
            assertThat(service.consumir(valor).estado()).isEqualTo(EstadoToken.INVALIDO);
            assertThat(service.consumir(valor).estudiante()).isNull();
        }
    }

    // ================= concurrencia

    @Test
    void deDosConsumosSimultaneosSoloUnoActivaLaCuenta() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            for (int intento = 0; intento < 10; intento++) {
                String valor = service.generar(pendiente()).valor();
                CyclicBarrier salida = new CyclicBarrier(2);

                List<Future<EstadoToken>> resultados = new ArrayList<>();
                for (int hilo = 0; hilo < 2; hilo++) {
                    resultados.add(hilos.submit(() -> {
                        salida.await(5, TimeUnit.SECONDS);
                        return service.consumir(valor).estado();
                    }));
                }

                List<EstadoToken> estados = new ArrayList<>();
                for (Future<EstadoToken> resultado : resultados) {
                    estados.add(resultado.get(30, TimeUnit.SECONDS));
                }
                assertThat(estados).as("intento %d", intento).containsExactlyInAnyOrder(EstadoToken.CONSUMIDO, EstadoToken.USADO);
            }
        } finally {
            hilos.shutdownNow();
        }
    }
}
