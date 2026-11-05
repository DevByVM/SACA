package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.ModalidadClase;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Endpoint semanal del calendario y orden corregido del endpoint anterior
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarioSemanaTest {

    private static final LocalDate MIERCOLES = LocalDate.of(2026, 11, 4);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private MateriaInscritaRepository materiaInscritaRepository;

    @Autowired
    private HorarioClaseRepository horarioRepository;

    @Autowired
    private ActividadAcademicaRepository actividadRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante a;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        a = crearEstudiante("Estudiante Uno");
        Estudiante b = crearEstudiante("Estudiante Dos");
        MateriaInscrita materiaA = crearMateria(crearCiclo(a, EstadoCiclo.ACTIVO), "PRN315");
        MateriaInscrita materiaFinalizadaA = crearMateria(crearCiclo(a, EstadoCiclo.FINALIZADO), "ANT115");
        MateriaInscrita materiaB = crearMateria(crearCiclo(b, EstadoCiclo.ACTIVO), "SECRETA");

        crearHorario(materiaA, DiaSemana.SABADO, 7);
        crearHorario(materiaA, DiaSemana.VIERNES, 8);
        crearHorario(materiaA, DiaSemana.LUNES, 10);
        crearHorario(materiaFinalizadaA, DiaSemana.MARTES, 7);
        crearHorario(materiaB, DiaSemana.MIERCOLES, 7);

        crearActividad(materiaA, "Parcial de esta semana", MIERCOLES);
        crearActividad(materiaA, "Proyecto de la semana siguiente", MIERCOLES.plusDays(7));
        crearActividad(materiaB, "Actividad privada de B", MIERCOLES);

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("CA" + unico);
        nuevo.setCorreoInstitucional("cal." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    // Ciclos que abarcan cualquier fecha usada en las pruebas, incluida la semana actual
    private CicloAcademico crearCiclo(Estudiante propietario, EstadoCiclo estado) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo " + estado);
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 1, 1));
        nuevo.setFechaFin(LocalDate.of(2030, 12, 31));
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

    private void crearHorario(MateriaInscrita materia, DiaSemana dia, int hora) {
        HorarioClase nuevo = new HorarioClase();
        nuevo.setMateriaInscrita(materia);
        nuevo.setDiaSemana(dia);
        nuevo.setHoraInicio(LocalTime.of(hora, 0));
        nuevo.setHoraFin(LocalTime.of(hora + 1, 40));
        nuevo.setModalidad(ModalidadClase.PRESENCIAL);
        horarioRepository.save(nuevo);
    }

    private void crearActividad(MateriaInscrita materia, String nombre, LocalDate entrega) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(entrega.minusDays(3).atTime(8, 0));
        nueva.setFechaEntrega(entrega.atTime(9, 0));
        nueva.setPorcentajeEvaluacion(30);
        nueva.setTiempoEstimadoHoras(4);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.PARCIAL);
        nueva.setMateriaInscrita(materia);
        actividadRepository.save(nueva);
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private static List<String> titulos(JsonNode semana) {
        List<String> titulos = new ArrayList<>();
        semana.get("eventos").forEach(evento -> titulos.add(evento.get("tipo").asText() + " " + evento.get("titulo").asText()));
        return titulos;
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/calendario/semana")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void devuelveLaSemanaDeLaFechaConClasesYActividadesOrdenadas() throws Exception {
        MvcResult resultado = como(tokenA, get("/api/calendario/semana").param("fecha", MIERCOLES.toString()));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode semana = json(resultado);
        assertThat(semana.get("inicio").asText()).isEqualTo("2026-11-02");
        assertThat(semana.get("fin").asText()).isEqualTo("2026-11-08");
        assertThat(titulos(semana)).containsExactly(
                "CLASE Materia PRN315",
                "ACTIVIDAD Parcial de esta semana",
                "CLASE Materia PRN315",
                "CLASE Materia PRN315");
        assertThat(semana.get("eventos").get(0).get("fecha").asText()).isEqualTo("2026-11-02");
        assertThat(semana.get("eventos").get(2).get("fecha").asText()).isEqualTo("2026-11-06");
        assertThat(semana.get("eventos").get(3).get("fecha").asText()).isEqualTo("2026-11-07");
        JsonNode parcial = semana.get("eventos").get(1);
        assertThat(parcial.get("tipoActividad").asText()).isEqualTo("PARCIAL");
        assertThat(parcial.get("porcentajeEvaluacion").decimalValue()).isEqualByComparingTo("30");
        assertThat(semana.get("actividadesSinFecha").asInt()).isZero();
    }

    @Test
    void alCambiarDeSemanaCambianLosEventos() throws Exception {
        JsonNode siguiente = json(como(tokenA, get("/api/calendario/semana").param("fecha", MIERCOLES.plusDays(7).toString())));

        assertThat(siguiente.get("inicio").asText()).isEqualTo("2026-11-09");
        assertThat(titulos(siguiente)).contains("ACTIVIDAD Proyecto de la semana siguiente")
                .doesNotContain("ACTIVIDAD Parcial de esta semana");
    }

    @Test
    void sinFechaSeUsaLaSemanaActual() throws Exception {
        JsonNode semana = json(como(tokenA, get("/api/calendario/semana")));

        LocalDate lunesActual = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        assertThat(semana.get("inicio").asText()).isEqualTo(lunesActual.toString());
        assertThat(semana.get("fin").asText()).isEqualTo(lunesActual.plusDays(6).toString());
    }

    @Test
    void unaFechaInvalidaResponde400() throws Exception {
        MvcResult resultado = como(tokenA, get("/api/calendario/semana").param("fecha", "04/11/2026"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void cadaEstudianteSoloVeSuCalendarioYSoloDelCicloActivo() throws Exception {
        String calendarioA = json(como(tokenA, get("/api/calendario/semana").param("fecha", MIERCOLES.toString()))).toString();
        JsonNode calendarioB = json(como(tokenB, get("/api/calendario/semana").param("fecha", MIERCOLES.toString())));

        assertThat(calendarioA).doesNotContain("SECRETA", "Actividad privada de B", "ANT115");
        assertThat(titulos(calendarioB)).containsExactly("CLASE Materia SECRETA", "ACTIVIDAD Actividad privada de B");
        assertThat(calendarioB.toString()).doesNotContain("PRN315", "Parcial de esta semana");
    }

    // F7: el endpoint anterior se conserva y ahora ordena los días de lunes a domingo
    @Test
    void elEndpointAnteriorDevuelveLosDiasEnOrden() throws Exception {
        JsonNode horarios = json(como(tokenA, get("/api/calendario/semanal/" + a.getId())));

        List<String> dias = new ArrayList<>();
        horarios.forEach(horario -> dias.add(horario.get("diaSemana").asText()));
        assertThat(dias).containsExactly("LUNES", "MARTES", "VIERNES", "SABADO");
    }
}
