package com.saca.api.controllers;

import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.EstudianteRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Recorre controlador, servicio, repositorio (H2) y GlobalExceptionHandler reales, con los filtros
// de seguridad activos y el token del estudiante propietario de los bloques
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisponibilidadSemanalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DisponibilidadSemanalRepository repository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;

    @Autowired
    private TokenService tokenService;

    private DisponibilidadSemanal existente;
    private Estudiante estudiante;
    private CicloAcademico ciclo;
    private CicloAcademico otroCiclo;
    private String token;

    @BeforeEach
    void prepararDatos() {
        repository.deleteAll();
        estudiante = crearEstudiante();
        ciclo = crearCiclo(estudiante, "Ciclo I");
        otroCiclo = crearCiclo(estudiante, "Ciclo II");
        token = tokenService.emitir(estudiante).valor();
        existente = repository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("08:00"), LocalTime.parse("10:00"), "CLASE", ciclo.getId(), estudiante.getId()));
    }

    private Estudiante crearEstudiante() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante nuevo = new Estudiante();
        nuevo.setNombre("Estudiante Disponibilidad");
        nuevo.setCarnet("DS" + unico);
        nuevo.setCorreoInstitucional("disp." + unico + "@ues.edu.sv");
        nuevo.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        nuevo.setCuentaActiva(true);
        nuevo.setIntentosFallidos(0);
        return estudianteRepository.save(nuevo);
    }

    private CicloAcademico crearCiclo(Estudiante propietario, String nombre) {
        CicloAcademico nuevo = new CicloAcademico();
        nuevo.setEstudiante(propietario);
        nuevo.setNombre(nombre);
        nuevo.setAnio(2026);
        nuevo.setFechaInicio(LocalDate.of(2026, 2, 1));
        nuevo.setFechaFin(LocalDate.of(2026, 6, 30));
        nuevo.setEstado(EstadoCiclo.ACTIVO);
        return cicloAcademicoRepository.save(nuevo);
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String cuerpo(String dia, String inicio, String fin, CicloAcademico cicloDelBloque) {
        return """
                {"diaSemana":"%s","horaInicio":"%s","horaFin":"%s","tipoBloque":"CLASE","cicloId":%d,"estudianteId":%d}
                """.formatted(dia, inicio, fin, cicloDelBloque.getId(), estudiante.getId());
    }

    @Test
    void crearConCruceDevuelve400ConElMotivo() throws Exception {
        mockMvc.perform(conToken(post("/api/disponibilidades"), cuerpo("LUNES", "09:00", "11:00", ciclo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void crearDuplicadoDevuelve400ConElMotivo() throws Exception {
        mockMvc.perform(conToken(post("/api/disponibilidades"), cuerpo("LUNES", "08:00", "10:00", ciclo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe un bloque de disponibilidad registrado el LUNES de 08:00 a 10:00"));
    }

    @Test
    void crearBloqueConsecutivoSeAcepta() throws Exception {
        mockMvc.perform(conToken(post("/api/disponibilidades"), cuerpo("LUNES", "10:00", "11:00", ciclo)))
                .andExpect(status().isCreated());

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void crearEnOtroDiaUOtroCicloSeAcepta() throws Exception {
        mockMvc.perform(conToken(post("/api/disponibilidades"), cuerpo("MARTES", "08:00", "10:00", ciclo)))
                .andExpect(status().isCreated());
        mockMvc.perform(conToken(post("/api/disponibilidades"), cuerpo("LUNES", "08:00", "10:00", otroCiclo)))
                .andExpect(status().isCreated());

        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void editarElPropioBloqueSinCrucesSeAcepta() throws Exception {
        mockMvc.perform(conToken(put("/api/disponibilidades/{id}", existente.getId()), cuerpo("LUNES", "08:30", "10:30", ciclo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("08:30:00"));
    }

    @Test
    void editarConCruceDevuelve400YNoModificaElBloque() throws Exception {
        DisponibilidadSemanal otro = repository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("11:00"), LocalTime.parse("12:00"), "ESTUDIO", ciclo.getId(), estudiante.getId()));

        mockMvc.perform(conToken(put("/api/disponibilidades/{id}", otro.getId()), cuerpo("LUNES", "09:30", "11:30", ciclo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El bloque se cruza con otro registrado el LUNES de 08:00 a 10:00"));

        assertThat(repository.findById(otro.getId()).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.parse("11:00"));
    }

    @Test
    void editarAUnBloqueConsecutivoSeAcepta() throws Exception {
        DisponibilidadSemanal otro = repository.save(new DisponibilidadSemanal(
                "LUNES", LocalTime.parse("11:00"), LocalTime.parse("12:00"), "ESTUDIO", ciclo.getId(), estudiante.getId()));

        mockMvc.perform(conToken(put("/api/disponibilidades/{id}", otro.getId()), cuerpo("LUNES", "10:00", "11:00", ciclo)))
                .andExpect(status().isOk());
    }

    @Test
    void intervaloInvalidoSigueDevolviendo400ConSuMensaje() throws Exception {
        mockMvc.perform(conToken(post("/api/disponibilidades"), cuerpo("MARTES", "09:00", "08:00", ciclo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La hora de inicio debe ser menor que la hora de fin"));
        mockMvc.perform(conToken(put("/api/disponibilidades/{id}", existente.getId()), cuerpo("LUNES", "09:00", "09:00", ciclo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La hora de inicio debe ser menor que la hora de fin"));
    }
}
