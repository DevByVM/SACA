package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.NotaRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Etapa 5B: con los filtros de seguridad activos y tokens reales, una actividad solo es accesible
// para el estudiante dueño de su materia (actividad → materia → ciclo → estudiante), y las notas
// solo se pueden usar desde la actividad a la que ya pertenecen
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropiedadActividadTest {

    private static final String NOMBRE_ACTIVIDAD_A = "Parcial privado de A";
    private static final double NOTA_A = 7.25;

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
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transaccion;

    private Estudiante a;
    private Estudiante b;
    private CicloAcademico cicloA;
    private CicloAcademico cicloAnteriorA;
    private CicloAcademico cicloB;
    private MateriaInscrita materiaA;
    private MateriaInscrita materiaAnteriorA;
    private MateriaInscrita materiaB;
    private ActividadAcademica actividadA;
    private Nota notaA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        b = crearEstudiante("Estudiante Dos");
        cicloA = crearCiclo(a, EstadoCiclo.ACTIVO);
        cicloAnteriorA = crearCiclo(a, EstadoCiclo.FINALIZADO);
        cicloB = crearCiclo(b, EstadoCiclo.ACTIVO);
        materiaA = crearMateria(cicloA, "MAT-A");
        materiaAnteriorA = crearMateria(cicloAnteriorA, "ANT-A");
        materiaB = crearMateria(cicloB, "MAT-B");

        notaA = new Nota();
        notaA.setValorObtenido(NOTA_A);
        notaA.setFechaRegistro(LocalDate.of(2026, 3, 1));
        notaA = notaRepository.save(notaA);
        actividadA = crearActividad(materiaA, NOMBRE_ACTIVIDAD_A, notaA);

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("AC" + unico);
        nuevo.setCorreoInstitucional("act." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, EstadoCiclo estado) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo " + estado);
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 2, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 6, 30));
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

    private ActividadAcademica crearActividad(MateriaInscrita materia, String nombre, Nota nota) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(LocalDateTime.of(2026, 3, 1, 8, 0));
        nueva.setFechaEntrega(LocalDateTime.of(2026, 3, 10, 8, 0));
        nueva.setPorcentajeEvaluacion(20);
        nueva.setTiempoEstimadoHoras(5);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.PARCIAL);
        nueva.setMateriaInscrita(materia);
        nueva.setNota(nota);
        return actividadRepository.save(nueva);
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return como(token, peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String actividad(String nombre, Long materiaInscritaId, Integer notaId, Double notaObtenida) {
        return """
                {"nombre":"%s","fechaInicio":"2026-04-01 08:00","fechaEntrega":"2026-04-05 08:00",
                 "porcentajeEvaluacion":10,"tiempoEstimadoHoras":3,"estado":"ACTIVO","tipoActividad":"LABORATORIO",
                 "materiaInscritaId":%s,"notaId":%s,"notaObtenida":%s}
                """.formatted(nombre, materiaInscritaId, notaId, notaObtenida);
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private List<Long> ids(MvcResult resultado) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(resultado).forEach(n -> ids.add(n.get("idActividad").asLong()));
        return ids;
    }

    private int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    // La respuesta de error no contiene nada de la actividad ni de la nota de A
    private void assertSinDatosDeA(MvcResult resultado) throws Exception {
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain(NOMBRE_ACTIVIDAD_A, "7.25", "MAT-A", "Materia MAT-A");
    }

    // Comprobado en la base de datos: la actividad de A sigue en su materia y con su nota intacta
    private void assertActividadYNotaDeAIntactas() {
        transaccion.executeWithoutResult(estado -> {
            ActividadAcademica actual = actividadRepository.findById(actividadA.getIdActividad()).orElseThrow();
            assertThat(actual.getNombre()).isEqualTo(NOMBRE_ACTIVIDAD_A);
            assertThat(actual.getMateriaInscrita().getId()).isEqualTo(materiaA.getId());
            assertThat(actual.getNota()).isNotNull();
            assertThat(actual.getNota().getId()).isEqualTo(notaA.getId());
        });
        assertThat(notaRepository.findById(notaA.getId()).orElseThrow().getValorObtenido()).isEqualTo(NOTA_A);
    }

    // ================= Operaciones legítimas de A

    @Test
    void aGestionaSusActividadesYSusNotas() throws Exception {
        MvcResult consulta = como(tokenA, get("/api/actividades/" + actividadA.getIdActividad()));
        assertThat(estado(consulta)).isEqualTo(200);
        assertThat(json(consulta).get("notaObtenida").asDouble()).isEqualTo(NOTA_A);

        MvcResult creada = como(tokenA, post("/api/actividades"), actividad("Laboratorio 1", materiaA.getId(), null, 8.5));
        assertThat(estado(creada)).isEqualTo(201);
        long nuevaId = json(creada).get("idActividad").asLong();
        int notaNueva = json(creada).get("notaId").asInt();
        assertThat(json(creada).get("materiaInscritaId").asLong()).isEqualTo(materiaA.getId());
        assertThat(json(creada).get("notaObtenida").asDouble()).isEqualTo(8.5);

        // Cambiar el valor reutiliza la misma nota de la actividad
        MvcResult nuevoValor = como(tokenA, put("/api/actividades/" + nuevaId), actividad("Laboratorio 1", materiaA.getId(), null, 9.0));
        assertThat(estado(nuevoValor)).isEqualTo(200);
        assertThat(json(nuevoValor).get("notaId").asInt()).isEqualTo(notaNueva);
        assertThat(json(nuevoValor).get("notaObtenida").asDouble()).isEqualTo(9.0);

        // Enviar el notaId que la actividad ya tiene la conserva
        MvcResult conSuNota = como(tokenA, put("/api/actividades/" + nuevaId), actividad("Laboratorio 1b", materiaA.getId(), notaNueva, null));
        assertThat(estado(conSuNota)).isEqualTo(200);
        assertThat(json(conSuNota).get("notaId").asInt()).isEqualTo(notaNueva);
        assertThat(json(conSuNota).get("nombre").asText()).isEqualTo("Laboratorio 1b");

        // Mover la actividad a otra materia propia está permitido
        MvcResult otraMateria = como(tokenA, put("/api/actividades/" + nuevaId), actividad("Laboratorio 1b", materiaAnteriorA.getId(), null, null));
        assertThat(estado(otraMateria)).isEqualTo(200);
        assertThat(json(otraMateria).get("materiaInscritaId").asLong()).isEqualTo(materiaAnteriorA.getId());
        assertThat(json(otraMateria).get("notaId").isNull()).isTrue();

        assertThat(estado(como(tokenA, delete("/api/actividades/" + nuevaId)))).isEqualTo(204);
        assertThat(actividadRepository.findById(nuevaId)).isEmpty();
        assertActividadYNotaDeAIntactas();
    }

    @Test
    void aVeSusActividadesDelCicloActivo() throws Exception {
        MvcResult activas = como(tokenA, get("/api/actividades/estudiante/" + a.getId() + "/activo"));
        assertThat(estado(activas)).isEqualTo(200);
        assertThat(ids(activas)).contains(actividadA.getIdActividad());
    }

    @Test
    void laConsultaPorCicloDevuelveLasActividadesDeEseCiclo() throws Exception {
        ActividadAcademica anterior = crearActividad(materiaAnteriorA, "Proyecto del ciclo anterior", null);

        MvcResult delActivo = como(tokenA, get("/api/actividades")
                .param("estudianteId", String.valueOf(a.getId())).param("cicloId", String.valueOf(cicloA.getId())));
        MvcResult delAnterior = como(tokenA, get("/api/actividades")
                .param("estudianteId", String.valueOf(a.getId())).param("cicloId", String.valueOf(cicloAnteriorA.getId())));

        assertThat(estado(delActivo)).isEqualTo(200);
        assertThat(ids(delActivo)).containsExactly(actividadA.getIdActividad());
        assertThat(ids(delAnterior)).containsExactly(anterior.getIdActividad());
    }

    @Test
    void lasValidacionesExistentesSeConservan() throws Exception {
        String fechasInvertidas = """
                {"nombre":"Mal","fechaInicio":"2026-04-05 08:00","fechaEntrega":"2026-04-01 08:00",
                 "porcentajeEvaluacion":10,"tiempoEstimadoHoras":3,"tipoActividad":"LABORATORIO","materiaInscritaId":%d}
                """.formatted(materiaA.getId());
        MvcResult resultado = como(tokenA, post("/api/actividades"), fechasInvertidas);
        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(json(resultado).get("message").asText()).isEqualTo("La fecha de inicio no puede ser posterior a la fecha de entrega");
    }

    @Test
    void unaActividadInexistenteResponde404YNo500() throws Exception {
        assertThat(estado(como(tokenA, get("/api/actividades/987654321")))).isEqualTo(404);
        assertThat(estado(como(tokenA, delete("/api/actividades/987654321")))).isEqualTo(404);
        assertThat(estado(como(tokenA, put("/api/actividades/987654321"), actividad("X", materiaA.getId(), null, null)))).isEqualTo(404);
    }

    // ================= B contra los datos de A

    @Test
    void bNoPuedeConsultarModificarNiEliminarLaActividadDeA() throws Exception {
        MvcResult consulta = como(tokenB, get("/api/actividades/" + actividadA.getIdActividad()));
        assertThat(estado(consulta)).isEqualTo(404);
        assertSinDatosDeA(consulta);

        MvcResult conSuMateria = como(tokenB, put("/api/actividades/" + actividadA.getIdActividad()), actividad("Tomada", materiaB.getId(), null, 0.0));
        assertThat(estado(conSuMateria)).isEqualTo(404);
        assertSinDatosDeA(conSuMateria);
        assertThat(estado(como(tokenB, put("/api/actividades/" + actividadA.getIdActividad()),
                actividad("Tomada", materiaA.getId(), notaA.getId(), null)))).isEqualTo(404);

        MvcResult borrado = como(tokenB, delete("/api/actividades/" + actividadA.getIdActividad()));
        assertThat(estado(borrado)).isEqualTo(404);

        assertActividadYNotaDeAIntactas();
    }

    @Test
    void bNoVeLasActividadesDeAEnNingunListado() throws Exception {
        assertThat(estado(como(tokenB, get("/api/actividades/estudiante/" + a.getId() + "/activo")))).isEqualTo(404);
        assertThat(estado(como(tokenB, get("/api/actividades")
                .param("estudianteId", String.valueOf(a.getId())).param("cicloId", String.valueOf(cicloA.getId()))))).isEqualTo(404);

        // Con su propio id pero el ciclo de A, la consulta filtra por B y no devuelve nada
        MvcResult conCicloDeA = como(tokenB, get("/api/actividades")
                .param("estudianteId", String.valueOf(b.getId())).param("cicloId", String.valueOf(cicloA.getId())));
        assertThat(estado(conCicloDeA)).isEqualTo(200);
        assertThat(ids(conCicloDeA)).isEmpty();

        MvcResult propias = como(tokenB, get("/api/actividades/estudiante/" + b.getId() + "/activo"));
        assertThat(estado(propias)).isEqualTo(200);
        assertThat(ids(propias)).doesNotContain(actividadA.getIdActividad());
        assertSinDatosDeA(propias);
    }

    @Test
    void bNoPuedeCrearActividadesEnUnaMateriaDeA() throws Exception {
        long antes = actividadRepository.count();

        MvcResult resultado = como(tokenB, post("/api/actividades"), actividad("Intrusa", materiaA.getId(), null, 10.0));

        assertThat(estado(resultado)).isEqualTo(404);
        assertThat(json(resultado).get("message").asText()).isEqualTo("No se encontro la materia inscrita solicitada");
        assertSinDatosDeA(resultado);
        assertThat(actividadRepository.count()).isEqualTo(antes);
        assertThat(estado(como(tokenB, post("/api/actividades"), actividad("Intrusa", 987654L, null, null)))).isEqualTo(404);
    }

    @Test
    void bNoPuedeMoverSuActividadAUnaMateriaDeA() throws Exception {
        ActividadAcademica actividadB = crearActividad(materiaB, "Actividad de B", null);

        MvcResult resultado = como(tokenB, put("/api/actividades/" + actividadB.getIdActividad()), actividad("Actividad de B", materiaA.getId(), null, null));

        assertThat(estado(resultado)).isEqualTo(404);
        transaccion.executeWithoutResult(estado -> assertThat(
                actividadRepository.findById(actividadB.getIdActividad()).orElseThrow().getMateriaInscrita().getId())
                .isEqualTo(materiaB.getId()));
    }

    @Test
    void bNoPuedeAsociarNiModificarLaNotaDeA() throws Exception {
        MvcResult alCrear = como(tokenB, post("/api/actividades"), actividad("Con nota ajena", materiaB.getId(), notaA.getId(), null));
        assertThat(estado(alCrear)).isEqualTo(404);
        assertThat(json(alCrear).get("message").asText()).isEqualTo("No se encontró la nota solicitada");
        assertSinDatosDeA(alCrear);

        ActividadAcademica actividadB = crearActividad(materiaB, "Actividad de B", null);
        MvcResult alActualizar = como(tokenB, put("/api/actividades/" + actividadB.getIdActividad()),
                actividad("Actividad de B", materiaB.getId(), notaA.getId(), null));
        assertThat(estado(alActualizar)).isEqualTo(404);
        assertSinDatosDeA(alActualizar);

        // notaObtenida junto al notaId ajeno solo afecta a la nota propia de B, nunca a la de A
        MvcResult conValor = como(tokenB, put("/api/actividades/" + actividadB.getIdActividad()),
                actividad("Actividad de B", materiaB.getId(), notaA.getId(), 1.0));
        assertThat(estado(conValor)).isEqualTo(200);
        assertThat(json(conValor).get("notaId").asInt()).isNotEqualTo(notaA.getId());

        assertActividadYNotaDeAIntactas();
    }

    @Test
    void unaNotaSinActividadNoSePuedeAsociar() throws Exception {
        Nota huerfana = new Nota();
        huerfana.setValorObtenido(4.5);
        huerfana.setFechaRegistro(LocalDate.of(2026, 1, 1));
        huerfana = notaRepository.save(huerfana);
        ActividadAcademica actividadB = crearActividad(materiaB, "Actividad de B", null);

        MvcResult alCrear = como(tokenB, post("/api/actividades"), actividad("Huerfana", materiaB.getId(), huerfana.getId(), null));
        MvcResult alActualizar = como(tokenB, put("/api/actividades/" + actividadB.getIdActividad()),
                actividad("Actividad de B", materiaB.getId(), huerfana.getId(), null));

        assertThat(estado(alCrear)).isEqualTo(404);
        assertThat(estado(alActualizar)).isEqualTo(404);
        assertThat(alCrear.getResponse().getContentAsString()).doesNotContain("4.5");
        transaccion.executeWithoutResult(estado -> assertThat(
                actividadRepository.findById(actividadB.getIdActividad()).orElseThrow().getNota()).isNull());
    }

    @Test
    void aTampocoPuedeEnlazarLaNotaDeOtraDeSusActividades() throws Exception {
        ActividadAcademica otraDeA = crearActividad(materiaA, "Otra actividad de A", null);

        MvcResult resultado = como(tokenA, put("/api/actividades/" + otraDeA.getIdActividad()),
                actividad("Otra actividad de A", materiaA.getId(), notaA.getId(), null));

        assertThat(estado(resultado)).isEqualTo(404);
        assertActividadYNotaDeAIntactas();
    }
}
