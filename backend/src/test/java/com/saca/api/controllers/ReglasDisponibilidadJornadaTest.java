package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// SDACAS-7: bloques de disponibilidad de 30 minutos como mínimo y tipos «No disponible» / «Tiempo de estudio».
// SDACAS-8: la jornada laboral exige hora de inicio anterior a la de fin, de lunes a domingo.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReglasDisponibilidadJornadaTest {

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

    private Estudiante estudiante;
    private CicloAcademico ciclo;
    private String token;

    @BeforeEach
    void prepararDatos() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        estudiante = new Estudiante();
        estudiante.setNombre("Estudiante Jornada");
        estudiante.setCarnet("JD" + unico);
        estudiante.setCorreoInstitucional("jornada." + unico + "@ues.edu.sv");
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante = estudianteRepository.save(estudiante);

        ciclo = new CicloAcademico();
        ciclo.setEstudiante(estudiante);
        ciclo.setNombre("Ciclo de disponibilidad");
        ciclo.setAnio(2026);
        ciclo.setFechaInicio(LocalDate.of(2026, 1, 1));
        ciclo.setFechaFin(LocalDate.of(2026, 12, 31));
        ciclo.setEstado(EstadoCiclo.ACTIVO);
        ciclo = cicloAcademicoRepository.save(ciclo);

        token = tokenService.emitir(estudiante).valor();
    }

    // ================= SDACAS-7: duración mínima

    @Test
    void unBloqueDeMenosDeTreintaMinutosSeRechaza() throws Exception {
        MvcResult resultado = como(post("/api/disponibilidades"), bloque("MARTES", "08:00", "08:15", "ESTUDIO"));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("El bloque debe durar al menos 30 minutos");
        assertThat(disponibilidadRepository.findByEstudianteIdAndCicloId(estudiante.getId(), ciclo.getId())).isEmpty();
    }

    @Test
    void unBloqueDeExactamenteTreintaMinutosSeAcepta() throws Exception {
        assertThat(estado(como(post("/api/disponibilidades"), bloque("MARTES", "08:00", "08:30", "ESTUDIO")))).isEqualTo(201);
    }

    @Test
    void alEditarTambienSeExigeLaDuracionMinima() throws Exception {
        MvcResult creado = como(post("/api/disponibilidades"), bloque("MIERCOLES", "10:00", "11:00", "ESTUDIO"));
        long id = json(creado).get("id").asLong();

        MvcResult editado = como(put("/api/disponibilidades/" + id), bloque("MIERCOLES", "10:00", "10:20", "ESTUDIO"));

        assertThat(estado(editado)).isEqualTo(400);
        assertThat(mensaje(editado)).isEqualTo("El bloque debe durar al menos 30 minutos");
        assertThat(disponibilidadRepository.findById(id).orElseThrow().getHoraFin()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void laValidacionDeInicioAnteriorAlFinSeConserva() throws Exception {
        MvcResult resultado = como(post("/api/disponibilidades"), bloque("JUEVES", "12:00", "09:00", "ESTUDIO"));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("La hora de inicio debe ser menor que la hora de fin");
    }

    // ================= SDACAS-7: tipos de bloque y persistencia por ciclo

    @Test
    void noDisponibleYTiempoDeEstudioSeGuardanPorCiclo() throws Exception {
        assertThat(estado(como(post("/api/disponibilidades"), bloque("LUNES", "07:00", "12:00", "NO_DISPONIBLE")))).isEqualTo(201);
        assertThat(estado(como(post("/api/disponibilidades"), bloque("LUNES", "14:00", "16:00", "ESTUDIO")))).isEqualTo(201);

        MvcResult consulta = como(get("/api/disponibilidades/estudiante/" + estudiante.getId() + "/ciclo/" + ciclo.getId()));
        List<String> tipos = new ArrayList<>();
        json(consulta).forEach((b) -> tipos.add(b.get("tipoBloque").asText()));

        assertThat(estado(consulta)).isEqualTo(200);
        assertThat(tipos).containsExactlyInAnyOrder("NO_DISPONIBLE", "ESTUDIO");
    }

    // ================= SDACAS-8: horas de la jornada

    @Test
    void unaJornadaConInicioPosteriorAlFinSeRechaza() throws Exception {
        MvcResult resultado = como(post("/api/jornadas"), jornada("Martes", "17:00", "08:00"));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("La hora de inicio debe ser menor que la hora de fin");
        assertThat(jornadaRepository.findByEstudianteId(estudiante.getId())).isEmpty();
    }

    @Test
    void unaJornadaSinDuracionSeRechaza() throws Exception {
        MvcResult resultado = como(post("/api/jornadas"), jornada("Martes", "08:00", "08:00"));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("La hora de inicio debe ser menor que la hora de fin");
    }

    @Test
    void unaJornadaSinHorasSeRechazaConUnMensajeYNoConUnError500() throws Exception {
        MvcResult resultado = como(post("/api/jornadas"),
                "{\"diaSemana\":\"Lunes\",\"estudianteId\":%d}".formatted(estudiante.getId()));

        assertThat(estado(resultado)).isEqualTo(400);
        assertThat(mensaje(resultado)).isEqualTo("La hora de inicio y la hora de fin son obligatorias");
    }

    @Test
    void alEditarUnaJornadaTambienSeValidanLasHoras() throws Exception {
        MvcResult creada = como(post("/api/jornadas"), jornada("Miércoles", "08:00", "17:00"));
        long id = json(creada).get("id").asLong();

        MvcResult editada = como(put("/api/jornadas/" + id), jornada("Miércoles", "18:00", "09:00"));

        assertThat(estado(editada)).isEqualTo(400);
        assertThat(jornadaRepository.findById(id).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void seAceptanJornadasDeLunesADomingo() throws Exception {
        assertThat(estado(como(post("/api/jornadas"), jornada("Lunes", "08:00", "17:00")))).isEqualTo(201);
        assertThat(estado(como(post("/api/jornadas"), jornada("Domingo", "09:00", "13:00")))).isEqualTo(201);
    }

    // ================= utilidades

    private String bloque(String dia, String inicio, String fin, String tipo) {
        return """
                {"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","tipoBloque":"%s","cicloId":%d,"estudianteId":%d}
                """.formatted(dia, inicio, fin, tipo, ciclo.getId(), estudiante.getId());
    }

    private String jornada(String dia, String inicio, String fin) {
        return """
                {"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","estudianteId":%d}
                """.formatted(dia, inicio, fin, estudiante.getId());
    }

    private MvcResult como(MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult como(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return como(peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String mensaje(MvcResult resultado) throws Exception {
        return json(resultado).get("message").asText();
    }
}
