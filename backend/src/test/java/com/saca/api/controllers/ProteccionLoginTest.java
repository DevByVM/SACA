package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.RelojAjustable;
import com.saca.api.dto.LoginRequest;
import com.saca.api.dto.LoginResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.proteccion.LimiteSolicitudesExcedidoException;
import com.saca.api.proteccion.ProteccionSolicitudes;
import com.saca.api.proteccion.ResolutorIpCliente;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.EstudianteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Límite de inicios de sesión fallidos por cliente. Los valores se fijan con los nombres
// de las variables de entorno, como en un despliegue: 3 fallos cada 10 minutos y 5 cada 24 horas
@SpringBootTest(properties = {
        "SACA_LIMITE_LOGIN_FALLOS_10_MIN=3",
        "SACA_LIMITE_LOGIN_FALLOS_24_H=5",
        "SACA_IP_REDES_COMPARTIDAS=198.51.100.0/24",
        "SACA_LIMITE_MULTIPLICADOR_REDES_COMPARTIDAS=10"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProteccionLoginTest {

    private static final String CLAVE = "Segura123";

    @TestConfiguration
    static class ConfiguracionReloj {
        @Bean
        @Primary
        RelojAjustable relojDePrueba() {
            return new RelojAjustable(Instant.now(), ZoneId.of("America/El_Salvador"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelojAjustable reloj;

    @Autowired
    private AuthController authController;

    @Autowired
    private EstudianteService estudianteService;

    @Autowired
    private ResolutorIpCliente resolutorIp;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Cada prueba usa IP propias, pero el reloj es común: se reinicia para que los JWT emitidos sean vigentes
    @BeforeEach
    void reiniciarReloj() {
        reloj.fijar(Instant.now());
    }

    private Estudiante cuenta() {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Prueba Limite");
        estudiante.setCarnet(CarnetsDePrueba.nuevo("PL"));
        estudiante.setCorreoInstitucional("limite." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(CLAVE));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        return repository.save(estudiante);
    }

    private MockHttpServletResponse login(String ip, String correo, String contrasenia, String... reenvios) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(Map.of("correoInstitucional", correo, "contrasenia", contrasenia));
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .with(peticion -> {
                    peticion.setRemoteAddr(ip);
                    for (String reenvio : reenvios) {
                        peticion.addHeader("X-Forwarded-For", reenvio);
                    }
                    return peticion;
                })).andReturn().getResponse();
    }

    private MockHttpServletResponse fallo(String ip, String... reenvios) throws Exception {
        return login(ip, "no.existe." + UUID.randomUUID() + "@ues.edu.sv", "Incorrecta1", reenvios);
    }

    private static void assertLimitado(MockHttpServletResponse respuesta) throws Exception {
        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(respuesta.getHeader("Retry-After"))).isPositive();
        String cuerpo = respuesta.getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("Demasiados intentos de inicio de sesión desde tu red").doesNotContain("token");
    }

    @Test
    void trasLosFallosPermitidosResponde429SinTocarLaCuenta() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            assertThat(fallo(ip).getStatus()).isEqualTo(200);
        }

        // Ni siquiera una cuenta con su contraseña correcta entra desde esa IP, y su contador no cambia
        Estudiante legitima = cuenta();
        MockHttpServletResponse respuesta = login(ip, legitima.getCorreoInstitucional(), CLAVE);
        assertLimitado(respuesta);
        assertThat(Long.parseLong(respuesta.getHeader("Retry-After"))).isBetween(1L, 600L);
        Estudiante guardada = repository.findById(legitima.getId()).orElseThrow();
        assertThat(guardada.getIntentosFallidos()).isZero();
        assertThat(guardada.getBloqueadoHasta()).isNull();

        // Desde otra IP la misma cuenta entra con normalidad
        JsonNode correcto = objectMapper.readTree(login("203.0.113.11", legitima.getCorreoInstitucional(), CLAVE).getContentAsString());
        assertThat(correcto.get("autenticado").asBoolean()).isTrue();
    }

    @Test
    void losInicioDeSesionCorrectosNoConsumenElLimite() throws Exception {
        Estudiante estudiante = cuenta();
        for (int i = 0; i < 10; i++) {
            JsonNode cuerpo = objectMapper.readTree(login("203.0.113.20", estudiante.getCorreoInstitucional(), CLAVE).getContentAsString());
            assertThat(cuerpo.get("autenticado").asBoolean()).as("intento %d", i).isTrue();
        }
    }

    @Test
    void elLimiteSeReiniciaAlVencerLaVentanaYRespetaElDiario() throws Exception {
        String ip = "203.0.113.30";
        for (int i = 0; i < 3; i++) {
            fallo(ip);
        }
        assertLimitado(fallo(ip));

        reloj.avanzar(Duration.ofMinutes(11));
        assertThat(fallo(ip).getStatus()).isEqualTo(200);
        assertThat(fallo(ip).getStatus()).isEqualTo(200);

        // 5 fallos en el día: se alcanza el límite de 24 horas aunque la ventana de 10 minutos tenga cupo
        reloj.avanzar(Duration.ofMinutes(11));
        MockHttpServletResponse diario = fallo(ip);
        assertLimitado(diario);
        assertThat(Long.parseLong(diario.getHeader("Retry-After"))).isGreaterThan(Duration.ofHours(20).toSeconds());

        reloj.avanzar(Duration.ofHours(24));
        assertThat(fallo(ip).getStatus()).isEqualTo(200);
    }

    @Test
    void unaRedCompartidaDeclaradaTieneDiezVecesMasMargenPeroTambienSeLimita() throws Exception {
        String ip = "198.51.100.20";
        for (int i = 0; i < 5 * 10; i++) {
            assertThat(fallo(ip).getStatus()).as("fallo %d", i).isEqualTo(200);
            if ((i + 1) % (3 * 10) == 0) {
                reloj.avanzar(Duration.ofMinutes(11));
            }
        }
        assertLimitado(fallo(ip));
    }

    @Test
    void cambiarXForwardedForNoEvitaElLimite() throws Exception {
        String ip = "203.0.113.40";
        for (int i = 0; i < 3; i++) {
            fallo(ip, "10.9.9." + i);
        }

        assertLimitado(fallo(ip, "1.2.3.4"));
        assertLimitado(login(ip, cuenta().getCorreoInstitucional(), CLAVE, "198.51.100.77"));
    }

    @Test
    void elBloqueoPorCuentaSigueIgualDentroDelLimite() throws Exception {
        Estudiante estudiante = cuenta();
        String ip = "203.0.113.50";
        for (int i = 0; i < 3; i++) {
            JsonNode cuerpo = objectMapper.readTree(login(ip, estudiante.getCorreoInstitucional(), "Incorrecta1").getContentAsString());
            assertThat(cuerpo.get("mensaje").asText()).isEqualTo("Credenciales incorrectas");
        }

        Estudiante bloqueada = repository.findById(estudiante.getId()).orElseThrow();
        assertThat(bloqueada.getIntentosFallidos()).isEqualTo(3);
        assertThat(bloqueada.getBloqueadoHasta()).isNotNull();

        // Desde otra IP la cuenta sigue bloqueada por su propia regla y la respuesta es la genérica
        JsonNode otraIp = objectMapper.readTree(login("203.0.113.51", estudiante.getCorreoInstitucional(), CLAVE).getContentAsString());
        assertThat(otraIp.get("autenticado").asBoolean()).isFalse();
        assertThat(otraIp.get("mensaje").asText()).isEqualTo("Credenciales incorrectas");
    }

    // ================= concurrencia: se llama al controlador desde varios hilos con comparaciones BCrypt reales

    private enum Desenlace { CORRECTO, FALLIDO, LIMITADO }

    private Callable<Desenlace> intento(String ip, String correo, String contrasenia) {
        return intento(authController, ip, correo, contrasenia);
    }

    private static Callable<Desenlace> intento(AuthController controlador, String ip, String correo, String contrasenia) {
        return () -> {
            LoginRequest peticion = new LoginRequest();
            peticion.setCorreoInstitucional(correo);
            peticion.setContrasenia(contrasenia);
            MockHttpServletRequest solicitud = new MockHttpServletRequest();
            solicitud.setRemoteAddr(ip);
            try {
                LoginResponse respuesta = controlador.login(peticion, solicitud);
                return respuesta.autenticado() ? Desenlace.CORRECTO : Desenlace.FALLIDO;
            } catch (LimiteSolicitudesExcedidoException e) {
                return Desenlace.LIMITADO;
            }
        };
    }

    private Callable<Desenlace> intentoFallido(String ip) {
        return intento(ip, "no.existe." + UUID.randomUUID() + "@ues.edu.sv", "Incorrecta1");
    }

    // Todos los intentos esperan a una señal común para empezar a la vez
    private static List<Desenlace> simultaneos(int hilos, List<Callable<Desenlace>> intentos) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<Desenlace>> pendientes = new ArrayList<>();
            for (Callable<Desenlace> intento : intentos) {
                pendientes.add(ejecutor.submit(() -> {
                    salida.await();
                    return intento.call();
                }));
            }
            salida.countDown();
            List<Desenlace> desenlaces = new ArrayList<>();
            for (Future<Desenlace> pendiente : pendientes) {
                desenlaces.add(pendiente.get(60, TimeUnit.SECONDS));
            }
            return desenlaces;
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private static long contar(List<Desenlace> desenlaces, Desenlace buscado) {
        return desenlaces.stream().filter(buscado::equals).count();
    }

    private int fallosAdmitidosHastaLimite(String ip) throws Exception {
        int admitidos = 0;
        while (intentoFallido(ip).call() == Desenlace.FALLIDO) {
            admitidos++;
            assertThat(admitidos).isLessThan(100);
        }
        return admitidos;
    }

    // Antes, todos los intentos pasaban la comprobación mientras los primeros seguían en BCrypt
    @Test
    void losFallosSimultaneosDesdeUnaIpNoSuperanElLimite() throws Exception {
        List<Callable<Desenlace>> intentos = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            intentos.add(intentoFallido("203.0.113.60"));
        }

        List<Desenlace> desenlaces = simultaneos(40, intentos);

        assertThat(contar(desenlaces, Desenlace.FALLIDO)).isEqualTo(3);
        assertThat(contar(desenlaces, Desenlace.LIMITADO)).isEqualTo(37);
        assertThat(fallosAdmitidosHastaLimite("203.0.113.61")).isEqualTo(3);
    }

    // Dos direcciones del mismo prefijo /64 comparten límite; otro prefijo tiene el suyo
    @Test
    void losLimitesSimultaneosSonIndependientesPorPrefijoIpv6() throws Exception {
        List<Callable<Desenlace>> intentos = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            intentos.add(intentoFallido("2001:db8:60:1::" + Integer.toHexString(i + 1)));
            intentos.add(intentoFallido("2001:db8:60:2::" + Integer.toHexString(i + 1)));
        }

        List<Desenlace> desenlaces = simultaneos(30, intentos);

        List<Desenlace> primerPrefijo = new ArrayList<>();
        List<Desenlace> segundoPrefijo = new ArrayList<>();
        for (int i = 0; i < desenlaces.size(); i++) {
            (i % 2 == 0 ? primerPrefijo : segundoPrefijo).add(desenlaces.get(i));
        }
        assertThat(contar(primerPrefijo, Desenlace.FALLIDO)).isEqualTo(3);
        assertThat(contar(segundoPrefijo, Desenlace.FALLIDO)).isEqualTo(3);
    }

    // Con como mucho tres intentos en curso a la vez (el límite), ninguno correcto se rechaza ni consume cupo
    @Test
    void losLoginsCorrectosSimultaneosNoConsumenElLimite() throws Exception {
        String ip = "203.0.113.70";
        List<Callable<Desenlace>> intentos = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            intentos.add(intento(ip, cuenta().getCorreoInstitucional(), CLAVE));
        }

        List<Desenlace> desenlaces = simultaneos(3, intentos);

        assertThat(desenlaces).containsOnly(Desenlace.CORRECTO);
        assertThat(fallosAdmitidosHastaLimite(ip)).isEqualTo(3);
    }

    // Correctos y fallidos a la vez: nunca más de tres fallos admitidos, y al terminar los correctos no ocupan
    // cupo. Un correcto puede recibir 429 si coincide con el límite ocupado por intentos aún en curso
    @Test
    void conIntentosMezcladosSoloLosFallosConsumenElLimite() throws Exception {
        String ip = "203.0.113.80";
        List<Callable<Desenlace>> correctos = new ArrayList<>();
        List<Callable<Desenlace>> intentos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            correctos.add(intento(ip, cuenta().getCorreoInstitucional(), CLAVE));
        }
        for (int i = 0; i < 10; i++) {
            intentos.add(correctos.get(i));
            intentos.add(intentoFallido(ip));
        }

        List<Desenlace> desenlaces = simultaneos(20, intentos);

        long fallosAdmitidos = contar(desenlaces, Desenlace.FALLIDO);
        assertThat(fallosAdmitidos).isBetween(0L, 3L);
        for (int i = 0; i < desenlaces.size(); i += 2) {
            assertThat(desenlaces.get(i)).isIn(Desenlace.CORRECTO, Desenlace.LIMITADO);
        }
        assertThat(fallosAdmitidosHastaLimite(ip)).isEqualTo(3 - (int) fallosAdmitidos);
    }

    // ================= login sin capacidad en el limitador

    // Con las 5 claves de login ocupadas (máximo de 10 claves en total) los intentos de una IP nueva siguen sin
    // contarse por IP: ni el límite de 3 fallos los frena. El bloqueo de cada cuenta se aplica igual
    @Test
    void sinCapacidadEnElLoginSeMantieneElBloqueoPorCuenta() throws Exception {
        AuthController controlador = new AuthController();
        ReflectionTestUtils.setField(controlador, "service", estudianteService);
        ReflectionTestUtils.setField(controlador, "resolutorIp", resolutorIp);
        ReflectionTestUtils.setField(controlador, "proteccion",
                new ProteccionSolicitudes(reloj, 10, 10, 3, 5, 20, 100, 30, 500));
        for (int i = 0; i < 5; i++) {
            String ocupante = "203.0.113.9" + i;
            assertThat(intento(controlador, ocupante, "no.existe." + UUID.randomUUID() + "@ues.edu.sv", "Incorrecta1").call())
                    .isEqualTo(Desenlace.FALLIDO);
        }

        String ip = "203.0.113.99";
        for (int i = 0; i < 10; i++) {
            assertThat(intento(controlador, ip, "no.existe." + UUID.randomUUID() + "@ues.edu.sv", "Incorrecta1").call())
                    .as("fallo %d", i).isEqualTo(Desenlace.FALLIDO);
        }

        Estudiante estudiante = cuenta();
        for (int i = 0; i < 3; i++) {
            assertThat(intento(controlador, ip, estudiante.getCorreoInstitucional(), "Incorrecta1").call())
                    .isEqualTo(Desenlace.FALLIDO);
        }
        assertThat(intento(controlador, ip, estudiante.getCorreoInstitucional(), CLAVE).call()).isEqualTo(Desenlace.FALLIDO);
        Estudiante bloqueada = repository.findById(estudiante.getId()).orElseThrow();
        assertThat(bloqueada.getIntentosFallidos()).isEqualTo(3);
        assertThat(bloqueada.getBloqueadoHasta()).isNotNull();

        Estudiante otra = cuenta();
        assertThat(intento(controlador, "203.0.113.98", otra.getCorreoInstitucional(), CLAVE).call())
                .isEqualTo(Desenlace.CORRECTO);
    }
}
