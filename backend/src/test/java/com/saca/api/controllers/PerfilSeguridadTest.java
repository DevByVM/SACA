package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.config.TokenConfig;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PerfilAcademico;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PerfilAcademicoRepository;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Con los filtros de seguridad activos y tokens reales: el perfil y el perfil académico
// solo son accesibles con un JWT válido y siempre son los del estudiante del token
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilSeguridadTest {

    private static final String CLAVE_A = "ClaveA123";
    private static final String CLAVE_B = "ClaveB123";
    private static final String ORIGEN_FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private PerfilAcademicoRepository perfilAcademicoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtEncoder codificadorToken;

    @Autowired
    private ObjectMapper objectMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Estudiante a;
    private Estudiante b;

    @BeforeEach
    void crearEstudiantes() {
        a = crearEstudiante("Estudiante Uno", CLAVE_A);
        b = crearEstudiante("Estudiante Dos", CLAVE_B);
        PerfilAcademico perfilB = new PerfilAcademico();
        perfilB.setEstudiante(b);
        perfilB.setCarrera("Carrera de B");
        perfilB.setFacultad("Facultad de B");
        perfilB.setFechaIngreso(LocalDate.of(2022, 2, 1));
        perfilB.setPlanEstudio("Plan B");
        perfilAcademicoRepository.save(perfilB);
    }

    private Estudiante crearEstudiante(String nombre, String clave) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre(nombre);
        estudiante.setCarnet("PF" + unico);
        estudiante.setCorreoInstitucional("perfil." + unico + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(clave));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        return estudianteRepository.save(estudiante);
    }

    private String tokenDe(Estudiante estudiante) {
        return tokenService.emitir(estudiante).valor();
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private String cuerpoPerfil(Estudiante estudiante, String nombre, String contrasenia) {
        return """
                {"nombre":"%s","carnet":"%s","correoInstitucional":"%s","contrasenia":"%s"}
                """.formatted(nombre, estudiante.getCarnet(), estudiante.getCorreoInstitucional(), contrasenia);
    }

    private JsonNode leer(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private boolean puedeIniciarSesion(Estudiante estudiante, String clave) throws Exception {
        String respuesta = mockMvc.perform(json(post("/api/auth/login"), """
                {"correoInstitucional":"%s","contrasenia":"%s"}
                """.formatted(estudiante.getCorreoInstitucional(), clave)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return leer(respuesta).get("autenticado").asBoolean();
    }

    private void assertBSinCambios() throws Exception {
        Estudiante actual = estudianteRepository.findById(b.getId()).orElseThrow();
        assertThat(actual.getNombre()).isEqualTo("Estudiante Dos");
        assertThat(actual.getContrasenia()).isEqualTo(b.getContrasenia());
        PerfilAcademico perfil = perfilAcademicoRepository.findByEstudianteId(b.getId()).orElseThrow();
        assertThat(perfil.getCarrera()).isEqualTo("Carrera de B");
        assertThat(puedeIniciarSesion(b, CLAVE_B)).isTrue();
    }

    // ---- sin token, token inválido, caducado o firmado con otra clave → 401

    @Test
    void sinTokenNingunaOperacionDePerfilEstaPermitida() throws Exception {
        mockMvc.perform(get("/api/auth/perfil")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/api/auth/perfil"), cuerpoPerfil(b, "Cuenta Tomada", "Atacante123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/perfil-academico")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/api/perfil-academico"), "{\"carrera\":\"Modificada\"}"))
                .andExpect(status().isUnauthorized());

        assertBSinCambios();
    }

    @Test
    void lasRutasVulnerablesAnterioresYaNoAceptanPeticionesSinToken() throws Exception {
        mockMvc.perform(get("/api/auth/perfil/correo/" + b.getCorreoInstitucional())).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/api/auth/perfil/" + b.getId()), cuerpoPerfil(b, "Cuenta Tomada", "Atacante123")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/perfil-academico/" + b.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/api/perfil-academico/" + b.getId()), "{\"carrera\":\"Modificada\"}"))
                .andExpect(status().isUnauthorized());

        assertBSinCambios();
    }

    @Test
    void unTokenInvalidoEsRechazado() throws Exception {
        mockMvc.perform(conToken(get("/api/auth/perfil"), "no-es-un-jwt")).andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/api/perfil-academico"), "a.b.c")).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenManipuladoParaSuplantarAOtroEstudianteEsRechazado() throws Exception {
        String[] partes = tokenDe(a).split("\\.");
        String cargaDeB = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"saca-api\",\"sub\":\"" + b.getId() + "\",\"exp\":4102444800,\"iat\":1700000000}").getBytes());

        mockMvc.perform(conToken(get("/api/auth/perfil"), partes[0] + "." + cargaDeB + "." + partes[2]))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenCaducadoEsRechazado() throws Exception {
        Clock haceNueveHoras = Clock.fixed(Instant.now().minus(Duration.ofHours(9)), ZoneId.of("UTC"));
        String caducado = new TokenService(codificadorToken, haceNueveHoras, Duration.ofHours(8)).emitir(a).valor();

        mockMvc.perform(conToken(get("/api/auth/perfil"), caducado)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(conToken(put("/api/auth/perfil"), caducado), cuerpoPerfil(a, "Otro Nombre", "")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenFirmadoConOtraClaveEsRechazado() throws Exception {
        TokenConfig config = new TokenConfig();
        JwtEncoder otraClave = config.codificadorToken(config.claveFirmaToken("otra-clave-distinta-de-la-del-backend-32b", false));
        String ajeno = new TokenService(otraClave, Clock.systemUTC(), Duration.ofHours(8)).emitir(a).valor();

        mockMvc.perform(conToken(get("/api/auth/perfil"), ajeno)).andExpect(status().isUnauthorized());
    }

    // ---- con token válido: siempre los datos del estudiante del token

    @Test
    void elPerfilEsElDelEstudianteDelTokenYSoloExponeLosCamposDeLaPantalla() throws Exception {
        String respuesta = mockMvc.perform(conToken(get("/api/auth/perfil"), tokenDe(a)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode perfil = leer(respuesta);

        assertThat(perfil.get("id").asLong()).isEqualTo(a.getId());
        assertThat(perfil.get("nombre").asText()).isEqualTo("Estudiante Uno");
        assertThat(perfil.get("carnet").asText()).isEqualTo(a.getCarnet());
        assertThat(perfil.get("correoInstitucional").asText()).isEqualTo(a.getCorreoInstitucional());
        Iterator<String> campos = perfil.fieldNames();
        assertThat(campos).toIterable()
                .containsExactlyInAnyOrder("id", "nombre", "carnet", "correoInstitucional");
        assertThat(respuesta).doesNotContain("contrasenia", "cuentaActiva", "intentosFallidos", "bloqueadoHasta", "$2");
    }

    // Con un token válido, las rutas antiguas llegan a Spring MVC, que ya no tiene controlador para ellas
    private void assertRutaEliminada(MockHttpServletRequestBuilder peticion) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion).andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(resultado.getResponse().getStatus())
                .as("estado (excepción resuelta: %s)", resultado.getResolvedException())
                .isEqualTo(404);
        assertThat(leer(cuerpo).get("status").asInt()).isEqualTo(404);
        // "path" repite la URL enviada por el cliente; no debe aparecer ningún dato que solo tenga el servidor
        assertThat(cuerpo).doesNotContain(b.getCarnet(), "Estudiante Dos", "Carrera de B", "Facultad de B");
    }

    @Test
    void conElTokenDeALasRutasAntiguasConElIdOCorreoDeBResponden404SinDatosNiCambios() throws Exception {
        String tokenA = tokenDe(a);

        assertRutaEliminada(conToken(get("/api/auth/perfil/correo/" + b.getCorreoInstitucional()), tokenA));
        assertRutaEliminada(json(conToken(put("/api/auth/perfil/" + b.getId()), tokenA),
                cuerpoPerfil(b, "Cuenta Tomada", "Atacante123")));
        assertRutaEliminada(conToken(get("/api/perfil-academico/" + b.getId()), tokenA));
        assertRutaEliminada(json(conToken(put("/api/perfil-academico/" + b.getId()), tokenA),
                "{\"carrera\":\"Modificada por A\"}"));

        assertBSinCambios();
        assertThat(estudianteRepository.findById(a.getId()).orElseThrow().getNombre()).isEqualTo("Estudiante Uno");
    }

    // Desde la etapa 4, sin token se responde 401 antes de saber si la ruta existe; con token, 404
    @Test
    void unaRutaInexistenteResponde401SinTokenY404ConToken() throws Exception {
        mockMvc.perform(get("/api/ruta-que-no-existe")).andExpect(status().isUnauthorized());

        MvcResult resultado = mockMvc.perform(conToken(get("/api/ruta-que-no-existe"), tokenDe(a))).andReturn();
        assertThat(resultado.getResponse().getStatus())
                .as("estado (excepción resuelta: %s)", resultado.getResolvedException())
                .isEqualTo(404);
    }

    @Test
    void conElTokenDeANoSePuedeTomarElCorreoNiElCarnetDeB() throws Exception {
        String robarCorreo = """
                {"nombre":"Estudiante Uno","carnet":"%s","correoInstitucional":"%s","contrasenia":""}
                """.formatted(a.getCarnet(), b.getCorreoInstitucional());
        String robarCarnet = """
                {"nombre":"Estudiante Uno","carnet":"%s","correoInstitucional":"%s","contrasenia":""}
                """.formatted(b.getCarnet(), a.getCorreoInstitucional());

        assertThat(mockMvc.perform(json(conToken(put("/api/auth/perfil"), tokenDe(a)), robarCorreo))
                .andReturn().getResponse().getContentAsString()).isEqualTo("Correo ya registrado");
        assertThat(mockMvc.perform(json(conToken(put("/api/auth/perfil"), tokenDe(a)), robarCarnet))
                .andReturn().getResponse().getContentAsString()).isEqualTo("Carnet ya registrado");

        assertThat(estudianteRepository.findById(a.getId()).orElseThrow().getCorreoInstitucional())
                .isEqualTo(a.getCorreoInstitucional());
        assertBSinCambios();
    }

    @Test
    void conSuTokenAActualizaSuPropioPerfilYContraseniaSinAfectarAB() throws Exception {
        // Desde la etapa 6 cambiar la contraseña exige la actual
        String conActual = """
                {"nombre":"Nombre Nuevo","carnet":"%s","correoInstitucional":"%s","contrasenia":"NuevaClave123","contraseniaActual":"%s"}
                """.formatted(a.getCarnet(), a.getCorreoInstitucional(), CLAVE_A);
        String respuesta = mockMvc.perform(json(conToken(put("/api/auth/perfil"), tokenDe(a)), conActual))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(respuesta).isEqualTo("Perfil actualizado correctamente");
        assertThat(estudianteRepository.findById(a.getId()).orElseThrow().getNombre()).isEqualTo("Nombre Nuevo");
        assertThat(puedeIniciarSesion(a, "NuevaClave123")).isTrue();
        assertThat(puedeIniciarSesion(a, CLAVE_A)).isFalse();
        assertBSinCambios();
    }

    @Test
    void sinContraseniaEnLaPeticionLaContraseniaActualSeConserva() throws Exception {
        mockMvc.perform(json(conToken(put("/api/auth/perfil"), tokenDe(a)), cuerpoPerfil(a, "Solo Nombre", "")))
                .andExpect(status().isOk());

        assertThat(puedeIniciarSesion(a, CLAVE_A)).isTrue();
    }

    @Test
    void elPerfilAcademicoEsSiempreElDelEstudianteDelToken() throws Exception {
        String tokenA = tokenDe(a);

        // A aún no tiene perfil académico: no recibe el de B
        assertThat(mockMvc.perform(conToken(get("/api/perfil-academico"), tokenA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .doesNotContain("Carrera de B");

        mockMvc.perform(json(conToken(put("/api/perfil-academico"), tokenA), """
                {"carrera":"Carrera de A","facultad":"Facultad de A","fechaIngreso":"2023-03-01","planEstudio":"Plan A"}
                """)).andExpect(status().isOk());

        JsonNode propio = leer(mockMvc.perform(conToken(get("/api/perfil-academico"), tokenA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(propio.get("carrera").asText()).isEqualTo("Carrera de A");

        JsonNode deB = leer(mockMvc.perform(conToken(get("/api/perfil-academico"), tokenDe(b)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(deB.get("carrera").asText()).isEqualTo("Carrera de B");
        assertBSinCambios();
    }

    @Test
    void unTokenValidoDeUnEstudianteQueYaNoExisteDa404() throws Exception {
        String token = tokenService.emitir(new Estudiante(987654321L)).valor();

        mockMvc.perform(conToken(get("/api/auth/perfil"), token)).andExpect(status().isNotFound());
    }

    // ---- login y registro siguen siendo públicos

    @Test
    void loginYRegistroFuncionanSinToken() throws Exception {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        String registro = mockMvc.perform(json(post("/api/auth/registro"), """
                {"nombre":"Nuevo Estudiante","carnet":"NV%s","correoInstitucional":"nuevo.%s@ues.edu.sv","contrasenia":"Segura123"}
                """.formatted(unico, unico)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(registro).isEqualTo("Usuario registrado correctamente");

        String login = mockMvc.perform(json(post("/api/auth/login"), """
                {"correoInstitucional":"nuevo.%s@ues.edu.sv","contrasenia":"Segura123"}
                """.formatted(unico)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(leer(login).get("autenticado").asBoolean()).isTrue();
        assertThat(leer(login).get("token").asText()).isNotBlank();
    }

    @Test
    void desdeLaEtapa4LasDemasRutasYaNoSonAccesiblesSinToken() throws Exception {
        mockMvc.perform(get("/api/disponibilidades")).andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/api/disponibilidades"), tokenDe(a))).andExpect(status().isOk());
    }

    // ---- CORS: el navegador debe poder hacer la consulta previa y leer los 401

    @Test
    void laConsultaPreviaCorsDelFrontendEstaPermitidaSinToken() throws Exception {
        mockMvc.perform(options("/api/auth/perfil")
                        .header("Origin", ORIGEN_FRONTEND)
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_FRONTEND));

        // PerfilAcademicoController ya declaraba @CrossOrigin("*"), por eso aquí la respuesta es "*"
        String permitido = mockMvc.perform(options("/api/perfil-academico")
                        .header("Origin", ORIGEN_FRONTEND)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Access-Control-Allow-Origin");
        assertThat(permitido).isIn(ORIGEN_FRONTEND, "*");
    }

    @Test
    void elRechazo401LlevaCabecerasCorsParaQueElFrontendLoDetecte() throws Exception {
        mockMvc.perform(get("/api/auth/perfil").header("Origin", ORIGEN_FRONTEND))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_FRONTEND));
    }

    @Test
    void unOrigenNoPermitidoEsRechazadoEnLaConsultaPrevia() throws Exception {
        mockMvc.perform(options("/api/auth/perfil")
                        .header("Origin", "http://sitio-ajeno.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void conTokenValidoLaRespuestaTambienLlevaCabecerasCors() throws Exception {
        mockMvc.perform(conToken(get("/api/auth/perfil"), tokenDe(a)).header("Origin", ORIGEN_FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_FRONTEND));
    }
}
