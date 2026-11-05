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
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.NotaRepository;
import com.saca.api.service.TokenService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Vista mensual del calendario: endpoint /periodo, sus validaciones y su coste en consultas SQL
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarioPeriodoTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 26);
    private static final LocalDate HASTA = LocalDate.of(2026, 12, 6);

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
    private NotaRepository notaRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        Estudiante a = crearEstudiante("Estudiante Uno");
        Estudiante b = crearEstudiante("Estudiante Dos");
        CicloAcademico cicloA = crearCiclo(a);
        MateriaInscrita materiaB = crearMateria(crearCiclo(b), "SECRETA", 0);
        crearHorario(materiaB, DiaSemana.LUNES, null);
        crearActividad(materiaB, "Actividad privada de B", DESDE.plusDays(9), false);

        // Volumen de un mes realista: 4 materias, 3 horarios por materia y 8 actividades por materia
        for (int m = 0; m < 4; m++) {
            MateriaInscrita materia = crearMateria(cicloA, "MAT" + m, m);
            ActividadAcademica vinculada = null;
            for (int i = 0; i < 8; i++) {
                ActividadAcademica actividad = crearActividad(materia, "Actividad " + m + "-" + i, DESDE.plusDays(3 + i * 4 + m), i % 3 == 0);
                if (i == 0) {
                    vinculada = actividad;
                }
            }
            crearHorario(materia, DiaSemana.values()[m], vinculada);
            crearHorario(materia, DiaSemana.values()[m + 1], null);
            crearHorario(materia, DiaSemana.values()[m + 2], null);
        }

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("PE" + unico);
        nuevo.setCorreoInstitucional("per." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo II-2026");
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 8, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 12, 15));
        nuevo.setEstado(EstadoCiclo.ACTIVO);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String codigo, int indice) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre("Materia " + codigo);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT0" + indice);
        return materiaInscritaRepository.save(nueva);
    }

    private void crearHorario(MateriaInscrita materia, DiaSemana dia, ActividadAcademica vinculada) {
        HorarioClase nuevo = new HorarioClase();
        nuevo.setMateriaInscrita(materia);
        nuevo.setActividadAcademica(vinculada);
        nuevo.setDiaSemana(dia);
        nuevo.setHoraInicio(LocalTime.of(7 + dia.ordinal(), 0));
        nuevo.setHoraFin(LocalTime.of(8 + dia.ordinal(), 40));
        nuevo.setModalidad(ModalidadClase.PRESENCIAL);
        horarioRepository.save(nuevo);
    }

    private ActividadAcademica crearActividad(MateriaInscrita materia, String nombre, LocalDate entrega, boolean conNota) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(entrega.minusDays(3).atTime(8, 0));
        nueva.setFechaEntrega(entrega.atTime(10, 0));
        nueva.setPorcentajeEvaluacion(10);
        nueva.setTiempoEstimadoHoras(3);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.LABORATORIO);
        nueva.setMateriaInscrita(materia);
        if (conNota) {
            Nota nota = new Nota();
            nota.setValorObtenido(8);
            nota.setFechaRegistro(entrega);
            nueva.setNota(notaRepository.save(nota));
        }
        return actividadRepository.save(nueva);
    }

    private MvcResult como(String token, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult periodo(String token, Object desde, Object hasta) throws Exception {
        return como(token, get("/api/calendario/periodo").param("desde", String.valueOf(desde)).param("hasta", String.valueOf(hasta)));
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/calendario/periodo").param("desde", "2026-11-02").param("hasta", "2026-11-08"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unaCuadriculaMensualDe42DiasDevuelveEventosYUnResumenPorDia() throws Exception {
        MvcResult resultado = periodo(tokenA, DESDE, HASTA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("desde").asText()).isEqualTo("2026-10-26");
        assertThat(cuerpo.get("hasta").asText()).isEqualTo("2026-12-06");
        assertThat(cuerpo.get("dias")).hasSize(42);
        assertThat(cuerpo.get("eventos").size()).isGreaterThan(32);
        JsonNode dia = cuerpo.get("dias").get(0);
        assertThat(dia.has("clases") && dia.has("actividades") && dia.has("horasPendientes") && dia.has("concentracion")).isTrue();
    }

    @Test
    void validaElRangoDelPeriodo() throws Exception {
        MvcResult invertido = periodo(tokenA, HASTA, DESDE);
        MvcResult demasiadoLargo = periodo(tokenA, DESDE, HASTA.plusDays(1));
        MvcResult sinHasta = como(tokenA, get("/api/calendario/periodo").param("desde", "2026-11-02"));
        MvcResult formatoInvalido = periodo(tokenA, "02/11/2026", "2026-11-08");

        assertThat(invertido.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(invertido).get("message").asText()).isEqualTo("La fecha inicial no puede ser posterior a la fecha final");
        assertThat(demasiadoLargo.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(demasiadoLargo).get("message").asText()).isEqualTo("El periodo no puede superar 42 días");
        assertThat(sinHasta.getResponse().getStatus()).isEqualTo(400);
        assertThat(formatoInvalido.getResponse().getStatus()).isEqualTo(400);
        assertThat(periodo(tokenA, "2026-11-04", "2026-11-04").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unPeriodoDeSieteDiasDevuelveLosMismosEventosQueLaVistaSemanal() throws Exception {
        JsonNode semana = json(como(tokenA, get("/api/calendario/semana").param("fecha", "2026-11-04")));
        JsonNode periodo = json(periodo(tokenA, "2026-11-02", "2026-11-08"));

        assertThat(periodo.get("eventos")).isEqualTo(semana.get("eventos"));
        assertThat(periodo.get("actividadesSinFecha")).isEqualTo(semana.get("actividadesSinFecha"));
        assertThat(semana.get("eventos").size()).isPositive();
    }

    @Test
    void cadaEstudianteSoloVeSuPeriodo() throws Exception {
        String cuerpoA = json(periodo(tokenA, DESDE, HASTA)).toString();
        String cuerpoB = json(periodo(tokenB, DESDE, HASTA)).toString();

        assertThat(cuerpoA).doesNotContain("SECRETA", "Actividad privada de B");
        assertThat(cuerpoB).contains("SECRETA").doesNotContain("MAT0", "Actividad 0-0");
    }

    @Test
    void unMesCompletoSeResuelveConDosConsultas() throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        MvcResult resultado = periodo(tokenA, DESDE, HASTA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        // 12 horarios con materia, ciclo y actividad vinculada; 32 actividades con materia, ciclo y nota
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
    }
}
