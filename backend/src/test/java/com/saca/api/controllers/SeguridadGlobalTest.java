package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.config.ManejadorErroresSeguridad;
import com.saca.api.config.TokenConfig;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Etapa 4: toda ruta exige un JWT válido salvo login, registro, la consulta previa CORS y /error.
// Las rutas se obtienen de Spring MVC, de modo que una ruta nueva queda cubierta sin tocar la prueba.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadGlobalTest {

    private static final String ORIGEN_FRONTEND = "http://localhost:5173";
    private static final String ID_INEXISTENTE = "999999";

    // Únicas rutas que pueden responder sin token, con su método
    private static final Set<String> RUTAS_PUBLICAS = Set.of("POST /api/auth/login", "POST /api/auth/registro");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeos;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtEncoder codificadorToken;

    @Autowired
    private ManejadorErroresSeguridad manejadorErrores;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante estudiante;

    private record Ruta(String metodo, String patron, String controlador) {
        String url() {
            return patron.replaceAll("\\{[^}]+}", ID_INEXISTENTE);
        }

        @Override
        public String toString() {
            return metodo + " " + patron;
        }
    }

    @BeforeEach
    void crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Global");
        nuevo.setCarnet("GL" + unico);
        nuevo.setCorreoInstitucional("global." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        estudiante = estudianteRepository.save(nuevo);
    }

    private List<Ruta> rutasDeLaAplicacion() {
        List<Ruta> rutas = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeos.getHandlerMethods().entrySet()) {
            String controlador = entrada.getValue().getBeanType().getSimpleName();
            if (!entrada.getValue().getBeanType().getPackageName().startsWith("com.saca.api")
                    || entrada.getValue().getBeanType().getSimpleName().equals("ControladorErrores")) {
                continue; // la página /error es pública por diseño y se comprueba aparte
            }
            Set<RequestMethod> metodos = entrada.getKey().getMethodsCondition().getMethods();
            for (String patron : entrada.getKey().getPatternValues()) {
                if (metodos.isEmpty()) {
                    rutas.add(new Ruta("GET", patron, controlador));
                }
                metodos.forEach(m -> rutas.add(new Ruta(m.name(), patron, controlador)));
            }
        }
        return rutas;
    }

    private MockHttpServletRequestBuilder peticion(Ruta ruta) {
        MockHttpServletRequestBuilder peticion = request(HttpMethod.valueOf(ruta.metodo()), ruta.url());
        if (ruta.metodo().equals("POST") || ruta.metodo().equals("PUT")) {
            peticion.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return peticion;
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    // Una ruta GET por controlador, para los casos de token inválido, caducado o válido
    private Map<String, Ruta> unaRutaGetPorControlador() {
        Map<String, Ruta> porControlador = new LinkedHashMap<>();
        rutasDeLaAplicacion().stream()
                .filter(r -> r.metodo().equals("GET"))
                .forEach(r -> porControlador.putIfAbsent(r.controlador(), r));
        return porControlador;
    }

    private void assertRespuesta401Json(MvcResult resultado) throws Exception {
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(resultado.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        JsonNode cuerpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        assertThat(cuerpo.get("status").asInt()).isEqualTo(401);
        assertThat(cuerpo.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(cuerpo.get("message").asText()).isEqualTo("Se requiere iniciar sesión con un token válido");
        assertThat(cuerpo.get("path").asText()).isEqualTo(resultado.getRequest().getRequestURI());
    }

    // ---- inventario: ninguna ruta queda pública por accidente

    @Test
    void elInventarioIncluyeTodosLosControladoresDeLaAplicacion() {
        assertThat(rutasDeLaAplicacion().stream().map(Ruta::controlador).distinct())
                .contains("AuthController", "ActividadAcademicaController", "CalendarioController",
                        "CicloAcademicoController", "DisponibilidadSemanalController",
                        "HorarioClaseController", "JornadaLaboralController", "MateriaInscritaController",
                        "NotificacionController", "PerfilAcademicoController");
        // 42 rutas tras retirar GreetingController en la etapa 6; una ruta nueva entra sola en las comprobaciones siguientes
        assertThat(rutasDeLaAplicacion()).hasSizeGreaterThanOrEqualTo(42);
    }

    @Test
    void sinTokenTodasLasRutasResponden401JsonSalvoLoginYRegistro() throws Exception {
        List<String> publicasEncontradas = new ArrayList<>();
        List<String> noProtegidas = new ArrayList<>();

        for (Ruta ruta : rutasDeLaAplicacion()) {
            if (RUTAS_PUBLICAS.contains(ruta.toString())) {
                publicasEncontradas.add(ruta.toString());
                continue;
            }
            MvcResult resultado = mockMvc.perform(peticion(ruta)).andReturn();
            if (resultado.getResponse().getStatus() != 401) {
                noProtegidas.add(ruta + " → " + resultado.getResponse().getStatus());
                continue;
            }
            assertRespuesta401Json(resultado);
        }

        assertThat(noProtegidas).as("rutas accesibles sin token").isEmpty();
        assertThat(publicasEncontradas).containsExactlyInAnyOrderElementsOf(RUTAS_PUBLICAS);
    }

    @Test
    void conTokenValidoNingunaRutaGetEsRechazadaPorSeguridad() throws Exception {
        String token = tokenService.emitir(estudiante).valor();
        List<String> rechazadas = new ArrayList<>();

        for (Ruta ruta : rutasDeLaAplicacion()) {
            if (!ruta.metodo().equals("GET")) {
                continue; // POST, PUT y DELETE con token se cubren en las pruebas de cada módulo
            }
            int estado = mockMvc.perform(conToken(peticion(ruta), token)).andReturn().getResponse().getStatus();
            if (estado == 401 || estado == 403) {
                rechazadas.add(ruta + " → " + estado);
            }
        }

        assertThat(rechazadas).as("rutas GET rechazadas con un token válido").isEmpty();
    }

    @Test
    void unaRutaPublicaConOtroMetodoNoEsPublica() throws Exception {
        assertRespuesta401Json(mockMvc.perform(get("/api/auth/login")).andReturn());
        assertRespuesta401Json(mockMvc.perform(get("/api/auth/registro")).andReturn());
    }

    // GreetingController se retiró en la etapa 6: sin token 401 y con token 404, como cualquier ruta inexistente
    @Test
    void lasRutasDeEjemploGreetingYaNoExisten() throws Exception {
        String token = tokenService.emitir(estudiante).valor();
        for (String ruta : List.of("/hello", "/hello/json")) {
            assertRespuesta401Json(mockMvc.perform(get(ruta)).andReturn());
            assertThat(mockMvc.perform(conToken(get(ruta), token)).andReturn().getResponse().getStatus()).as(ruta).isEqualTo(404);
        }
    }

    // ---- tokens no válidos en una ruta representativa de cada controlador

    @Test
    void unTokenBasuraEsRechazadoEnTodosLosControladores() throws Exception {
        for (Ruta ruta : unaRutaGetPorControlador().values()) {
            assertRespuesta401Json(mockMvc.perform(conToken(peticion(ruta), "no-es-un-jwt")).andReturn());
        }
    }

    @Test
    void unTokenCaducadoEsRechazadoEnTodosLosControladores() throws Exception {
        Clock haceNueveHoras = Clock.fixed(Instant.now().minus(Duration.ofHours(9)), ZoneId.of("UTC"));
        String caducado = new TokenService(codificadorToken, haceNueveHoras, Duration.ofHours(8)).emitir(estudiante).valor();

        for (Ruta ruta : unaRutaGetPorControlador().values()) {
            assertRespuesta401Json(mockMvc.perform(conToken(peticion(ruta), caducado)).andReturn());
        }
    }

    @Test
    void unTokenFirmadoConOtraClaveEsRechazadoEnTodosLosControladores() throws Exception {
        TokenConfig config = new TokenConfig();
        JwtEncoder otraClave = config.codificadorToken(config.claveFirmaToken("otra-clave-distinta-de-la-del-backend-32b", false));
        String ajeno = new TokenService(otraClave, Clock.systemUTC(), Duration.ofHours(8)).emitir(estudiante).valor();

        for (Ruta ruta : unaRutaGetPorControlador().values()) {
            assertRespuesta401Json(mockMvc.perform(conToken(peticion(ruta), ajeno)).andReturn());
        }
    }

    @Test
    void unTokenValidoEsAceptadoEnTodosLosControladores() throws Exception {
        String token = tokenService.emitir(estudiante).valor();
        Map<String, Ruta> rutas = unaRutaGetPorControlador();
        // 10 controladores con rutas GET tras retirar GreetingController en la etapa 6
        assertThat(rutas).hasSizeGreaterThanOrEqualTo(10);

        for (Ruta ruta : rutas.values()) {
            assertThat(mockMvc.perform(conToken(peticion(ruta), token)).andReturn().getResponse().getStatus())
                    .as(ruta.toString()).isNotIn(401, 403);
        }
    }

    // ---- rutas públicas

    @Test
    void loginYRegistroFuncionanSinToken() throws Exception {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"Publico Nuevo","carnet":"PN%s","correoInstitucional":"publico.%s@ues.edu.sv","contrasenia":"Segura123"}
                """.formatted(unico, unico)))
                .andExpect(status().isOk());

        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"correoInstitucional":"publico.%s@ues.edu.sv","contrasenia":"Segura123"}
                """.formatted(unico)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(login).get("token").asText()).isNotBlank();
    }

    // Spring Security valida cualquier Bearer recibido antes de autorizar; el frontend nunca lo envía al login
    @Test
    void unBearerInvalidoEnUnaRutaPublicaSeRechazaCon401() throws Exception {
        assertRespuesta401Json(mockMvc.perform(post("/api/auth/login")
                .header("Authorization", "Bearer no-es-un-jwt")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correoInstitucional\":\"x@ues.edu.sv\",\"contrasenia\":\"x\"}")).andReturn());
    }

    // Llamada directamente, sin un error real detrás, la página de error responde 404 JSON (etapa 6)
    @Test
    void laRutaDeErrorNoExigeTokenYRespondeJson() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/error")).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(resultado.getResponse().getContentAsString()).get("status").asInt()).isEqualTo(404);
    }

    @Test
    void conTokenUnaRutaInexistenteDevuelve404JsonYNo401() throws Exception {
        MvcResult resultado = mockMvc.perform(conToken(get("/api/no-existe"), tokenService.emitir(estudiante).valor()))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(resultado.getResponse().getContentAsString()).get("status").asInt()).isEqualTo(404);
    }

    // ---- CORS y consulta previa

    @Test
    void laConsultaPreviaCorsEstaPermitidaSinTokenEnRutasProtegidas() throws Exception {
        for (String ruta : List.of("/api/ciclos", "/api/disponibilidades", "/api/jornadas/estudiante/1",
                "/api/actividades/estudiante/1/activo", "/api/notificaciones/estudiante/1/proximas")) {
            String permitido = mockMvc.perform(options(ruta)
                            .header("Origin", ORIGEN_FRONTEND)
                            .header("Access-Control-Request-Method", "GET")
                            .header("Access-Control-Request-Headers", "authorization"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeader("Access-Control-Allow-Origin");
            assertThat(permitido).as(ruta).isIn(ORIGEN_FRONTEND, "*");
        }
    }

    @Test
    void unOptionsQueNoEsConsultaPreviaCorsExigeToken() throws Exception {
        assertRespuesta401Json(mockMvc.perform(options("/api/ciclos")).andReturn());
    }

    @Test
    void los401LlevanCabecerasCorsParaQueElNavegadorLosLea() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/ciclos").header("Origin", ORIGEN_FRONTEND)).andReturn();
        assertRespuesta401Json(resultado);
        assertThat(resultado.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo(ORIGEN_FRONTEND);
    }

    @Test
    void unOrigenNoPermitidoSigueRechazadoEnLaConsultaPrevia() throws Exception {
        mockMvc.perform(options("/api/ciclos")
                        .header("Origin", "http://sitio-ajeno.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ---- 403: hoy ninguna regla deniega a un usuario autenticado; se comprueba el formato del manejador

    @Test
    void unAccesoDenegadoAUnUsuarioAutenticadoResponde403Json() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/ciclos");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        manejadorErrores.handle(peticion, respuesta, new AccessDeniedException("denegado"));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(respuesta.getHeader("WWW-Authenticate")).startsWith("Bearer");
        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString());
        assertThat(cuerpo.get("status").asInt()).isEqualTo(403);
        assertThat(cuerpo.get("message").asText()).isEqualTo("No tiene permiso para realizar esta acción");
        assertThat(cuerpo.get("path").asText()).isEqualTo("/api/ciclos");
    }

    @Test
    void elMensajeDel401NoRevelaPorQueSeRechazoElToken() throws Exception {
        String basura = mockMvc.perform(conToken(get("/api/ciclos"), "no-es-un-jwt")).andReturn().getResponse().getContentAsString();
        String sinToken = mockMvc.perform(get("/api/ciclos")).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(basura).get("message")).isEqualTo(objectMapper.readTree(sinToken).get("message"));
        assertThat(basura).doesNotContainIgnoringCase("malformed").doesNotContainIgnoringCase("expired").doesNotContain("Jwt");
    }

    @Test
    void conTokenValidoLasRespuestasTambienLlevanCabecerasCors() throws Exception {
        mockMvc.perform(conToken(get("/api/ciclos").param("estudianteId", String.valueOf(estudiante.getId())),
                        tokenService.emitir(estudiante).valor()).header("Origin", ORIGEN_FRONTEND))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_FRONTEND));
    }
}
