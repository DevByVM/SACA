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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// SDACAS-32 (E3): endpoint /api/calendario/franjas con el estudiante del token
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarioFranjasTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private JornadaLaboralRepository jornadaRepository;

    @Autowired
    private DisponibilidadSemanalRepository disponibilidadRepository;

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
        Estudiante a = crearEstudiante();
        Estudiante b = crearEstudiante();
        CicloAcademico cicloA = crearCiclo(a, EstadoCiclo.ACTIVO);
        CicloAcademico finalizadoA = crearCiclo(a, EstadoCiclo.FINALIZADO);

        jornadaRepository.save(new JornadaLaboral("Martes", LocalTime.of(8, 0), LocalTime.of(12, 0), a.getId()));
        disponibilidadRepository.save(new DisponibilidadSemanal("LUNES", LocalTime.of(18, 0), LocalTime.of(20, 0), "ESTUDIO", cicloA.getId(), a.getId()));
        disponibilidadRepository.save(new DisponibilidadSemanal("MIERCOLES", LocalTime.of(7, 0), LocalTime.of(9, 0), "NO_DISPONIBLE", cicloA.getId(), a.getId()));
        disponibilidadRepository.save(new DisponibilidadSemanal("JUEVES", LocalTime.of(10, 0), LocalTime.of(12, 0), "CLASE", cicloA.getId(), a.getId()));
        disponibilidadRepository.save(new DisponibilidadSemanal("VIERNES", LocalTime.of(10, 0), LocalTime.of(12, 0), "ESTUDIO", finalizadoA.getId(), a.getId()));

        jornadaRepository.save(new JornadaLaboral("Lunes", LocalTime.of(13, 0), LocalTime.of(17, 0), b.getId()));

        tokenA = tokenService.emitir(a).valor();
        tokenB = tokenService.emitir(b).valor();
    }

    private Estudiante crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Franjas");
        nuevo.setCarnet("FR" + unico);
        nuevo.setCorreoInstitucional("franjas." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, EstadoCiclo estado) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre("Ciclo II-2026");
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 8, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 12, 15));
        nuevo.setEstado(estado);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MvcResult franjas(String token, String desde, String hasta) throws Exception {
        return mockMvc.perform(get("/api/calendario/franjas").param("desde", desde).param("hasta", hasta)
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void sinTokenResponde401() throws Exception {
        assertThat(mockMvc.perform(get("/api/calendario/franjas").param("desde", "2026-11-02").param("hasta", "2026-11-08"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void devuelveLaJornadaYLosBloquesNoDisponibleYEstudioDelCicloActivo() throws Exception {
        MvcResult resultado = franjas(tokenA, "2026-11-02", "2026-11-08");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        JsonNode lista = json(resultado).get("franjas");
        assertThat(lista.findValuesAsText("tipo")).containsExactly("ESTUDIO", "JORNADA_LABORAL", "NO_DISPONIBLE");
        assertThat(lista.findValuesAsText("fecha")).containsExactly("2026-11-02", "2026-11-03", "2026-11-04");
        assertThat(lista.get(1).get("horaInicio").asText()).isEqualTo("08:00:00");
    }

    @Test
    void cadaEstudianteSoloVeSusFranjas() throws Exception {
        JsonNode deB = json(franjas(tokenB, "2026-11-02", "2026-11-08")).get("franjas");

        assertThat(deB).hasSize(1);
        assertThat(deB.get(0).get("fecha").asText()).isEqualTo("2026-11-02");
        assertThat(deB.get(0).get("horaInicio").asText()).isEqualTo("13:00:00");
    }

    @Test
    void validaElRangoConLasMismasReglasQueElPeriodo() throws Exception {
        MvcResult invertido = franjas(tokenA, "2026-11-08", "2026-11-02");
        MvcResult largo = franjas(tokenA, "2026-11-01", "2026-12-15");
        MvcResult sinHasta = mockMvc.perform(get("/api/calendario/franjas").param("desde", "2026-11-02")
                .header("Authorization", "Bearer " + tokenA)).andReturn();

        assertThat(invertido.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(invertido).get("message").asText()).isEqualTo("La fecha inicial no puede ser posterior a la fecha final");
        assertThat(largo.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(largo).get("message").asText()).isEqualTo("El periodo no puede superar 42 días");
        assertThat(sinHasta.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void unaCuadriculaMensualSeResuelveConTresConsultas() throws Exception {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        MvcResult resultado = franjas(tokenA, "2026-10-26", "2026-12-06");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        // Jornadas, ciclos activos y bloques de disponibilidad; no depende del número de días
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
        assertThat(json(resultado).get("franjas")).hasSize(18);
    }
}
