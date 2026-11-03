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

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// SDACAS-26: endpoint /api/carga/semanas con el estudiante del token. Las fechas se construyen a partir
// del día real para no sustituir el reloj de la aplicación, que también usa la emisión de tokens.
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CargaSemanasTest {

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
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Clock relojAplicacion;

    private LocalDate lunesActual;
    private String tokenA;
    private String tokenB;
    private String tokenSinCicloActivo;

    @BeforeEach
    void prepararDatos() {
        LocalDate hoy = LocalDate.now(relojAplicacion);
        lunesActual = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // A: ciclo activo que termina el domingo de dentro de tres semanas, es decir, cuatro semanas
        Estudiante a = crearEstudiante("Estudiante Uno");
        MateriaInscrita materiaA = crearMateria(crearCiclo(a, EstadoCiclo.ACTIVO, lunesActual.plusDays(27)), "PRN315");
        crearActividad(materiaA, "Vencida ayer", LocalDateTime.now(relojAplicacion).minusDays(1), 8, 30, false);
        crearActividad(materiaA, "Tarea semana 2", lunesActual.plusWeeks(1).plusDays(2).atTime(9, 0), 10, 5, false);
        crearActividad(materiaA, "Proyecto semana 3", lunesActual.plusWeeks(2).plusDays(2).atTime(9, 0), 25, 5, false);
        crearActividad(materiaA, "Parcial A semana 4", lunesActual.plusWeeks(3).plusDays(3).atTime(9, 0), 2, 30, false);
        crearActividad(materiaA, "Parcial B semana 4", lunesActual.plusWeeks(3).plusDays(3).atTime(15, 0), 2, 30, false);
        crearActividad(materiaA, "Calificada semana 4", lunesActual.plusWeeks(3).plusDays(4).atTime(9, 0), 6, 20, true);

        // B: otro estudiante con su propio ciclo activo
        Estudiante b = crearEstudiante("Estudiante Dos");
        MateriaInscrita materiaB = crearMateria(crearCiclo(b, EstadoCiclo.ACTIVO, lunesActual.plusDays(27)), "SECRETA");
        crearActividad(materiaB, "Actividad privada de B", lunesActual.plusWeeks(1).plusDays(1).atTime(9, 0), 40, 50, false);

        // C: solo tiene un ciclo finalizado que abarca el periodo actual
        Estudiante c = crearEstudiante("Estudiante Tres");
        MateriaInscrita materiaC = crearMateria(crearCiclo(c, EstadoCiclo.FINALIZADO, lunesActual.plusDays(27)), "FINALIZADA");
        crearActividad(materiaC, "De ciclo finalizado", lunesActual.plusWeeks(1).atTime(9, 0), 30, 40, false);

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
        tokenSinCicloActivo = tokenService.emitir(c).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("CS" + unico);
        nuevo.setCorreoInstitucional("sem." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, EstadoCiclo estado, LocalDate fin) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo de prueba");
        nuevo.setAnio(fin.getYear());
        nuevo.setFechaInicio(lunesActual.minusWeeks(10));
        nuevo.setFechaFin(fin);
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

    private void crearActividad(MateriaInscrita materia, String nombre, LocalDateTime entrega, double horas, double peso, boolean conNota) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(entrega.minusDays(3));
        nueva.setFechaEntrega(entrega);
        nueva.setPorcentajeEvaluacion(peso);
        nueva.setTiempoEstimadoHoras(horas);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.PARCIAL);
        nueva.setMateriaInscrita(materia);
        if (conNota) {
            Nota nota = new Nota();
            nota.setValorObtenido(8);
            nota.setFechaRegistro(entrega.toLocalDate());
            nueva.setNota(notaRepository.save(nota));
        }
        actividadRepository.save(nueva);
    }

    private MvcResult semanas(String token) throws Exception {
        return mockMvc.perform(get("/api/carga/semanas").header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/carga/semanas")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void devuelveLasSemanasHastaElFinDelCicloConSuNivelYSuDesglose() throws Exception {
        MvcResult resultado = semanas(tokenA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("cicloActivo").asBoolean()).isTrue();
        JsonNode lista = cuerpo.get("semanas");
        assertThat(lista).hasSize(4);
        assertThat(lista.get(0).get("inicio").asText()).isEqualTo(lunesActual.toString());
        assertThat(lista.get(0).get("fin").asText()).isEqualTo(lunesActual.plusDays(6).toString());
        assertThat(lista.get(0).get("semanaActual").asBoolean()).isTrue();
        assertThat(lista.get(1).get("semanaActual").asBoolean()).isFalse();

        // Baja (Normal), Baja, Media (Elevada) y Alta (Crítica por concentración)
        assertThat(lista.findValuesAsText("nivelGeneral")).containsExactly("NORMAL", "NORMAL", "ELEVADA", "CRITICA");

        // La semana actual no incluye la actividad que venció ayer
        assertThat(lista.get(0).toString()).doesNotContain("Vencida ayer");
        assertThat(lista.get(0).get("carga").get("horas").get("horasPendientes").decimalValue()).isZero();

        JsonNode cuarta = lista.get(3).get("carga");
        assertThat(cuarta.get("actividadesPendientes").findValuesAsText("nombre"))
                .containsExactlyInAnyOrder("Parcial A semana 4", "Parcial B semana 4");
        assertThat(cuarta.get("actividadesNoPendientes").findValuesAsText("nombre")).containsExactly("Calificada semana 4");
        assertThat(cuarta.get("horas").get("horasPendientes").decimalValue()).isEqualByComparingTo("4");
    }

    @Test
    void cadaEstudianteSoloVeSusSemanas() throws Exception {
        String cuerpoA = json(semanas(tokenA)).toString();
        String cuerpoB = json(semanas(tokenB)).toString();

        assertThat(cuerpoA).doesNotContain("SECRETA", "Actividad privada de B");
        assertThat(cuerpoB).contains("Actividad privada de B").doesNotContain("PRN315", "Parcial A semana 4");
    }

    @Test
    void sinCicloActivoNoDevuelveSemanasNiDatosDelCicloFinalizado() throws Exception {
        MvcResult resultado = semanas(tokenSinCicloActivo);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = json(resultado);
        assertThat(cuerpo.get("cicloActivo").asBoolean()).isFalse();
        assertThat(cuerpo.get("semanas")).isEmpty();
        assertThat(cuerpo.get("inicioCiclo").isNull()).isTrue();
        assertThat(cuerpo.get("finCiclo").isNull()).isTrue();
        assertThat(cuerpo.toString()).doesNotContain("De ciclo finalizado", "FINALIZADA");
    }

    @Test
    void todasLasSemanasSeResuelvenConDosConsultas() throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        MvcResult resultado = semanas(tokenA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        // Ciclos activos y actividades con materia, ciclo y nota; no depende del número de semanas
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(2);
    }
}
