package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// SDACAS-15/27: al registrar, la fecha de entrega no puede ser anterior a hoy.
// SDACAS-18: porcentaje de 1 a 100 y la suma por materia no puede superar el 100 %.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReglasActividadTest {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    // El mismo reloj que usa el servicio, para que «hoy» coincida en ambos lados
    @Autowired
    private Clock relojAplicacion;

    private MateriaInscrita materia;
    private MateriaInscrita otraMateria;
    private String token;
    private LocalDate hoy;

    @BeforeEach
    void prepararDatos() {
        Estudiante estudiante = crearEstudiante();
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setEstudiante(estudiante);
        ciclo.setNombre("Ciclo de reglas");
        ciclo.setAnio(2026);
        ciclo.setFechaInicio(LocalDate.of(2026, 1, 1));
        ciclo.setFechaFin(LocalDate.of(2026, 12, 31));
        ciclo.setEstado(EstadoCiclo.ACTIVO);
        ciclo = cicloAcademicoRepository.save(ciclo);
        materia = crearMateria(ciclo, "REG-1");
        otraMateria = crearMateria(ciclo, "REG-2");
        token = tokenService.emitir(estudiante).valor();
        hoy = LocalDate.now(relojAplicacion);
    }

    // ================= SDACAS-15 / SDACAS-27: fecha de entrega

    @Test
    void noSeRegistraUnaActividadConEntregaAnteriorAHoy() throws Exception {
        MvcResult resultado = crear(cuerpo("Vencida", materia.getId(), 10.0, hoy.minusDays(1)));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("La fecha de entrega no puede ser anterior a la fecha actual");
        assertThat(actividadRepository.findByMateriaInscritaId(materia.getId())).isEmpty();
    }

    @Test
    void seRegistraUnaActividadConEntregaHoyOPosterior() throws Exception {
        assertThat(estado(crear(cuerpo("Para hoy", materia.getId(), 10.0, hoy)))).isEqualTo(201);
        assertThat(estado(crear(cuerpo("Para mañana", materia.getId(), 10.0, hoy.plusDays(1))))).isEqualTo(201);
    }

    @Test
    void unaActividadVencidaSePuedeEditarYCompletar() throws Exception {
        ActividadAcademica vencida = guardarActividad(materia, 20.0, hoy.minusDays(5));

        MvcResult resultado = como(put("/api/actividades/" + vencida.getIdActividad()),
                cuerpo("Vencida completada", materia.getId(), 20.0, hoy.minusDays(5))
                        .replace("\"estado\":\"ACTIVO\"", "\"estado\":\"COMPLETADA\""));

        assertThat(estado(resultado)).isEqualTo(200);
    }

    // ================= SDACAS-18: rango del porcentaje

    @Test
    void elPorcentajeDebeEstarEntreUnoYCien() throws Exception {
        MvcResult cero = crear(cuerpo("Cero", materia.getId(), 0.0, hoy.plusDays(3)));
        MvcResult cientoUno = crear(cuerpo("Ciento uno", materia.getId(), 101.0, hoy.plusDays(3)));

        assertThat(estado(cero)).isEqualTo(400);
        assertThat(mensaje(cero)).contains("El minimo de porcentaje es 1");
        assertThat(estado(cientoUno)).isEqualTo(400);
        assertThat(mensaje(cientoUno)).contains("El maximo de porcentaje es 100");
        assertThat(estado(crear(cuerpo("Uno", materia.getId(), 1.0, hoy.plusDays(3))))).isEqualTo(201);
    }

    // ================= SDACAS-18: suma por materia

    @Test
    void alRegistrarNoSeSuperaElCienPorCientoDeLaMateria() throws Exception {
        guardarActividad(materia, 70.0, hoy.plusDays(2));

        MvcResult excede = crear(cuerpo("Excede", materia.getId(), 40.0, hoy.plusDays(3)));

        assertThat(estado(excede)).isEqualTo(400);
        assertThat(mensaje(excede)).isEqualTo("Solo cuenta con 30% disponible para esta materia.");
        assertThat(estado(crear(cuerpo("Completa el cien", materia.getId(), 30.0, hoy.plusDays(3))))).isEqualTo(201);
        assertThat(estado(crear(cuerpo("Sobra", materia.getId(), 1.0, hoy.plusDays(3))))).isEqualTo(400);
    }

    @Test
    void laSumaSeCalculaPorMateria() throws Exception {
        guardarActividad(materia, 100.0, hoy.plusDays(2));

        assertThat(estado(crear(cuerpo("En otra materia", otraMateria.getId(), 100.0, hoy.plusDays(3))))).isEqualTo(201);
    }

    @Test
    void alEditarSeDescuentaElPorcentajeDeLaPropiaActividad() throws Exception {
        guardarActividad(materia, 70.0, hoy.plusDays(2));
        ActividadAcademica editada = guardarActividad(materia, 30.0, hoy.plusDays(4));
        String ruta = "/api/actividades/" + editada.getIdActividad();

        assertThat(estado(como(put(ruta), cuerpo("Sin cambio de peso", materia.getId(), 30.0, hoy.plusDays(4))))).isEqualTo(200);

        MvcResult excede = como(put(ruta), cuerpo("Más peso", materia.getId(), 31.0, hoy.plusDays(4)));
        assertThat(estado(excede)).isEqualTo(400);
        assertThat(mensaje(excede)).isEqualTo("Solo cuenta con 30% disponible para esta materia.");
    }

    @Test
    void alMoverUnaActividadSeValidaLaMateriaDeDestino() throws Exception {
        guardarActividad(otraMateria, 90.0, hoy.plusDays(2));
        ActividadAcademica movida = guardarActividad(materia, 20.0, hoy.plusDays(4));

        MvcResult resultado = como(put("/api/actividades/" + movida.getIdActividad()),
                cuerpo("Movida", otraMateria.getId(), 20.0, hoy.plusDays(4)));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("Solo cuenta con 10% disponible para esta materia.");
    }

    @Test
    void losDecimalesQueSumanExactamenteCienSeAceptan() throws Exception {
        guardarActividad(materia, 33.3, hoy.plusDays(2));
        guardarActividad(materia, 33.3, hoy.plusDays(3));

        assertThat(estado(crear(cuerpo("Resto exacto", materia.getId(), 33.4, hoy.plusDays(4))))).isEqualTo(201);
    }

    // ================= utilidades

    private Estudiante crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Reglas");
        nuevo.setCarnet("RG" + unico);
        nuevo.setCorreoInstitucional("reglas." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private MateriaInscrita crearMateria(CicloAcademico ciclo, String codigo) {
        MateriaInscrita nueva = new MateriaInscrita();
        nueva.setCicloAcademico(ciclo);
        nueva.setNombre("Materia " + codigo);
        nueva.setCodigo(codigo);
        nueva.setGrupoTeorico("GT01");
        return materiaInscritaRepository.save(nueva);
    }

    private ActividadAcademica guardarActividad(MateriaInscrita destino, double porcentaje, LocalDate entrega) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre("Existente " + porcentaje);
        nueva.setFechaInicio(entrega.minusDays(1).atTime(8, 0));
        nueva.setFechaEntrega(entrega.atTime(8, 0));
        nueva.setPorcentajeEvaluacion(porcentaje);
        nueva.setTiempoEstimadoHoras(2);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.LABORATORIO);
        nueva.setMateriaInscrita(destino);
        return actividadRepository.save(nueva);
    }

    private String cuerpo(String nombre, Long materiaInscritaId, Double porcentaje, LocalDate entrega) {
        LocalDateTime fechaEntrega = entrega.atTime(23, 0);
        return """
                {"nombre":"%s","fechaInicio":"%s","fechaEntrega":"%s",
                 "porcentajeEvaluacion":%s,"tiempoEstimadoHoras":3,"estado":"ACTIVO","tipoActividad":"LABORATORIO",
                 "materiaInscritaId":%s}
                """.formatted(nombre, FORMATO_FECHA.format(fechaEntrega.minusDays(1)), FORMATO_FECHA.format(fechaEntrega),
                porcentaje, materiaInscritaId);
    }

    private MvcResult crear(String cuerpo) throws Exception {
        return como(post("/api/actividades"), cuerpo);
    }

    private MvcResult como(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    private int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    private String mensaje(MvcResult resultado) throws Exception {
        JsonNode cuerpo = objectMapper.readTree(resultado.getResponse().getContentAsString());
        return cuerpo.get("message").asText();
    }
}
