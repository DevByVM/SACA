package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.ModalidadClase;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.MateriaInscritaRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Etapa 5C: con los filtros de seguridad activos y tokens reales, ciclos, materias y horarios solo son
// accesibles para su propietario, y sus relaciones no permiten vincular registros de otro estudiante
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropiedadCicloMateriaHorarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloRepository;

    @Autowired
    private MateriaInscritaRepository materiaRepository;

    @Autowired
    private HorarioClaseRepository horarioRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transaccion;

    private Estudiante a;
    private Estudiante b;
    private CicloAcademico cicloA;
    private CicloAcademico cicloB;
    private MateriaInscrita materiaA;
    private MateriaInscrita materiaB;
    private HorarioClase horarioA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        b = crearEstudiante("Estudiante Dos");
        cicloA = crearCiclo(a, "Ciclo privado de A");
        cicloB = crearCiclo(b, "Ciclo de B");
        materiaA = crearMateria(cicloA, "Materia privada de A", "PRIV-A");
        materiaB = crearMateria(cicloB, "Materia de B", "MAT-B");
        horarioA = crearHorario(materiaA, "Docente de A");
        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("CM" + unico);
        nuevo.setCorreoInstitucional("cmh." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, String nombre) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre(nombre);
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 2, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 6, 30));
        nuevo.setEstado(EstadoCiclo.ACTIVO);
        return cicloRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String nombre, String codigo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre(nombre);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT01");
        return materiaRepository.save(nueva);
    }

    private HorarioClase crearHorario(MateriaInscrita materia, String docente) {
        HorarioClase nuevo = new HorarioClase();
        nuevo.setMateriaInscrita(materia);
        nuevo.setDiaSemana(DiaSemana.LUNES);
        nuevo.setHoraInicio(LocalTime.parse("08:00"));
        nuevo.setHoraFin(LocalTime.parse("10:00"));
        nuevo.setModalidad(ModalidadClase.PRESENCIAL);
        nuevo.setDocenteTutor(docente);
        return horarioRepository.save(nuevo);
    }

    // ---- utilidades de petición

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return como(token, peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static MockHttpServletRequestBuilder conEstudiante(MockHttpServletRequestBuilder peticion, Estudiante estudiante) {
        return peticion.param("estudianteId", String.valueOf(estudiante.getId()));
    }

    private static String ciclo(Long estudianteId, String nombre, String inicio, String fin) {
        return """
                {"estudianteId":%s,"nombre":"%s","anio":2026,"fechaInicio":"%s","fechaFin":"%s","estado":"PLANIFICADO"}
                """.formatted(estudianteId, nombre, inicio, fin);
    }

    private static String materia(Long cicloAcademicoId, String nombre, String codigo) {
        return """
                {"cicloAcademicoId":%s,"nombre":"%s","codigo":"%s","grupoTeorico":"GT02"}
                """.formatted(cicloAcademicoId, nombre, codigo);
    }

    private static String horario(Long materiaInscritaId, String dia, String inicio, String fin) {
        return """
                {"materiaInscritaId":%s,"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","modalidad":"VIRTUAL","docenteTutor":"Docente nuevo"}
                """.formatted(materiaInscritaId, dia, inicio, fin);
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private List<Long> ids(MvcResult resultado) throws Exception {
        List<Long> ids = new ArrayList<>();
        json(resultado).forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private static int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    private void assertSinDatosDeA(MvcResult resultado) throws Exception {
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("Ciclo privado de A", "Materia privada de A", "PRIV-A", "Docente de A");
    }

    // Comprobado en la base de datos: ciclo, materia y horario de A siguen como estaban
    private void assertDatosDeAIntactos() {
        transaccion.executeWithoutResult(estado -> {
            CicloAcademico ciclo = cicloRepository.findById(cicloA.getId()).orElseThrow();
            assertThat(ciclo.getNombre()).isEqualTo("Ciclo privado de A");
            assertThat(ciclo.getEstudiante().getId()).isEqualTo(a.getId());

            MateriaInscrita materia = materiaRepository.findById(materiaA.getId()).orElseThrow();
            assertThat(materia.getNombre()).isEqualTo("Materia privada de A");
            assertThat(materia.getCicloAcademico().getId()).isEqualTo(cicloA.getId());

            HorarioClase horario = horarioRepository.findById(horarioA.getId()).orElseThrow();
            assertThat(horario.getDocenteTutor()).isEqualTo("Docente de A");
            assertThat(horario.getHoraInicio()).isEqualTo(LocalTime.parse("08:00"));
            assertThat(horario.getMateriaInscrita().getId()).isEqualTo(materiaA.getId());
        });
        assertThat(materiaRepository.findByCicloAcademicoIdOrderByNombreAsc(cicloA.getId())).hasSize(1);
        assertThat(horarioRepository.findByMateriaInscritaIdOrderByDiaSemanaAscHoraInicioAsc(materiaA.getId())).hasSize(1);
    }

    // ================= Operaciones legítimas de A

    @Test
    void aGestionaSusCiclos() throws Exception {
        assertThat(ids(como(tokenA, conEstudiante(get("/api/ciclos"), a)))).containsExactly(cicloA.getId());
        assertThat(ids(como(tokenA, conEstudiante(get("/api/ciclos"), a).param("estado", "ACTIVO")))).containsExactly(cicloA.getId());
        assertThat(json(como(tokenA, conEstudiante(get("/api/ciclos/" + cicloA.getId()), a))).get("nombre").asText()).isEqualTo("Ciclo privado de A");

        MvcResult creado = como(tokenA, post("/api/ciclos"), ciclo(a.getId(), "Ciclo nuevo", "2026-08-01", "2026-12-15"));
        assertThat(estado(creado)).isEqualTo(201);
        long nuevoId = json(creado).get("id").asLong();
        assertThat(json(creado).get("estudianteId").asLong()).isEqualTo(a.getId());

        MvcResult editado = como(tokenA, conEstudiante(put("/api/ciclos/" + nuevoId), a),
                """
                {"nombre":"Ciclo editado","anio":2026,"fechaInicio":"2026-08-01","fechaFin":"2026-12-20","estado":"ACTIVO"}
                """);
        assertThat(estado(editado)).isEqualTo(200);
        assertThat(json(editado).get("nombre").asText()).isEqualTo("Ciclo editado");

        assertThat(estado(como(tokenA, conEstudiante(delete("/api/ciclos/" + nuevoId), a)))).isEqualTo(204);
        assertThat(cicloRepository.findById(nuevoId)).isEmpty();
    }

    @Test
    void aGestionaSusMaterias() throws Exception {
        assertThat(ids(como(tokenA, conEstudiante(get("/api/materias"), a)))).containsExactly(materiaA.getId());
        assertThat(ids(como(tokenA, conEstudiante(get("/api/materias"), a).param("cicloAcademicoId", String.valueOf(cicloA.getId())))))
                .containsExactly(materiaA.getId());
        assertThat(json(como(tokenA, conEstudiante(get("/api/materias/" + materiaA.getId()), a))).get("codigo").asText()).isEqualTo("PRIV-A");

        MvcResult creada = como(tokenA, conEstudiante(post("/api/materias"), a), materia(cicloA.getId(), "Materia nueva", "NUEVA-1"));
        assertThat(estado(creada)).isEqualTo(201);
        long nuevaId = json(creada).get("id").asLong();
        assertThat(json(creada).get("cicloAcademicoId").asLong()).isEqualTo(cicloA.getId());

        MvcResult editada = como(tokenA, conEstudiante(put("/api/materias/" + nuevaId), a),
                """
                {"nombre":"Materia editada","codigo":"NUEVA-2","grupoTeorico":"GT03"}
                """);
        assertThat(estado(editada)).isEqualTo(200);
        assertThat(json(editada).get("codigo").asText()).isEqualTo("NUEVA-2");

        assertThat(estado(como(tokenA, conEstudiante(delete("/api/materias/" + nuevaId), a)))).isEqualTo(204);
        assertThat(materiaRepository.findById(nuevaId)).isEmpty();
    }

    @Test
    void aGestionaSusHorarios() throws Exception {
        assertThat(ids(como(tokenA, conEstudiante(get("/api/horarios"), a)))).containsExactly(horarioA.getId());
        assertThat(ids(como(tokenA, conEstudiante(get("/api/horarios"), a).param("materiaInscritaId", String.valueOf(materiaA.getId())))))
                .containsExactly(horarioA.getId());
        assertThat(ids(como(tokenA, conEstudiante(get("/api/horarios"), a).param("cicloAcademicoId", String.valueOf(cicloA.getId())))))
                .containsExactly(horarioA.getId());
        assertThat(json(como(tokenA, conEstudiante(get("/api/horarios/" + horarioA.getId()), a))).get("docenteTutor").asText()).isEqualTo("Docente de A");

        MvcResult creado = como(tokenA, conEstudiante(post("/api/horarios"), a), horario(materiaA.getId(), "MARTES", "08:00", "10:00"));
        assertThat(estado(creado)).isEqualTo(201);
        long nuevoId = json(creado).get("id").asLong();

        MvcResult editado = como(tokenA, conEstudiante(put("/api/horarios/" + nuevoId), a),
                """
                {"diaSemana":"MARTES","horaInicio":"10:00","horaFin":"12:00","modalidad":"HIBRIDA","docenteTutor":"Otro docente"}
                """);
        assertThat(estado(editado)).isEqualTo(200);
        assertThat(json(editado).get("horaInicio").asText()).isEqualTo("10:00:00");

        assertThat(estado(como(tokenA, conEstudiante(delete("/api/horarios/" + nuevoId), a)))).isEqualTo(204);
        assertThat(horarioRepository.findById(nuevoId)).isEmpty();
    }

    @Test
    void lasValidacionesDeNegocioExistentesSeConservan() throws Exception {
        MvcResult fechas = como(tokenA, post("/api/ciclos"), ciclo(a.getId(), "Mal", "2026-12-01", "2026-08-01"));
        assertThat(estado(fechas)).isEqualTo(400);
        assertThat(json(fechas).get("message").asText()).isEqualTo("La fecha de inicio no puede ser posterior a la fecha de fin");

        MvcResult codigo = como(tokenA, conEstudiante(post("/api/materias"), a), materia(cicloA.getId(), "Repetida", "PRIV-A"));
        assertThat(estado(codigo)).isEqualTo(400);
        assertThat(json(codigo).get("message").asText()).isEqualTo("Ya existe una materia con ese codigo en el ciclo academico");

        MvcResult horas = como(tokenA, conEstudiante(post("/api/horarios"), a), horario(materiaA.getId(), "MIERCOLES", "10:00", "09:00"));
        assertThat(estado(horas)).isEqualTo(400);
        assertThat(json(horas).get("message").asText()).isEqualTo("La hora de inicio debe ser menor que la hora de fin");

        MvcResult cruce = como(tokenA, conEstudiante(post("/api/horarios"), a), horario(materiaA.getId(), "LUNES", "09:00", "11:00"));
        assertThat(estado(cruce)).isEqualTo(400);
        assertThat(json(cruce).get("message").asText()).isEqualTo("Existe conflicto con otro horario registrado el LUNES de 08:00 a 10:00");
    }

    // ================= B con el estudianteId de A: toda ruta responde 404

    @Test
    void bNoPuedeUsarElEstudianteIdDeAEnNingunaRuta() throws Exception {
        List<MvcResult> intentos = List.of(
                como(tokenB, conEstudiante(get("/api/ciclos"), a)),
                como(tokenB, conEstudiante(get("/api/ciclos"), a).param("estado", "ACTIVO")),
                como(tokenB, conEstudiante(get("/api/ciclos/" + cicloA.getId()), a)),
                como(tokenB, conEstudiante(put("/api/ciclos/" + cicloA.getId()), a),
                        """
                        {"nombre":"Tomado","anio":2026,"fechaInicio":"2026-02-01","fechaFin":"2026-06-30","estado":"ACTIVO"}
                        """),
                como(tokenB, conEstudiante(delete("/api/ciclos/" + cicloA.getId()), a)),
                como(tokenB, conEstudiante(get("/api/materias"), a)),
                como(tokenB, conEstudiante(get("/api/materias"), a).param("cicloAcademicoId", String.valueOf(cicloA.getId()))),
                como(tokenB, conEstudiante(get("/api/materias/" + materiaA.getId()), a)),
                como(tokenB, conEstudiante(post("/api/materias"), a), materia(cicloA.getId(), "Intrusa", "INTR-1")),
                como(tokenB, conEstudiante(put("/api/materias/" + materiaA.getId()), a),
                        """
                        {"nombre":"Tomada","codigo":"TOM","grupoTeorico":"GT01"}
                        """),
                como(tokenB, conEstudiante(delete("/api/materias/" + materiaA.getId()), a)),
                como(tokenB, conEstudiante(get("/api/horarios"), a)),
                como(tokenB, conEstudiante(get("/api/horarios"), a).param("materiaInscritaId", String.valueOf(materiaA.getId()))),
                como(tokenB, conEstudiante(get("/api/horarios"), a).param("cicloAcademicoId", String.valueOf(cicloA.getId()))),
                como(tokenB, conEstudiante(get("/api/horarios/" + horarioA.getId()), a)),
                como(tokenB, conEstudiante(post("/api/horarios"), a), horario(materiaA.getId(), "VIERNES", "08:00", "09:00")),
                como(tokenB, conEstudiante(put("/api/horarios/" + horarioA.getId()), a),
                        """
                        {"diaSemana":"LUNES","horaInicio":"06:00","horaFin":"07:00","modalidad":"VIRTUAL","docenteTutor":"Tomado"}
                        """),
                como(tokenB, conEstudiante(delete("/api/horarios/" + horarioA.getId()), a)));

        for (MvcResult intento : intentos) {
            assertThat(estado(intento)).as(intento.getRequest().getMethod() + " " + intento.getRequest().getRequestURI()).isEqualTo(404);
            assertSinDatosDeA(intento);
        }
        assertDatosDeAIntactos();
    }

    // ================= B con su propio estudianteId sobre recursos de A

    @Test
    void bNoPuedeConsultarModificarNiEliminarLosRegistrosDeA() throws Exception {
        List<MvcResult> intentos = List.of(
                como(tokenB, conEstudiante(get("/api/ciclos/" + cicloA.getId()), b)),
                como(tokenB, conEstudiante(put("/api/ciclos/" + cicloA.getId()), b),
                        """
                        {"nombre":"Tomado","anio":2026,"fechaInicio":"2026-02-01","fechaFin":"2026-06-30","estado":"ACTIVO"}
                        """),
                como(tokenB, conEstudiante(delete("/api/ciclos/" + cicloA.getId()), b)),
                como(tokenB, conEstudiante(get("/api/materias/" + materiaA.getId()), b)),
                como(tokenB, conEstudiante(put("/api/materias/" + materiaA.getId()), b),
                        """
                        {"nombre":"Tomada","codigo":"TOM","grupoTeorico":"GT01"}
                        """),
                como(tokenB, conEstudiante(delete("/api/materias/" + materiaA.getId()), b)),
                como(tokenB, conEstudiante(get("/api/horarios/" + horarioA.getId()), b)),
                como(tokenB, conEstudiante(put("/api/horarios/" + horarioA.getId()), b),
                        """
                        {"diaSemana":"LUNES","horaInicio":"06:00","horaFin":"07:00","modalidad":"VIRTUAL","docenteTutor":"Tomado"}
                        """),
                como(tokenB, conEstudiante(delete("/api/horarios/" + horarioA.getId()), b)));

        for (MvcResult intento : intentos) {
            assertThat(estado(intento)).as(intento.getRequest().getMethod() + " " + intento.getRequest().getRequestURI()).isEqualTo(404);
            assertSinDatosDeA(intento);
        }
        assertDatosDeAIntactos();
    }

    @Test
    void losListadosDeBNoIncluyenNadaDeA() throws Exception {
        MvcResult ciclos = como(tokenB, conEstudiante(get("/api/ciclos"), b));
        MvcResult materias = como(tokenB, conEstudiante(get("/api/materias"), b));
        MvcResult horarios = como(tokenB, conEstudiante(get("/api/horarios"), b));

        assertThat(ids(ciclos)).containsExactly(cicloB.getId());
        assertThat(ids(materias)).containsExactly(materiaB.getId());
        assertThat(ids(horarios)).isEmpty();
        assertSinDatosDeA(ciclos);
        assertSinDatosDeA(materias);
    }

    // ================= Relaciones con registros de A

    @Test
    void bNoPuedeCrearNiListarMateriasEnUnCicloDeA() throws Exception {
        MvcResult creacion = como(tokenB, conEstudiante(post("/api/materias"), b), materia(cicloA.getId(), "Intrusa", "INTR-2"));
        assertThat(estado(creacion)).isEqualTo(404);
        assertThat(json(creacion).get("message").asText()).isEqualTo("No se encontro el ciclo academico solicitado");

        assertThat(estado(como(tokenB, conEstudiante(get("/api/materias"), b).param("cicloAcademicoId", String.valueOf(cicloA.getId())))))
                .isEqualTo(404);
        assertDatosDeAIntactos();
    }

    @Test
    void bNoPuedeCrearNiListarHorariosSobreLaMateriaOElCicloDeA() throws Exception {
        MvcResult creacion = como(tokenB, conEstudiante(post("/api/horarios"), b), horario(materiaA.getId(), "VIERNES", "08:00", "09:00"));
        assertThat(estado(creacion)).isEqualTo(404);
        assertThat(json(creacion).get("message").asText()).isEqualTo("No se encontro la materia inscrita solicitada");

        assertThat(estado(como(tokenB, conEstudiante(get("/api/horarios"), b).param("materiaInscritaId", String.valueOf(materiaA.getId())))))
                .isEqualTo(404);
        assertThat(estado(como(tokenB, conEstudiante(get("/api/horarios"), b).param("cicloAcademicoId", String.valueOf(cicloA.getId())))))
                .isEqualTo(404);
        assertDatosDeAIntactos();
    }

    @Test
    void losConflictosDeHorarioNoComparanConLosHorariosDeA() throws Exception {
        // Mismo día y hora que el horario de A, pero en la materia de B: no hay conflicto con datos ajenos
        MvcResult mismoHorario = como(tokenB, conEstudiante(post("/api/horarios"), b), horario(materiaB.getId(), "LUNES", "08:00", "10:00"));

        assertThat(estado(mismoHorario)).isEqualTo(201);
        assertSinDatosDeA(mismoHorario);
        assertDatosDeAIntactos();
    }

    // ================= Propietario y relaciones no cambian por el cuerpo

    @Test
    void bNoPuedeCrearUnCicloANombreDeA() throws Exception {
        int antes = cicloRepository.findByEstudianteIdOrderByAnioDescFechaInicioDesc(a.getId()).size();

        MvcResult resultado = como(tokenB, post("/api/ciclos"), ciclo(a.getId(), "Ciclo intruso", "2026-08-01", "2026-12-15"));

        assertThat(estado(resultado)).isEqualTo(404);
        assertSinDatosDeA(resultado);
        assertThat(cicloRepository.findByEstudianteIdOrderByAnioDescFechaInicioDesc(a.getId())).hasSize(antes);
    }

    @Test
    void elCicloCreadoPorBEsDeB() throws Exception {
        MvcResult resultado = como(tokenB, post("/api/ciclos"), ciclo(b.getId(), "Ciclo nuevo de B", "2026-08-01", "2026-12-15"));

        assertThat(estado(resultado)).isEqualTo(201);
        long id = json(resultado).get("id").asLong();
        transaccion.executeWithoutResult(estado ->
                assertThat(cicloRepository.findById(id).orElseThrow().getEstudiante().getId()).isEqualTo(b.getId()));
    }

    @Test
    void camposDePropietarioORelacionEnElCuerpoDeUnaActualizacionNoTienenEfecto() throws Exception {
        HorarioClase horarioB = crearHorario(materiaB, "Docente de B");

        MvcResult cicloEditado = como(tokenB, conEstudiante(put("/api/ciclos/" + cicloB.getId()), b),
                """
                {"estudianteId":%d,"nombre":"Ciclo de B editado","anio":2026,"fechaInicio":"2026-02-01","fechaFin":"2026-06-30","estado":"ACTIVO"}
                """.formatted(a.getId()));
        MvcResult materiaEditada = como(tokenB, conEstudiante(put("/api/materias/" + materiaB.getId()), b),
                """
                {"cicloAcademicoId":%d,"nombre":"Materia de B editada","codigo":"MAT-B","grupoTeorico":"GT01"}
                """.formatted(cicloA.getId()));
        MvcResult horarioEditado = como(tokenB, conEstudiante(put("/api/horarios/" + horarioB.getId()), b),
                """
                {"materiaInscritaId":%d,"diaSemana":"MARTES","horaInicio":"08:00","horaFin":"10:00","modalidad":"VIRTUAL","docenteTutor":"Docente de B"}
                """.formatted(materiaA.getId()));

        assertThat(estado(cicloEditado)).isEqualTo(200);
        assertThat(estado(materiaEditada)).isEqualTo(200);
        assertThat(estado(horarioEditado)).isEqualTo(200);
        transaccion.executeWithoutResult(estado -> {
            assertThat(cicloRepository.findById(cicloB.getId()).orElseThrow().getEstudiante().getId()).isEqualTo(b.getId());
            assertThat(materiaRepository.findById(materiaB.getId()).orElseThrow().getCicloAcademico().getId()).isEqualTo(cicloB.getId());
            assertThat(horarioRepository.findById(horarioB.getId()).orElseThrow().getMateriaInscrita().getId()).isEqualTo(materiaB.getId());
        });
        assertDatosDeAIntactos();
    }

    @Test
    void unRecursoInexistenteResponde404() throws Exception {
        assertThat(estado(como(tokenA, conEstudiante(get("/api/ciclos/987654321"), a)))).isEqualTo(404);
        assertThat(estado(como(tokenA, conEstudiante(get("/api/materias/987654321"), a)))).isEqualTo(404);
        assertThat(estado(como(tokenA, conEstudiante(get("/api/horarios/987654321"), a)))).isEqualTo(404);
    }
}
