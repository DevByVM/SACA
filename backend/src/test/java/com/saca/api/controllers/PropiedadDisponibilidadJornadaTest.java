package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.JornadaLaboralRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Etapa 5A: con los filtros de seguridad activos y tokens reales, el estudiante B no puede ver,
// crear a nombre de A, modificar, eliminar ni reasignar disponibilidades o jornadas de A
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropiedadDisponibilidadJornadaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private DisponibilidadSemanalRepository disponibilidadRepository;

    @Autowired
    private JornadaLaboralRepository jornadaRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante a;
    private Estudiante b;
    private CicloAcademico cicloA;
    private CicloAcademico cicloB;
    private String tokenA;
    private String tokenB;
    private DisponibilidadSemanal bloqueA;
    private JornadaLaboral jornadaA;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        b = crearEstudiante("Estudiante Dos");
        cicloA = crearCiclo(a);
        cicloB = crearCiclo(b);
        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
        bloqueA = disponibilidadRepository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("08:00"), LocalTime.parse("10:00"), "CLASE", cicloA.getId(), a.getId()));
        jornadaA = new JornadaLaboral();
        jornadaA.setDiaSemana("LUNES");
        jornadaA.setHoraInicio(LocalTime.parse("14:00"));
        jornadaA.setHoraFin(LocalTime.parse("18:00"));
        jornadaA.setEstudianteId(a.getId());
        jornadaA = jornadaRepository.save(jornadaA);
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("PR" + unico);
        nuevo.setCorreoInstitucional("prop." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo I");
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 2, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 6, 30));
        nuevo.setEstado(EstadoCiclo.ACTIVO);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return como(token, peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static String bloque(String dia, String inicio, String fin, Long cicloId, Long estudianteId) {
        return """
                {"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","tipoBloque":"ESTUDIO","cicloId":%s,"estudianteId":%s}
                """.formatted(dia, inicio, fin, cicloId, estudianteId);
    }

    private static String jornada(String dia, String inicio, String fin, Long estudianteId) {
        return """
                {"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","estudianteId":%s}
                """.formatted(dia, inicio, fin, estudianteId);
    }

    private List<Long> ids(MvcResult resultado, String campoId) throws Exception {
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(resultado.getResponse().getContentAsString()).forEach(n -> ids.add(n.get(campoId).asLong()));
        return ids;
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private void assertBloqueDeAIntacto() {
        DisponibilidadSemanal actual = disponibilidadRepository.findById(bloqueA.getId()).orElseThrow();
        assertThat(actual.getEstudianteId()).isEqualTo(a.getId());
        assertThat(actual.getCicloId()).isEqualTo(cicloA.getId());
        assertThat(actual.getDiaSemana()).isEqualTo("LUNES");
        assertThat(actual.getHoraInicio()).isEqualTo(LocalTime.parse("08:00"));
        assertThat(actual.getHoraFin()).isEqualTo(LocalTime.parse("10:00"));
    }

    private void assertJornadaDeAIntacta() {
        JornadaLaboral actual = jornadaRepository.findById(jornadaA.getId()).orElseThrow();
        assertThat(actual.getEstudianteId()).isEqualTo(a.getId());
        assertThat(actual.getHoraInicio()).isEqualTo(LocalTime.parse("14:00"));
        assertThat(actual.getHoraFin()).isEqualTo(LocalTime.parse("18:00"));
    }

    private void assertNoAparecenDatosDeA(MvcResult resultado) throws Exception {
        String cuerpo = resultado.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("\"estudianteId\":" + a.getId() + ",", "\"estudianteId\":" + a.getId() + "}")
                .doesNotContain("08:00 a 10:00");
    }

    // ================= Disponibilidades

    @Test
    void aGestionaSusPropiasDisponibilidades() throws Exception {
        assertThat(ids(como(tokenA, get("/api/disponibilidades")), "id")).contains(bloqueA.getId());
        assertThat(json(como(tokenA, get("/api/disponibilidades/" + bloqueA.getId()))).get("horaInicio").asText()).isEqualTo("08:00:00");
        assertThat(ids(como(tokenA, get("/api/disponibilidades/estudiante/" + a.getId() + "/ciclo/" + cicloA.getId())), "id"))
                .containsExactly(bloqueA.getId());

        MvcResult creado = como(tokenA, post("/api/disponibilidades"), bloque("MARTES", "08:00", "10:00", cicloA.getId(), a.getId()));
        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        Long nuevoId = json(creado).get("id").asLong();
        assertThat(json(creado).get("estudianteId").asLong()).isEqualTo(a.getId());

        MvcResult editado = como(tokenA, put("/api/disponibilidades/" + nuevoId), bloque("MARTES", "09:00", "11:00", cicloA.getId(), a.getId()));
        assertThat(editado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(editado).get("horaInicio").asText()).isEqualTo("09:00:00");

        assertThat(como(tokenA, delete("/api/disponibilidades/" + nuevoId)).getResponse().getStatus()).isEqualTo(204);
        assertThat(disponibilidadRepository.findById(nuevoId)).isEmpty();
    }

    @Test
    void bNoVeLasDisponibilidadesDeAEnNingunListado() throws Exception {
        MvcResult listado = como(tokenB, get("/api/disponibilidades"));
        assertThat(listado.getResponse().getStatus()).isEqualTo(200);
        assertThat(ids(listado, "id")).doesNotContain(bloqueA.getId());
        assertNoAparecenDatosDeA(listado);

        assertThat(como(tokenB, get("/api/disponibilidades/estudiante/" + a.getId() + "/ciclo/" + cicloA.getId()))
                .getResponse().getStatus()).isEqualTo(404);
        // Con su propio id pero el ciclo de A: la consulta filtra por B y no devuelve nada de A
        MvcResult conCicloDeA = como(tokenB, get("/api/disponibilidades/estudiante/" + b.getId() + "/ciclo/" + cicloA.getId()));
        assertThat(ids(conCicloDeA, "id")).isEmpty();
    }

    @Test
    void bNoPuedeConsultarModificarNiEliminarLaDisponibilidadDeA() throws Exception {
        MvcResult consulta = como(tokenB, get("/api/disponibilidades/" + bloqueA.getId()));
        assertThat(consulta.getResponse().getStatus()).isEqualTo(404);
        assertNoAparecenDatosDeA(consulta);

        assertThat(como(tokenB, put("/api/disponibilidades/" + bloqueA.getId()),
                bloque("LUNES", "12:00", "13:00", cicloB.getId(), b.getId())).getResponse().getStatus()).isEqualTo(404);
        assertThat(como(tokenB, put("/api/disponibilidades/" + bloqueA.getId()),
                bloque("LUNES", "12:00", "13:00", cicloA.getId(), a.getId())).getResponse().getStatus()).isEqualTo(404);
        assertThat(como(tokenB, delete("/api/disponibilidades/" + bloqueA.getId())).getResponse().getStatus()).isEqualTo(404);

        assertBloqueDeAIntacto();
    }

    @Test
    void bNoPuedeCrearUnaDisponibilidadANombreDeA() throws Exception {
        long antes = disponibilidadRepository.findByEstudianteId(a.getId()).size();

        MvcResult conIdDeA = como(tokenB, post("/api/disponibilidades"), bloque("MIERCOLES", "08:00", "10:00", cicloB.getId(), a.getId()));
        assertThat(conIdDeA.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(conIdDeA).get("message").asText()).isEqualTo("El estudiante indicado no corresponde a la sesión iniciada");

        assertThat(como(tokenB, post("/api/disponibilidades"), bloque("MIERCOLES", "08:00", "10:00", cicloA.getId(), a.getId()))
                .getResponse().getStatus()).isEqualTo(400);

        assertThat(disponibilidadRepository.findByEstudianteId(a.getId())).hasSize((int) antes);
    }

    @Test
    void sinEstudianteEnElCuerpoElPropietarioEsElDelToken() throws Exception {
        MvcResult creado = como(tokenB, post("/api/disponibilidades"), bloque("JUEVES", "08:00", "10:00", cicloB.getId(), null));

        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(creado).get("estudianteId").asLong()).isEqualTo(b.getId());
    }

    @Test
    void unIdEnElCuerpoDelPostNoSobrescribeElBloqueDeA() throws Exception {
        String conId = """
                {"id":%d,"diaSemana":"VIERNES","horaInicio":"07:00","horaFin":"08:00","tipoBloque":"ESTUDIO","cicloId":%d,"estudianteId":%d}
                """.formatted(bloqueA.getId(), cicloB.getId(), b.getId());

        MvcResult creado = como(tokenB, post("/api/disponibilidades"), conId);

        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(creado).get("id").asLong()).isNotEqualTo(bloqueA.getId());
        assertBloqueDeAIntacto();
    }

    @Test
    void bNoPuedeAsociarSuDisponibilidadAUnCicloDeA() throws Exception {
        MvcResult creacion = como(tokenB, post("/api/disponibilidades"), bloque("MARTES", "08:00", "10:00", cicloA.getId(), b.getId()));
        assertThat(creacion.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(creacion).get("message").asText()).isEqualTo("No se encontró el ciclo académico solicitado");

        DisponibilidadSemanal bloqueB = disponibilidadRepository.save(new DisponibilidadSemanal(
                "MARTES", LocalTime.parse("08:00"), LocalTime.parse("10:00"), "CLASE", cicloB.getId(), b.getId()));
        assertThat(como(tokenB, put("/api/disponibilidades/" + bloqueB.getId()), bloque("MARTES", "08:00", "10:00", cicloA.getId(), b.getId()))
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(disponibilidadRepository.findById(bloqueB.getId()).orElseThrow().getCicloId()).isEqualTo(cicloB.getId());

        assertThat(como(tokenB, post("/api/disponibilidades"), bloque("MARTES", "08:00", "10:00", 987654L, b.getId()))
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void bNoPuedeCambiarElPropietarioDeUnaDisponibilidad() throws Exception {
        DisponibilidadSemanal bloqueB = disponibilidadRepository.save(new DisponibilidadSemanal(
                "MARTES", LocalTime.parse("08:00"), LocalTime.parse("10:00"), "CLASE", cicloB.getId(), b.getId()));

        MvcResult haciaA = como(tokenB, put("/api/disponibilidades/" + bloqueB.getId()), bloque("MARTES", "08:00", "10:00", cicloB.getId(), a.getId()));

        assertThat(haciaA.getResponse().getStatus()).isEqualTo(400);
        assertThat(disponibilidadRepository.findById(bloqueB.getId()).orElseThrow().getEstudianteId()).isEqualTo(b.getId());
    }

    @Test
    void losConflictosDeHorarioNoRevelanLosBloquesDeA() throws Exception {
        // Mismo horario que el bloque de A, pero en el ciclo de B: no hay conflicto con datos ajenos
        MvcResult mismoHorario = como(tokenB, post("/api/disponibilidades"), bloque("LUNES", "08:00", "10:00", cicloB.getId(), b.getId()));
        assertThat(mismoHorario.getResponse().getStatus()).isEqualTo(201);

        // Intentar comprobarlo contra el horario de A no llega a comparar: se rechaza antes con un mensaje fijo
        for (String intento : List.of(
                bloque("LUNES", "09:00", "11:00", cicloA.getId(), a.getId()),
                bloque("LUNES", "09:00", "11:00", cicloA.getId(), b.getId()),
                bloque("LUNES", "09:00", "11:00", cicloA.getId(), null))) {
            MvcResult resultado = como(tokenB, post("/api/disponibilidades"), intento);
            assertThat(resultado.getResponse().getStatus()).isIn(400, 404);
            assertThat(json(resultado).get("message").asText()).doesNotContain("08:00", "10:00", "cruza", "Ya existe");
        }

        // El conflicto propio de B solo describe el bloque de B
        MvcResult propio = como(tokenB, post("/api/disponibilidades"), bloque("LUNES", "09:00", "11:00", cicloB.getId(), b.getId()));
        assertThat(propio.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(propio).get("message").asText()).isEqualTo("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00");
        assertBloqueDeAIntacto();
    }

    @Test
    void unaDisponibilidadInexistenteResponde404Y404NoEs500() throws Exception {
        assertThat(como(tokenA, get("/api/disponibilidades/987654321")).getResponse().getStatus()).isEqualTo(404);
        assertThat(como(tokenA, delete("/api/disponibilidades/987654321")).getResponse().getStatus()).isEqualTo(404);
    }

    // ================= Jornadas laborales

    @Test
    void aGestionaSusPropiasJornadas() throws Exception {
        assertThat(ids(como(tokenA, get("/api/jornadas/estudiante/" + a.getId())), "id")).contains(jornadaA.getId());
        assertThat(json(como(tokenA, get("/api/jornadas/" + jornadaA.getId()))).get("horaInicio").asText()).isEqualTo("14:00:00");

        MvcResult creada = como(tokenA, post("/api/jornadas"), jornada("MARTES", "14:00", "18:00", a.getId()));
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        Long nuevaId = json(creada).get("id").asLong();

        MvcResult editada = como(tokenA, put("/api/jornadas/" + nuevaId), jornada("MARTES", "15:00", "19:00", a.getId()));
        assertThat(editada.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(editada).get("horaInicio").asText()).isEqualTo("15:00:00");

        assertThat(como(tokenA, delete("/api/jornadas/" + nuevaId)).getResponse().getStatus()).isEqualTo(204);
        assertThat(jornadaRepository.findById(nuevaId)).isEmpty();
    }

    @Test
    void bNoVeLasJornadasDeA() throws Exception {
        assertThat(como(tokenB, get("/api/jornadas/estudiante/" + a.getId())).getResponse().getStatus()).isEqualTo(404);

        MvcResult propias = como(tokenB, get("/api/jornadas/estudiante/" + b.getId()));
        assertThat(propias.getResponse().getStatus()).isEqualTo(200);
        assertThat(ids(propias, "id")).doesNotContain(jornadaA.getId());
    }

    @Test
    void bNoPuedeConsultarModificarNiEliminarLaJornadaDeA() throws Exception {
        MvcResult consulta = como(tokenB, get("/api/jornadas/" + jornadaA.getId()));
        assertThat(consulta.getResponse().getStatus()).isEqualTo(404);
        assertThat(consulta.getResponse().getContentAsString()).doesNotContain("14:00");

        assertThat(como(tokenB, put("/api/jornadas/" + jornadaA.getId()), jornada("LUNES", "06:00", "07:00", b.getId()))
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(como(tokenB, put("/api/jornadas/" + jornadaA.getId()), jornada("LUNES", "06:00", "07:00", a.getId()))
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(como(tokenB, delete("/api/jornadas/" + jornadaA.getId())).getResponse().getStatus()).isEqualTo(404);

        assertJornadaDeAIntacta();
    }

    @Test
    void bNoPuedeCrearUnaJornadaANombreDeANiSobrescribirla() throws Exception {
        int antes = jornadaRepository.findByEstudianteId(a.getId()).size();

        MvcResult conIdDeA = como(tokenB, post("/api/jornadas"), jornada("MIERCOLES", "14:00", "18:00", a.getId()));
        assertThat(conIdDeA.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(conIdDeA).get("message").asText()).isEqualTo("El estudiante indicado no corresponde a la sesión iniciada");

        String conIdDeLaJornada = """
                {"id":%d,"diaSemana":"MIERCOLES","horaInicio":"06:00","horaFin":"07:00","estudianteId":%d}
                """.formatted(jornadaA.getId(), b.getId());
        MvcResult creada = como(tokenB, post("/api/jornadas"), conIdDeLaJornada);
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(creada).get("id").asLong()).isNotEqualTo(jornadaA.getId());

        MvcResult sinEstudiante = como(tokenB, post("/api/jornadas"), jornada("JUEVES", "14:00", "18:00", null));
        assertThat(json(sinEstudiante).get("estudianteId").asLong()).isEqualTo(b.getId());

        assertThat(jornadaRepository.findByEstudianteId(a.getId())).hasSize(antes);
        assertJornadaDeAIntacta();
    }

    @Test
    void bNoPuedeCambiarElPropietarioDeUnaJornada() throws Exception {
        JornadaLaboral jornadaB = new JornadaLaboral();
        jornadaB.setDiaSemana("MARTES");
        jornadaB.setHoraInicio(LocalTime.parse("14:00"));
        jornadaB.setHoraFin(LocalTime.parse("18:00"));
        jornadaB.setEstudianteId(b.getId());
        jornadaB = jornadaRepository.save(jornadaB);

        assertThat(como(tokenB, put("/api/jornadas/" + jornadaB.getId()), jornada("MARTES", "14:00", "18:00", a.getId()))
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(jornadaRepository.findById(jornadaB.getId()).orElseThrow().getEstudianteId()).isEqualTo(b.getId());
    }

    // Regresión: una actualización sin estudianteId en el cuerpo no debe dejar la jornada sin propietario
    @Test
    void actualizarUnaJornadaSinEstudianteEnElCuerpoConservaAlPropietario() throws Exception {
        String sinCampo = """
                {"diaSemana":"MARTES","horaInicio":"15:00","horaFin":"19:00"}
                """;
        MvcResult editada = como(tokenA, put("/api/jornadas/" + jornadaA.getId()), sinCampo);
        assertThat(editada.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(editada).get("estudianteId").asLong()).isEqualTo(a.getId());

        MvcResult conNulo = como(tokenA, put("/api/jornadas/" + jornadaA.getId()), jornada("MIERCOLES", "16:00", "20:00", null));
        assertThat(conNulo.getResponse().getStatus()).isEqualTo(200);

        JornadaLaboral guardada = jornadaRepository.findById(jornadaA.getId()).orElseThrow();
        assertThat(guardada.getEstudianteId()).isEqualTo(a.getId());
        assertThat(guardada.getDiaSemana()).isEqualTo("MIERCOLES");
        assertThat(guardada.getHoraInicio()).isEqualTo(LocalTime.parse("16:00"));
        assertThat(ids(como(tokenA, get("/api/jornadas/estudiante/" + a.getId())), "id")).contains(jornadaA.getId());
    }

    @Test
    void actualizarUnaDisponibilidadSinEstudianteEnElCuerpoConservaAlPropietario() throws Exception {
        String sinCampo = """
                {"diaSemana":"LUNES","horaInicio":"08:30","horaFin":"10:30","tipoBloque":"CLASE","cicloId":%d}
                """.formatted(cicloA.getId());
        MvcResult editado = como(tokenA, put("/api/disponibilidades/" + bloqueA.getId()), sinCampo);
        assertThat(editado.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(editado).get("estudianteId").asLong()).isEqualTo(a.getId());

        MvcResult conNulo = como(tokenA, put("/api/disponibilidades/" + bloqueA.getId()),
                bloque("LUNES", "09:00", "11:00", cicloA.getId(), null));
        assertThat(conNulo.getResponse().getStatus()).isEqualTo(200);

        DisponibilidadSemanal guardado = disponibilidadRepository.findById(bloqueA.getId()).orElseThrow();
        assertThat(guardado.getEstudianteId()).isEqualTo(a.getId());
        assertThat(guardado.getHoraInicio()).isEqualTo(LocalTime.parse("09:00"));
        assertThat(ids(como(tokenA, get("/api/disponibilidades")), "id")).contains(bloqueA.getId());
    }

    @Test
    void unaJornadaInexistenteResponde404() throws Exception {
        assertThat(como(tokenA, get("/api/jornadas/987654321")).getResponse().getStatus()).isEqualTo(404);
        assertThat(como(tokenA, delete("/api/jornadas/987654321")).getResponse().getStatus()).isEqualTo(404);
    }
}
