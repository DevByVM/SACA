package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.JornadaLaboral;
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

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Jornada laboral: días permitidos, duración mínima, duplicados y cruces entre jornadas del mismo
// estudiante, al crear y al editar. Las jornadas consecutivas (salida = entrada) se aceptan.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReglasJornadaLaboralTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private JornadaLaboralRepository jornadaRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante estudiante;
    private String token;

    @BeforeEach
    void prepararDatos() {
        estudiante = crearEstudiante();
        token = tokenService.emitir(estudiante).valor();
    }

    private Estudiante crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Jornada");
        nuevo.setCarnet("JR" + unico);
        nuevo.setCorreoInstitucional("jornada32." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    // ================= días permitidos

    @Test
    void seAceptanLosSieteDiasEnCualquierFormatoYSeGuardanComoLlegan() throws Exception {
        String[][] dias = {
                {"Lunes", "Lunes"}, {"MARTES", "MARTES"}, {"miercoles", "miercoles"}, {" Miércoles ", "Miércoles"},
                {"JUEVES", "JUEVES"}, {"viernes", "viernes"}, {"SÁBADO", "SÁBADO"}, {"Domingo", "Domingo"}};
        int hora = 6;
        for (String[] dia : dias) {
            MvcResult creada = crear(dia[0], "%02d:00".formatted(hora), "%02d:00".formatted(hora + 1));
            hora++;
            assertThat(estado(creada)).as(dia[0]).isEqualTo(201);
            assertThat(json(creada).get("diaSemana").asText()).isEqualTo(dia[1]);
        }
    }

    @Test
    void unDiaQueNoEsDeLunesADomingoSeRechaza() throws Exception {
        for (String dia : new String[] {"Funday", "LUNES2", "Lun", "7"}) {
            MvcResult resultado = crear(dia, "08:00", "12:00");
            assertThat(estado(resultado)).as(dia).isEqualTo(400);
            assertThat(mensaje(resultado)).isEqualTo("El día de la semana no es válido: usa un día de lunes a domingo");
        }
        assertThat(jornadaRepository.findByEstudianteId(estudiante.getId())).isEmpty();
    }

    @Test
    void unaJornadaSinDiaSeRechaza() throws Exception {
        MvcResult sinCampo = como(post("/api/jornadas"), "{\"horaInicio\":\"08:00\",\"horaFin\":\"12:00\"}");
        MvcResult vacio = crear("  ", "08:00", "12:00");

        assertThat(estado(sinCampo)).isEqualTo(400);
        assertThat(mensaje(sinCampo)).isEqualTo("El día de la semana es obligatorio");
        assertThat(estado(vacio)).isEqualTo(400);
        assertThat(jornadaRepository.findByEstudianteId(estudiante.getId())).isEmpty();
    }

    // ================= duración mínima

    @Test
    void unaJornadaDeMenosDeTreintaMinutosSeRechazaYDeTreintaSeAcepta() throws Exception {
        MvcResult corta = crear("Lunes", "08:00", "08:29");
        MvcResult justa = crear("Lunes", "09:00", "09:30");

        assertThat(estado(corta)).isEqualTo(400);
        assertThat(mensaje(corta)).isEqualTo("La jornada debe durar al menos 30 minutos");
        assertThat(estado(justa)).isEqualTo(201);
    }

    // ================= duplicados y cruces al crear

    @Test
    void unaJornadaDuplicadaSeRechazaAunqueElDiaVengaEnOtroFormato() throws Exception {
        assertThat(estado(crear("Miércoles", "08:00", "12:00"))).isEqualTo(201);

        MvcResult duplicada = crear("MIERCOLES", "08:00", "12:00");

        assertThat(estado(duplicada)).isEqualTo(400);
        assertThat(mensaje(duplicada)).isEqualTo("Ya tienes registrada una jornada el Miércoles de 08:00 a 12:00");
        assertThat(jornadaRepository.findByEstudianteId(estudiante.getId())).hasSize(1);
    }

    @Test
    void unaJornadaQueSeSolapaElMismoDiaSeRechaza() throws Exception {
        assertThat(estado(crear("Martes", "08:00", "12:00"))).isEqualTo(201);

        for (String[] rango : new String[][] {{"11:00", "15:00"}, {"07:00", "08:30"}, {"09:00", "10:00"}, {"07:00", "13:00"}}) {
            MvcResult cruzada = crear("Martes", rango[0], rango[1]);
            assertThat(estado(cruzada)).as(rango[0] + "-" + rango[1]).isEqualTo(400);
            assertThat(mensaje(cruzada)).isEqualTo("La jornada se cruza con otra registrada el Martes de 08:00 a 12:00");
        }
        assertThat(jornadaRepository.findByEstudianteId(estudiante.getId())).hasSize(1);
    }

    @Test
    void lasJornadasConsecutivasYLasDeOtroDiaSeAceptan() throws Exception {
        assertThat(estado(crear("Jueves", "08:00", "12:00"))).isEqualTo(201);

        assertThat(estado(crear("Jueves", "12:00", "16:00"))).isEqualTo(201);
        assertThat(estado(crear("Jueves", "06:00", "08:00"))).isEqualTo(201);
        assertThat(estado(crear("Viernes", "08:00", "12:00"))).isEqualTo(201);
        assertThat(jornadaRepository.findByEstudianteId(estudiante.getId())).hasSize(4);
    }

    @Test
    void lasJornadasGuardadasConElDiaEnOtroFormatoTambienSeComparan() throws Exception {
        JornadaLaboral anterior = new JornadaLaboral("SABADO", LocalTime.of(8, 0), LocalTime.of(12, 0), estudiante.getId());
        jornadaRepository.save(anterior);

        MvcResult cruzada = crear("Sábado", "10:00", "14:00");

        assertThat(estado(cruzada)).isEqualTo(400);
        assertThat(mensaje(cruzada)).isEqualTo("La jornada se cruza con otra registrada el SABADO de 08:00 a 12:00");
    }

    @Test
    void lasJornadasDeOtroEstudianteNoProducenConflicto() throws Exception {
        Estudiante otro = crearEstudiante();
        jornadaRepository.save(new JornadaLaboral("Lunes", LocalTime.of(8, 0), LocalTime.of(12, 0), otro.getId()));

        assertThat(estado(crear("Lunes", "08:00", "12:00"))).isEqualTo(201);
    }

    // ================= edición

    @Test
    void editarUnaJornadaNoChocaConsigoMisma() throws Exception {
        long id = json(crear("Lunes", "14:00", "18:00")).get("id").asLong();

        MvcResult igual = como(put("/api/jornadas/" + id), jornada("Lunes", "14:00", "18:00"));
        MvcResult desplazada = como(put("/api/jornadas/" + id), jornada("LUNES", "15:00", "19:00"));

        assertThat(estado(igual)).isEqualTo(200);
        assertThat(estado(desplazada)).isEqualTo(200);
        JornadaLaboral guardada = jornadaRepository.findById(id).orElseThrow();
        assertThat(guardada.getHoraInicio()).isEqualTo(LocalTime.of(15, 0));
        assertThat(guardada.getDiaSemana()).isEqualTo("LUNES");
    }

    @Test
    void editarParaCruzarseODuplicarOtraJornadaSeRechazaYNoModifica() throws Exception {
        crear("Martes", "08:00", "12:00");
        long id = json(crear("Martes", "14:00", "18:00")).get("id").asLong();

        MvcResult cruzada = como(put("/api/jornadas/" + id), jornada("Martes", "11:00", "15:00"));
        MvcResult duplicada = como(put("/api/jornadas/" + id), jornada("Martes", "08:00", "12:00"));

        assertThat(estado(cruzada)).isEqualTo(400);
        assertThat(mensaje(cruzada)).isEqualTo("La jornada se cruza con otra registrada el Martes de 08:00 a 12:00");
        assertThat(estado(duplicada)).isEqualTo(400);
        assertThat(mensaje(duplicada)).isEqualTo("Ya tienes registrada una jornada el Martes de 08:00 a 12:00");
        assertThat(jornadaRepository.findById(id).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void editarAUnaJornadaConsecutivaUOtroDiaSeAcepta() throws Exception {
        crear("Martes", "08:00", "12:00");
        long id = json(crear("Martes", "14:00", "18:00")).get("id").asLong();

        assertThat(estado(como(put("/api/jornadas/" + id), jornada("Martes", "12:00", "16:00")))).isEqualTo(200);
        assertThat(estado(como(put("/api/jornadas/" + id), jornada("Miércoles", "08:00", "12:00")))).isEqualTo(200);
        assertThat(jornadaRepository.findById(id).orElseThrow().getDiaSemana()).isEqualTo("Miércoles");
    }

    @Test
    void editarConUnDiaInvalidoOMenosDeTreintaMinutosSeRechazaYNoModifica() throws Exception {
        long id = json(crear("Viernes", "08:00", "12:00")).get("id").asLong();

        MvcResult diaInvalido = como(put("/api/jornadas/" + id), jornada("Feriado", "08:00", "12:00"));
        MvcResult corta = como(put("/api/jornadas/" + id), jornada("Viernes", "08:00", "08:15"));

        assertThat(estado(diaInvalido)).isEqualTo(400);
        assertThat(mensaje(diaInvalido)).isEqualTo("El día de la semana no es válido: usa un día de lunes a domingo");
        assertThat(estado(corta)).isEqualTo(400);
        assertThat(mensaje(corta)).isEqualTo("La jornada debe durar al menos 30 minutos");
        JornadaLaboral guardada = jornadaRepository.findById(id).orElseThrow();
        assertThat(guardada.getDiaSemana()).isEqualTo("Viernes");
        assertThat(guardada.getHoraFin()).isEqualTo(LocalTime.of(12, 0));
    }

    // ================= utilidades

    private MvcResult crear(String dia, String inicio, String fin) throws Exception {
        return como(post("/api/jornadas"), jornada(dia, inicio, fin));
    }

    private String jornada(String dia, String inicio, String fin) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "diaSemana", dia, "horaInicio", inicio, "horaFin", fin, "estudianteId", estudiante.getId()));
    }

    private MvcResult como(MockHttpServletRequestBuilder peticion, String cuerpo) throws Exception {
        return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    private int estado(MvcResult resultado) {
        return resultado.getResponse().getStatus();
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String mensaje(MvcResult resultado) throws Exception {
        return json(resultado).get("message").asText();
    }
}
