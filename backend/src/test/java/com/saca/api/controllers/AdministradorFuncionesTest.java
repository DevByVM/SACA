package com.saca.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.Administrador;
import com.saca.api.entity.Estudiante;
import com.saca.api.config.AdministradorBootstrap;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.TokenService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministradorFuncionesTest {

    private static final String PASSWORD = "Administrador123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdministradorRepository administradores;

    @Autowired
    private EstudianteRepository estudiantes;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Administrador administrador;
    private Estudiante estudiante;
    private String tokenAdministrador;
    private String tokenEstudiante;

    @BeforeEach
    void prepararCuentas() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        administrador = new Administrador();
        administrador.setNombre("Admin Prueba");
        administrador.setCorreo("admin." + sufijo + "@ues.edu.sv");
        administrador.setContrasenia(encoder.encode(PASSWORD));
        administrador = administradores.save(administrador);

        estudiante = new Estudiante();
        estudiante.setNombre("Estudiante Prueba");
        estudiante.setCarnet("AD" + sufijo);
        estudiante.setCorreoInstitucional("estudiante." + sufijo + "@ues.edu.sv");
        estudiante.setContrasenia(encoder.encode("Estudiante123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante = estudiantes.save(estudiante);

        tokenAdministrador = tokenService.emitir(administrador).valor();
        tokenEstudiante = tokenService.emitir(estudiante).valor();
    }

    @Test
    void administradorPuedeIniciarSesionConElMismoFormularioYRecibeSuRol() throws Exception {
        String cuerpo = """
                {"correoInstitucional":"%s","contrasenia":"%s"}
                """.formatted(administrador.getCorreo(), PASSWORD);
        JsonNode respuesta = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(respuesta.get("autenticado").asBoolean()).isTrue();
        assertThat(respuesta.get("rol").asText()).isEqualTo("ADMINISTRADOR");
        assertThat(respuesta.get("carnet").isNull()).isTrue();
        String token = respuesta.get("token").asText();
        assertThat(token).isNotBlank();
        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void unaContraseniaAdministrativaIncorrectaNoEmiteSesion() throws Exception {
        String cuerpo = """
                {"correoInstitucional":"%s","contrasenia":"ClaveIncorrecta123"}
                """.formatted(administrador.getCorreo());
        JsonNode respuesta = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(respuesta.get("autenticado").asBoolean()).isFalse();
        assertThat(respuesta.get("token").isNull()).isTrue();
    }

    @Test
    void funcionesAdministrativasExigenSesionYRolAdministrador() throws Exception {
        for (String ruta : List.of("/api/admin/usuarios", "/api/admin/catalogo-materias",
                "/api/admin/tipos-actividad", "/api/admin/plantillas-planificacion")) {
            mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenEstudiante))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenAdministrador))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/admin/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/usuarios").header("Authorization", "Bearer " + tokenEstudiante)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginEstudiantilConservaSuRolYSuAccesoAlPerfil() throws Exception {
        String cuerpo = """
                {"correoInstitucional":"%s","contrasenia":"Estudiante123"}
                """.formatted(estudiante.getCorreoInstitucional());
        JsonNode respuesta = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(respuesta.get("autenticado").asBoolean()).isTrue();
        assertThat(respuesta.get("rol").asText()).isEqualTo("ESTUDIANTE");
        String token = respuesta.get("token").asText();
        mockMvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + tokenAdministrador))
                .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapGuardaElHashYNoReemplazaUnaCuentaYaCreada() {
        String correo = "bootstrap." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv";
        String password = "ClaveInicial123";
        AdministradorBootstrap bootstrap = new AdministradorBootstrap(administradores, estudiantes, correo,
                "Admin Inicial", password);

        bootstrap.run(null);

        Administrador guardado = administradores.findByCorreoIgnoreCase(correo).orElseThrow();
        assertThat(guardado.getContrasenia()).isNotEqualTo(password);
        assertThat(new BCryptPasswordEncoder().matches(password, guardado.getContrasenia())).isTrue();

        new AdministradorBootstrap(administradores, estudiantes, correo, "Otro nombre", "OtraClave123456")
                .run(null);
        assertThat(administradores.findByCorreoIgnoreCase(correo).orElseThrow().getContrasenia())
                .isEqualTo(guardado.getContrasenia());

        assertThatThrownBy(() -> new AdministradorBootstrap(administradores, estudiantes, correo, "", password).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SACA_ADMIN_CORREO");
    }

    @Test
    void soloElAdministradorAccedeALaGestionYElCatalogoActivoEsVisibleParaEstudiantes() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/usuarios").header("Authorization", "Bearer " + tokenAdministrador))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/catalogo-materias").header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/catalogo-materias")
                .header("Authorization", "Bearer " + tokenEstudiante)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"TEST-1","nombre":"Materia de prueba"}
                        """))
                .andExpect(status().isForbidden());

        String catalogoCreado = mockMvc.perform(post("/api/admin/catalogo-materias")
                .header("Authorization", "Bearer " + tokenAdministrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"codigo":"TEST-1","nombre":"Materia de prueba"}
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(catalogoCreado).get("id").asLong();

        JsonNode listado = objectMapper.readTree(mockMvc.perform(get("/api/catalogo-materias")
                .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(listado.findValuesAsText("codigo")).contains("TEST-1");

        mockMvc.perform(patch("/api/admin/catalogo-materias/" + id + "/estado")
                .header("Authorization", "Bearer " + tokenAdministrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activa\":false}"))
                .andExpect(status().isOk());

        JsonNode catalogoDesactivado = objectMapper.readTree(mockMvc.perform(get("/api/catalogo-materias")
                .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(catalogoDesactivado.findValuesAsText("codigo")).doesNotContain("TEST-1");
    }

    @Test
    void inhabilitarCuentaImpideNuevosLoginsYTumbaLaSesionYaEmitida() throws Exception {
        mockMvc.perform(patch("/api/admin/usuarios/" + estudiante.getId() + "/estado")
                .header("Authorization", "Bearer " + tokenAdministrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"habilitada\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isUnauthorized());

        String login = """
                {"correoInstitucional":"%s","contrasenia":"Estudiante123"}
                """.formatted(estudiante.getCorreoInstitucional());
        JsonNode respuesta = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(respuesta.get("autenticado").asBoolean()).isFalse();
    }
}
