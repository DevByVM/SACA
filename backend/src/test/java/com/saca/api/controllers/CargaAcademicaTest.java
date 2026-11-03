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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// SDACAS-20: la carga semanal solo considera las actividades del estudiante del token en sus ciclos activos
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CargaAcademicaTest {

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

    private MateriaInscrita materiaA;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void prepararDatos() {
        Estudiante a = crearEstudiante("Estudiante Uno");
        Estudiante b = crearEstudiante("Estudiante Dos");
        materiaA = crearMateria(crearCiclo(a, EstadoCiclo.ACTIVO), "PRN315");
        MateriaInscrita materiaFinalizadaA = crearMateria(crearCiclo(a, EstadoCiclo.FINALIZADO), "ANT115");
        MateriaInscrita materiaB = crearMateria(crearCiclo(b, EstadoCiclo.ACTIVO), "SECRETA");

        LocalDateTime pronto = LocalDateTime.now().plusDays(2);
        crearActividad(materiaA, "Parcial de A", 25, 30, pronto);
        crearActividad(materiaA, "Proyecto lejano", 12, 20, LocalDateTime.now().plusDays(20));
        crearActividad(materiaFinalizadaA, "Del ciclo anterior", 50, 50, pronto);
        crearActividad(materiaB, "Actividad privada de B", 40, 60, pronto);

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante(String nombre) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre(nombre);
        nuevo.setCarnet("CG" + unico);
        nuevo.setCorreoInstitucional("carga." + unico + "@ues.edu.sv");
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

    private void crearActividad(MateriaInscrita materia, String nombre, double horas, double peso, LocalDateTime entrega) {
        ActividadAcademica nueva = new ActividadAcademica();
        nueva.setNombre(nombre);
        nueva.setFechaInicio(entrega.minusDays(5));
        nueva.setFechaEntrega(entrega);
        nueva.setPorcentajeEvaluacion(peso);
        nueva.setTiempoEstimadoHoras(horas);
        nueva.setEstado("ACTIVO");
        nueva.setTipoActividad(TipoActividadAcademica.PARCIAL);
        nueva.setMateriaInscrita(materia);
        actividadRepository.save(nueva);
    }

    private JsonNode consultar(String token) throws Exception {
        MvcResult resultado = mockMvc.perform(get("/api/carga/semanal").header("Authorization", "Bearer " + token)).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private static List<String> nombres(JsonNode actividades) {
        List<String> nombres = new ArrayList<>();
        actividades.forEach(actividad -> nombres.add(actividad.get("nombre").asText()));
        return nombres;
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/carga/semanal")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void devuelveAmbosIndicadoresLaClasificacionYSusMotivos() throws Exception {
        JsonNode carga = consultar(tokenA);

        assertThat(nombres(carga.get("actividadesPendientes"))).containsExactly("Parcial de A");
        assertThat(carga.get("horas").get("horasPendientes").decimalValue()).isEqualByComparingTo("25");
        assertThat(carga.get("horas").get("nivel").asText()).isEqualTo("ELEVADA");
        assertThat(carga.get("exigencia").get("pesoAcumulado").decimalValue()).isEqualByComparingTo("30");
        assertThat(carga.get("exigencia").get("evaluacionesAltoPeso").asInt()).isEqualTo(1);
        assertThat(carga.get("exigencia").get("nivel").asText()).isEqualTo("ELEVADA");
        assertThat(carga.get("nivelGeneral").asText()).isEqualTo("CRITICA");
        assertThat(carga.get("motivos")).hasSize(3);
        assertThat(carga.get("actividadesSinFecha").asInt()).isZero();
        // Los campos que ya usaba el frontend siguen presentes en cada actividad
        JsonNode actividad = carga.get("actividadesPendientes").get(0);
        assertThat(actividad.has("idActividad") && actividad.has("materiaInscritaCodigo") && actividad.has("tiempoEstimadoHoras")).isTrue();
    }

    @Test
    void noIncluyeCiclosFinalizadosNiActividadesDeOtroEstudiante() throws Exception {
        String cuerpoA = consultar(tokenA).toString();
        JsonNode cargaB = consultar(tokenB);

        assertThat(cuerpoA).doesNotContain("Del ciclo anterior", "Actividad privada de B", "SECRETA");
        assertThat(nombres(cargaB.get("actividadesPendientes"))).containsExactly("Actividad privada de B");
        assertThat(cargaB.toString()).doesNotContain("Parcial de A", "PRN315");
    }

    // D6: sin @NotNull, unas horas nulas llegaban al campo primitivo de la entidad y respondían 500
    @Test
    void unaActividadSinHorasSeRechazaConUnMensajeClaro() throws Exception {
        String cuerpo = """
                {"nombre":"Sin horas","fechaInicio":"%s 08:00","fechaEntrega":"%s 08:00",
                 "porcentajeEvaluacion":10,"tiempoEstimadoHoras":null,"estado":"ACTIVO","tipoActividad":"PARCIAL",
                 "materiaInscritaId":%s}
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), materiaA.getId());

        MvcResult resultado = mockMvc.perform(post("/api/actividades")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(resultado.getResponse().getContentAsString()).get("message").asText())
                .isEqualTo("tiempoEstimadoHoras: El tiempo estimado de horas es obligatorio");
    }
}
