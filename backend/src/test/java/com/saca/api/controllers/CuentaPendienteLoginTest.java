package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.ContraseniaPendienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Una cuenta pendiente de activación no obtiene sesión por ninguna vía; las cuentas anteriores
// (sin estado) y las confirmadas inician sesión como hasta ahora
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CuentaPendienteLoginTest {

    private static final String CLAVE = "Segura123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private ContraseniaPendienteService contraseniaPendiente;

    @Autowired
    private JwtDecoder decodificadorToken;

    @Autowired
    private ObjectMapper objectMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Estudiante crear(EstadoCorreo estado, String hashContrasenia) {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Prueba Activacion");
        estudiante.setCarnet(CarnetsDePrueba.nuevo("CP"));
        estudiante.setCorreoInstitucional("pendiente." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv");
        estudiante.setContrasenia(hashContrasenia);
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setEstadoCorreo(estado);
        if (estado == EstadoCorreo.CONFIRMADO) {
            estudiante.setCorreoConfirmadoEn(LocalDateTime.now());
        }
        return repository.save(estudiante);
    }

    private MockHttpServletResponse login(Estudiante estudiante, String contrasenia) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(Map.of(
                "correoInstitucional", estudiante.getCorreoInstitucional(), "contrasenia", contrasenia));
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse();
    }

    private JsonNode json(MockHttpServletResponse respuesta) throws Exception {
        return objectMapper.readTree(respuesta.getContentAsString());
    }

    private void assertSesionValida(Estudiante estudiante) throws Exception {
        MockHttpServletResponse respuesta = login(estudiante, CLAVE);
        JsonNode cuerpo = json(respuesta);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cuerpo.get("autenticado").asBoolean()).isTrue();
        String token = cuerpo.get("token").asText();
        assertThat(decodificadorToken.decode(token).getSubject()).isEqualTo(String.valueOf(estudiante.getId()));
        assertThat(mockMvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unaCuentaPendienteConSuContraseniaAleatoriaNoPuedeIniciarSesion() throws Exception {
        Estudiante pendiente = crear(EstadoCorreo.PENDIENTE, contraseniaPendiente.generarHash());

        // El tercer fallo activa el bloqueo temporal, que responde con el mismo mensaje que cualquier otro fallo
        for (String intento : new String[] {CLAVE, "", "PENDIENTE"}) {
            JsonNode cuerpo = json(login(pendiente, intento));

            assertThat(cuerpo.get("autenticado").asBoolean()).as(intento).isFalse();
            assertThat(cuerpo.get("token").isNull()).as(intento).isTrue();
            assertThat(cuerpo.get("mensaje").asText()).as(intento).isEqualTo("Credenciales incorrectas");
        }
    }

    // Caso que hoy no puede crearse por la API (lo producirá la corrección administrativa futura): aunque la
    // contraseña sea correcta, el login responde como cualquier fallo y no llega a pedir un token
    @Test
    void unaCuentaPendienteConContraseniaConocidaTampocoObtieneToken() throws Exception {
        Estudiante pendiente = crear(EstadoCorreo.PENDIENTE, passwordEncoder.encode(CLAVE));

        MockHttpServletResponse respuesta = login(pendiente, CLAVE);
        JsonNode cuerpo = json(respuesta);

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(cuerpo.get("autenticado").asBoolean()).isFalse();
        assertThat(cuerpo.get("mensaje").asText()).isEqualTo("Credenciales incorrectas");
        assertThat(cuerpo.get("token").isNull()).isTrue();
        assertThat(respuesta.getContentAsString()).doesNotContain("eyJ");
    }

    @Test
    void unaCuentaAnteriorSinEstadoIniciaSesionComoAntes() throws Exception {
        Estudiante anterior = crear(null, passwordEncoder.encode(CLAVE));

        assertSesionValida(anterior);
        assertThat(repository.findById(anterior.getId()).orElseThrow().getEstadoCorreo()).isNull();
    }

    @Test
    void unaCuentaConfirmadaIniciaSesion() throws Exception {
        Estudiante confirmada = crear(EstadoCorreo.CONFIRMADO, passwordEncoder.encode(CLAVE));

        assertSesionValida(confirmada);
    }

    @Test
    void elEstadoDelCorreoNoSaleEnElPerfil() throws Exception {
        Estudiante confirmada = crear(EstadoCorreo.CONFIRMADO, passwordEncoder.encode(CLAVE));
        String token = json(login(confirmada, CLAVE)).get("token").asText();

        String perfil = mockMvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        assertThat(perfil).doesNotContain("estadoCorreo").doesNotContain("CONFIRMADO").doesNotContain("correoConfirmadoEn");
    }
}
