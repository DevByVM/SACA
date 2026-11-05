package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.Nota;
import com.saca.api.entity.PerfilAcademico;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.NotaRepository;
import com.saca.api.repository.PerfilAcademicoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// El reporte de rendimiento solo expone datos del estudiante del token y del ciclo pedido
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RendimientoAcademicoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private MateriaInscritaRepository materiaInscritaRepository;

    @Autowired
    private ActividadAcademicaRepository actividadRepository;

    @Autowired
    private NotaRepository notaRepository;

    @Autowired
    private PerfilAcademicoRepository perfilAcademicoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante a;
    private CicloAcademico cicloActivoA;
    private CicloAcademico cicloAnteriorA;
    private CicloAcademico cicloB;
    private MateriaInscrita materiaA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        Estudiante b = crearEstudiante("Estudiante Dos");
        cicloActivoA = crearCiclo(a, "Ciclo II-2026", EstadoCiclo.ACTIVO);
        cicloAnteriorA = crearCiclo(a, "Ciclo I-2026", EstadoCiclo.FINALIZADO);
        cicloB = crearCiclo(b, "Ciclo de B", EstadoCiclo.ACTIVO);

        materiaA = crearMateria(cicloActivoA, "PRN315");
        MateriaInscrita materiaAnteriorA = crearMateria(cicloAnteriorA, "MAT115");
        MateriaInscrita materiaB = crearMateria(cicloB, "SECRETA");

        // Datos del caso de promedio ponderado de RendimientoAcademicoCalculoTest y una evaluación todavía sin nota
        crearActividad(materiaA, "Parcial 1", 30, 10.0);
        crearActividad(materiaA, "Laboratorio 1", 10, 5.0);
        crearActividad(materiaA, "Parcial 2", 30, null);
        crearActividad(materiaAnteriorA, "Examen final anterior", 100, 8.0);
        crearActividad(materiaB, "Actividad privada de B", 50, 9.0);

        PerfilAcademico perfil = new PerfilAcademico();
        perfil.setEstudiante(a);
        perfil.setCarrera("Ingeniería de Sistemas");
        perfil.setNotaMinimaAprobacion(new BigDecimal("6.00"));
        perfilAcademicoRepository.save(perfil);

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("RD" + unico);
        nuevo.setCorreoInstitucional("rend." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, String nombre, EstadoCiclo estado) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre(nombre);
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 2, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 12, 15));
        nuevo.setEstado(estado);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String codigo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre("Materia " + codigo);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT01");
        return materiaInscritaRepository.save(nueva);
    }

    private void crearActividad(MateriaInscrita materia, String nombre, double peso, Double nota) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(LocalDateTime.of(2026, 9, 1, 8, 0));
        nueva.setFechaEntrega(LocalDateTime.of(2026, 9, 10, 8, 0));
        nueva.setPorcentajeEvaluacion(peso);
        nueva.setTiempoEstimadoHoras(4);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.PARCIAL);
        nueva.setMateriaInscrita(materia);
        if (nota != null) {
            Nota registrada = new Nota();
            registrada.setValorObtenido(nota);
            registrada.setFechaRegistro(LocalDate.of(2026, 9, 11));
            nueva.setNota(notaRepository.save(registrada));
        }
        actividadRepository.save(nueva);
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private static List<String> codigos(JsonNode respuesta) {
        List<String> codigos = new ArrayList<>();
        respuesta.get("materias").forEach(materia -> codigos.add(materia.get("codigo").asText()));
        return codigos;
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/rendimiento")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void sinCicloSeUsaElCicloActivoYNoSeMezclanMateriasDeOtrosCiclos() throws Exception {
        MvcResult resultado = como(tokenA, get("/api/rendimiento"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("cicloAcademicoId").asLong()).isEqualTo(cicloActivoA.getId());
        assertThat(cuerpo.get("cicloAcademicoNombre").asText()).isEqualTo("Ciclo II-2026");
        assertThat(codigos(cuerpo)).containsExactly("PRN315");
    }

    @Test
    void laRespuestaIncluyeLosCalculosYElDetalleDeActividades() throws Exception {
        JsonNode materia = json(como(tokenA, get("/api/rendimiento"))).get("materias").get(0);

        assertThat(materia.get("materiaInscritaId").asLong()).isEqualTo(materiaA.getId());
        assertThat(materia.get("promedioPonderado").decimalValue()).isEqualByComparingTo("8.75");
        assertThat(materia.get("notaAcumulada").decimalValue()).isEqualByComparingTo("3.50");
        assertThat(materia.get("pesoCalificado").decimalValue()).isEqualByComparingTo("40");
        assertThat(materia.get("pesoPendiente").decimalValue()).isEqualByComparingTo("30");
        assertThat(materia.get("pesoSinAsignar").decimalValue()).isEqualByComparingTo("30");
        assertThat(materia.get("planIncompleto").asBoolean()).isTrue();
        assertThat(materia.get("notaMinima").decimalValue()).isEqualByComparingTo("6.00");
        // (600 − 350) / 30 = 8.333… → 8.34
        assertThat(materia.get("notaNecesaria").decimalValue()).isEqualByComparingTo("8.34");
        assertThat(materia.get("situacion").asText()).isEqualTo("RECUPERABLE");

        JsonNode actividades = materia.get("actividades");
        assertThat(actividades).hasSize(3);
        List<String> calificadas = new ArrayList<>();
        List<String> pendientes = new ArrayList<>();
        actividades.forEach(act -> (act.get("calificada").asBoolean() ? calificadas : pendientes).add(act.get("nombre").asText()));
        assertThat(calificadas).containsExactlyInAnyOrder("Parcial 1", "Laboratorio 1");
        assertThat(pendientes).containsExactly("Parcial 2");
        actividades.forEach(act -> {
            assertThat(act.has("porcentajeEvaluacion")).isTrue();
            assertThat(act.has("tipoActividad")).isTrue();
            assertThat(act.has("aporte")).isTrue();
        });
    }

    @Test
    void sePuedeConsultarUnCicloAnteriorPropio() throws Exception {
        JsonNode cuerpo = json(como(tokenA, get("/api/rendimiento").param("cicloId", String.valueOf(cicloAnteriorA.getId()))));

        assertThat(cuerpo.get("cicloAcademicoId").asLong()).isEqualTo(cicloAnteriorA.getId());
        assertThat(codigos(cuerpo)).containsExactly("MAT115");
        assertThat(cuerpo.get("materias").get(0).get("situacion").asText()).isEqualTo("APROBADA");
    }

    @Test
    void unCicloAjenoResponde404SinRevelarSusDatos() throws Exception {
        MvcResult resultado = como(tokenA, get("/api/rendimiento").param("cicloId", String.valueOf(cicloB.getId())));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("SECRETA", "Actividad privada de B", "Ciclo de B");
    }

    @Test
    void unEstudianteNoVeElRendimientoDeOtro() throws Exception {
        JsonNode cuerpo = json(como(tokenB, get("/api/rendimiento")));

        assertThat(cuerpo.get("cicloAcademicoId").asLong()).isEqualTo(cicloB.getId());
        assertThat(codigos(cuerpo)).containsExactly("SECRETA");
        // B no configuró nota mínima: no se estima ni se clasifica
        assertThat(cuerpo.get("notaMinimaAprobacion").isNull()).isTrue();
        assertThat(cuerpo.get("materias").get(0).get("situacion").asText()).isEqualTo("NOTA_MINIMA_NO_CONFIGURADA");
        assertThat(cuerpo.toString()).doesNotContain("PRN315", "Parcial 1");
    }

    // Los cálculos dependen del porcentaje: antes un porcentaje nulo terminaba en un error 500
    @Test
    void unaActividadSinPorcentajeSeRechazaConUnMensajeClaro() throws Exception {
        String cuerpo = """
                {"nombre":"Sin porcentaje","fechaInicio":"%s 08:00","fechaEntrega":"%s 08:00",
                 "porcentajeEvaluacion":null,"tiempoEstimadoHoras":3,"estado":"ACTIVO","tipoActividad":"PARCIAL",
                 "materiaInscritaId":%s}
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), materiaA.getId());

        MvcResult resultado = mockMvc.perform(post("/api/actividades")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(resultado).get("message").asText())
                .isEqualTo("porcentajeEvaluacion: El porcentaje de evaluación es obligatorio");
    }

    @Test
    void sinCicloActivoNiCicloIndicadoLaRespuestaQuedaVacia() throws Exception {
        Estudiante sinCiclos = crearEstudiante("Estudiante Tres");

        JsonNode cuerpo = json(como(tokenService.emitir(sinCiclos).valor(), get("/api/rendimiento")));

        assertThat(cuerpo.get("cicloAcademicoId").isNull()).isTrue();
        assertThat(cuerpo.get("materias")).isEmpty();
    }
}
