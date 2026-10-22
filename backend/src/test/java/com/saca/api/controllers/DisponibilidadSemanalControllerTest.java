package com.saca.api.controllers;

import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Recorre controlador, servicio, repositorio (H2) y GlobalExceptionHandler reales
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DisponibilidadSemanalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisponibilidadSemanalRepository repository;

    private DisponibilidadSemanal existente;

    @BeforeEach
    void prepararDatos() {
        repository.deleteAll();
        existente = repository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("08:00"), LocalTime.parse("10:00"), "CLASE", 3L, 5L));
    }

    private static String cuerpo(String dia, String inicio, String fin, long cicloId) {
        return """
                {"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","tipoBloque":"CLASE","cicloId":%d,"estudianteId":5}
                """.formatted(dia, inicio, fin, cicloId);
    }

    @Test
    void crearConCruceDevuelve400ConElMotivo() throws Exception {
        mockMvc.perform(post("/api/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "09:00", "11:00", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void crearDuplicadoDevuelve400ConElMotivo() throws Exception {
        mockMvc.perform(post("/api/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "08:00", "10:00", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe un bloque de disponibilidad registrado el LUNES de 08:00 a 10:00"));
    }

    @Test
    void crearBloqueConsecutivoSeAcepta() throws Exception {
        mockMvc.perform(post("/api/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "10:00", "11:00", 3)))
                .andExpect(status().isCreated());

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void crearEnOtroDiaUOtroCicloSeAcepta() throws Exception {
        mockMvc.perform(post("/api/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("MARTES", "08:00", "10:00", 3)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "08:00", "10:00", 4)))
                .andExpect(status().isCreated());

        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void editarElPropioBloqueSinCrucesSeAcepta() throws Exception {
        mockMvc.perform(put("/api/disponibilidades/{id}", existente.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "08:30", "10:30", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("08:30:00"));
    }

    @Test
    void editarConCruceDevuelve400YNoModificaElBloque() throws Exception {
        DisponibilidadSemanal otro = repository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("11:00"), LocalTime.parse("12:00"), "ESTUDIO", 3L, 5L));

        mockMvc.perform(put("/api/disponibilidades/{id}", otro.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "09:30", "11:30", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00"));

        assertThat(repository.findById(otro.getId()).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.parse("11:00"));
    }

    @Test
    void editarAUnBloqueConsecutivoSeAcepta() throws Exception {
        DisponibilidadSemanal otro = repository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("11:00"), LocalTime.parse("12:00"), "ESTUDIO", 3L, 5L));

        mockMvc.perform(put("/api/disponibilidades/{id}", otro.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "10:00", "11:00", 3)))
                .andExpect(status().isOk());
    }

    @Test
    void intervaloInvalidoSigueDevolviendo400ConSuMensaje() throws Exception {
        mockMvc.perform(post("/api/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("MARTES", "09:00", "08:00", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La hora de inicio debe ser menor que la hora de fin"));
        mockMvc.perform(put("/api/disponibilidades/{id}", existente.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("LUNES", "09:00", "09:00", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La hora de inicio debe ser menor que la hora de fin"));
    }
}
