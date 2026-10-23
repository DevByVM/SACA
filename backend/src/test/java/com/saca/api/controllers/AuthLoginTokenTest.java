package com.saca.api.controllers;

import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Con los filtros de seguridad activos: el login emite el token solo para credenciales válidas
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthLoginTokenTest {

    private static final String CLAVE = "Segura123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private JwtDecoder decodificadorToken;

    @Autowired
    private ObjectMapper objectMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Estudiante crearEstudiante(boolean activa, Timestamp bloqueadoHasta) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Prueba Token");
        estudiante.setCarnet("TK" + unico);
        estudiante.setCorreoInstitucional("token." + unico + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(CLAVE));
        estudiante.setCuentaActiva(activa);
        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(bloqueadoHasta);
        return repository.save(estudiante);
    }

    private JsonNode login(String correo, String contrasenia) throws Exception {
        String cuerpo = """
                {"correoInstitucional":"%s","contrasenia":"%s"}
                """.formatted(correo, contrasenia);
        String respuesta = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta);
    }

    private static void assertSinToken(JsonNode respuesta, String mensaje) {
        assertThat(respuesta.get("autenticado").asBoolean()).isFalse();
        assertThat(respuesta.get("mensaje").asText()).isEqualTo(mensaje);
        assertThat(respuesta.get("token").isNull()).isTrue();
        assertThat(respuesta.get("expiraEn").isNull()).isTrue();
    }

    @Test
    void loginCorrectoDevuelveLosCamposDeSiempreYUnTokenVerificable() throws Exception {
        Estudiante estudiante = crearEstudiante(true, null);

        JsonNode respuesta = login(estudiante.getCorreoInstitucional(), CLAVE);

        assertThat(respuesta.get("autenticado").asBoolean()).isTrue();
        assertThat(respuesta.get("mensaje").asText()).isEqualTo("LOGIN_OK");
        assertThat(respuesta.get("estudianteId").asLong()).isEqualTo(estudiante.getId());
        assertThat(respuesta.get("nombre").asText()).isEqualTo("Prueba Token");
        assertThat(respuesta.get("carnet").asText()).isEqualTo(estudiante.getCarnet());
        assertThat(respuesta.get("correoInstitucional").asText()).isEqualTo(estudiante.getCorreoInstitucional());

        Jwt jwt = decodificadorToken.decode(respuesta.get("token").asText());
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(estudiante.getId()));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(8));
        assertThat(Instant.parse(respuesta.get("expiraEn").asText())).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void contraseniaIncorrectaNoEmiteTokenYSigueContandoElIntento() throws Exception {
        Estudiante estudiante = crearEstudiante(true, null);

        assertSinToken(login(estudiante.getCorreoInstitucional(), "Incorrecta1"), "Credenciales incorrectas");

        assertThat(repository.findById(estudiante.getId()).orElseThrow().getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void usuarioInexistenteNoEmiteToken() throws Exception {
        assertSinToken(login("no.existe." + UUID.randomUUID() + "@ues.edu.sv", CLAVE), "Usuario no encontrado");
    }

    @Test
    void cuentaInactivaNoEmiteTokenAunqueLaContraseniaSeaCorrecta() throws Exception {
        Estudiante estudiante = crearEstudiante(false, null);

        assertSinToken(login(estudiante.getCorreoInstitucional(), CLAVE), "Cuenta no activada");
    }

    @Test
    void cuentaBloqueadaNoEmiteTokenAunqueLaContraseniaSeaCorrecta() throws Exception {
        Estudiante estudiante = crearEstudiante(true, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)));

        assertSinToken(login(estudiante.getCorreoInstitucional(), CLAVE), "Cuenta bloqueada temporalmente");
    }

    @Test
    void alTercerIntentoFallidoSeBloqueaYNoSeEmiteToken() throws Exception {
        Estudiante estudiante = crearEstudiante(true, null);
        login(estudiante.getCorreoInstitucional(), "Incorrecta1");
        login(estudiante.getCorreoInstitucional(), "Incorrecta1");

        assertSinToken(login(estudiante.getCorreoInstitucional(), "Incorrecta1"), "Cuenta bloqueada por 5 minutos");
        assertSinToken(login(estudiante.getCorreoInstitucional(), CLAVE), "Cuenta bloqueada temporalmente");
    }

    // Desde la etapa 4 todas las rutas salvo login y registro exigen un token válido
    @Test
    void lasRutasDeDatosRechazanTantoUnTokenInvalidoComoLaFaltaDeToken() throws Exception {
        mockMvc.perform(get("/api/disponibilidades").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/disponibilidades"))
                .andExpect(status().isUnauthorized());
    }
}
