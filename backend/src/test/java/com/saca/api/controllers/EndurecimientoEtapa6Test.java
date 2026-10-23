package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Etapa 6: CORS sin comodines, cambio de contraseña con la actual y errores HTTP coherentes,
// con los filtros de seguridad activos y tokens reales
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EndurecimientoEtapa6Test {

    private static final String CLAVE = "ClaveActual1";
    private static final String ORIGEN_FRONTEND = "http://localhost:5173";
    private static final String ORIGEN_AJENO = "http://sitio-ajeno.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante estudiante;
    private String token;

    @BeforeEach
    void prepararDatos() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Final");
        nuevo.setCarnet("FN" + unico);
        nuevo.setCorreoInstitucional("final." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode(CLAVE));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        estudiante = estudianteRepository.save(nuevo);
        token = tokenService.emitir(estudiante).valor();
    }

    private MvcResult conToken(MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private boolean puedeIniciarSesion(String clave) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"correoInstitucional":"%s","contrasenia":"%s"}
                """.formatted(estudiante.getCorreoInstitucional(), clave))).andReturn();
        return json(login).get("autenticado").asBoolean();
    }

    private String perfil(String contrasenia, String contraseniaActual) {
        return """
                {"nombre":"Estudiante Final","carnet":"%s","correoInstitucional":"%s","contrasenia":%s,"contraseniaActual":%s}
                """.formatted(estudiante.getCarnet(), estudiante.getCorreoInstitucional(),
                contrasenia == null ? "null" : "\"" + contrasenia + "\"",
                contraseniaActual == null ? "null" : "\"" + contraseniaActual + "\"");
    }

    private String actualizarPerfil(String cuerpo) throws Exception {
        MvcResult resultado = conToken(put("/api/auth/perfil").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return resultado.getResponse().getContentAsString();
    }

    // Una respuesta de error nunca debe incluir trazas, clases internas ni detalles del parser
    private void assertErrorJsonSeguro(MvcResult resultado, int estado) throws Exception {
        assertThat(resultado.getResponse().getStatus()).isEqualTo(estado);
        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(json(resultado).get("status").asInt()).isEqualTo(estado);
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("Exception", "org.springframework", "com.saca", "at ", "JSON parse", "Unexpected character");
    }

    // ================= 1. CORS sin comodines

    @Test
    void losControladoresQueTeníanCrossOriginComodinYaNoAceptanOrigenesAjenos() throws Exception {
        for (String ruta : List.of("/api/calendario/semanal/1", "/api/jornadas/1", "/api/perfil-academico",
                "/api/auth/perfil", "/api/disponibilidades")) {
            MvcResult ajeno = mockMvc.perform(options(ruta)
                    .header("Origin", ORIGEN_AJENO)
                    .header("Access-Control-Request-Method", "GET")).andReturn();
            assertThat(ajeno.getResponse().getStatus()).as(ruta).isEqualTo(403);
            assertThat(ajeno.getResponse().getHeader("Access-Control-Allow-Origin")).as(ruta).isNull();

            MvcResult propio = mockMvc.perform(options(ruta)
                    .header("Origin", ORIGEN_FRONTEND)
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "authorization")).andReturn();
            assertThat(propio.getResponse().getStatus()).as(ruta).isEqualTo(200);
            assertThat(propio.getResponse().getHeader("Access-Control-Allow-Origin")).as(ruta).isEqualTo(ORIGEN_FRONTEND);
        }
    }

    @Test
    void lasRespuestasReflejanElOrigenConcretoYNuncaUnComodin() throws Exception {
        MvcResult resultado = conToken(get("/api/calendario/semanal/" + estudiante.getId()).header("Origin", ORIGEN_FRONTEND));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(resultado.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGEN_FRONTEND);
    }

    @Test
    void losOrigenesLocalesConfiguradosSiguenPermitidos() throws Exception {
        for (String origen : List.of("http://localhost:5174", "http://localhost:5175", "http://127.0.0.1:5173")) {
            MvcResult resultado = mockMvc.perform(options("/api/auth/login")
                    .header("Origin", origen)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type")).andReturn();
            assertThat(resultado.getResponse().getStatus()).as(origen).isEqualTo(200);
            assertThat(resultado.getResponse().getHeader("Access-Control-Allow-Origin")).as(origen).isEqualTo(origen);
        }
    }

    // ================= 3. Cambio de contraseña con la contraseña actual

    @Test
    void sinLaContraseniaActualNoSeCambia() throws Exception {
        assertThat(actualizarPerfil(perfil("NuevaClave2", null))).isEqualTo("Debe indicar la contraseña actual para cambiarla");
        assertThat(actualizarPerfil(perfil("NuevaClave2", "  "))).isEqualTo("Debe indicar la contraseña actual para cambiarla");

        assertThat(puedeIniciarSesion(CLAVE)).isTrue();
        assertThat(puedeIniciarSesion("NuevaClave2")).isFalse();
    }

    @Test
    void conUnaContraseniaActualIncorrectaNoSeCambiaNiSeRevelanDetalles() throws Exception {
        String respuesta = actualizarPerfil(perfil("NuevaClave2", "Incorrecta9"));

        assertThat(respuesta).isEqualTo("La contraseña actual es incorrecta");
        assertThat(respuesta).doesNotContain("$2");
        assertThat(puedeIniciarSesion(CLAVE)).isTrue();
        assertThat(estudianteRepository.findById(estudiante.getId()).orElseThrow().getContrasenia())
                .isEqualTo(estudiante.getContrasenia());
    }

    @Test
    void conLaContraseniaActualCorrectaSeGuardaLaNuevaConBcrypt() throws Exception {
        assertThat(actualizarPerfil(perfil("NuevaClave2", CLAVE))).isEqualTo("Perfil actualizado correctamente");

        String hash = estudianteRepository.findById(estudiante.getId()).orElseThrow().getContrasenia();
        assertThat(hash).startsWith("$2").hasSizeGreaterThanOrEqualTo(60).isNotEqualTo("NuevaClave2");
        assertThat(new BCryptPasswordEncoder().matches("NuevaClave2", hash)).isTrue();
        assertThat(puedeIniciarSesion("NuevaClave2")).isTrue();
        assertThat(puedeIniciarSesion(CLAVE)).isFalse();
    }

    @Test
    void sinContraseniaNuevaElPerfilSeActualizaSinPedirLaActual() throws Exception {
        String cuerpo = """
                {"nombre":"Nombre Cambiado","carnet":"%s","correoInstitucional":"%s","contrasenia":""}
                """.formatted(estudiante.getCarnet(), estudiante.getCorreoInstitucional());

        assertThat(actualizarPerfil(cuerpo)).isEqualTo("Perfil actualizado correctamente");
        assertThat(estudianteRepository.findById(estudiante.getId()).orElseThrow().getNombre()).isEqualTo("Nombre Cambiado");
        assertThat(puedeIniciarSesion(CLAVE)).isTrue();
    }

    @Test
    void laValidacionDeFortalezaSeMantieneYSeAplicaAntesDeComprobarLaActual() throws Exception {
        assertThat(actualizarPerfil(perfil("debil", CLAVE)))
                .isEqualTo("La contraseña debe tener al menos 8 caracteres, una mayúscula y un número");
        assertThat(puedeIniciarSesion(CLAVE)).isTrue();
    }

    @Test
    void elPerfilNuncaDevuelveElHash() throws Exception {
        assertThat(conToken(get("/api/auth/perfil")).getResponse().getContentAsString())
                .doesNotContain("$2", "contrasenia");
    }

    // ================= 4. Errores HTTP coherentes y seguros

    @Test
    void unJsonMalFormadoResponde400() throws Exception {
        assertErrorJsonSeguro(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content("{\"correoInstitucional\": ")).andReturn(), 400);
        assertErrorJsonSeguro(conToken(put("/api/auth/perfil")
                .contentType(MediaType.APPLICATION_JSON).content("esto no es json")), 400);
        assertErrorJsonSeguro(conToken(post("/api/jornadas")
                .contentType(MediaType.APPLICATION_JSON).content("{\"horaInicio\":\"25:99\"}")), 400);
    }

    @Test
    void unMetodoNoAdmitidoResponde405ConLosMetodosPermitidos() throws Exception {
        MvcResult resultado = conToken(delete("/api/auth/perfil"));

        assertErrorJsonSeguro(resultado, 405);
        assertThat(resultado.getResponse().getHeader("Allow")).contains("GET", "PUT");
    }

    @Test
    void unTipoDeContenidoNoAdmitidoResponde415() throws Exception {
        assertErrorJsonSeguro(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.TEXT_PLAIN).content("hola")).andReturn(), 415);
    }

    @Test
    void parametrosDeTipoIncorrectoOAusentesResponden400() throws Exception {
        assertErrorJsonSeguro(conToken(get("/api/ciclos/no-es-un-numero").param("estudianteId", String.valueOf(estudiante.getId()))), 400);
        assertErrorJsonSeguro(conToken(get("/api/ciclos")), 400);
        assertErrorJsonSeguro(conToken(get("/api/jornadas/abc")), 400);
    }

    @Test
    void laRutaDeErrorLlamadaDirectamenteResponde404Json() throws Exception {
        assertErrorJsonSeguro(mockMvc.perform(get("/error")).andReturn(), 404);
    }

    // ================= 5. Configuración de seguridad intacta

    @Test
    void loginYRegistroSiguenSiendoPublicosYLasRutasPrivadasExigenToken() throws Exception {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        assertThat(mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Otro Estudiante","carnet":"OT%s","correoInstitucional":"otro.%s@ues.edu.sv","contrasenia":"Segura123"}
                """.formatted(unico, unico))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(puedeIniciarSesion(CLAVE)).isTrue();

        assertThat(mockMvc.perform(get("/api/auth/perfil")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer basura"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void lasRespuestasNoCreanSesionDeServidor() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"correoInstitucional":"%s","contrasenia":"%s"}
                """.formatted(estudiante.getCorreoInstitucional(), CLAVE))).andReturn();
        MvcResult privada = conToken(get("/api/auth/perfil"));

        for (MvcResult resultado : List.of(login, privada)) {
            assertThat(resultado.getResponse().getHeader("Set-Cookie")).isNull();
            assertThat(resultado.getRequest().getSession(false)).isNull();
        }
    }
}
