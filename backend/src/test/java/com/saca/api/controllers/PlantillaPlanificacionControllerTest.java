package com.saca.api.controllers;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.dto.request.FranjaPlantillaRequest;
import com.saca.api.dto.request.PlantillaPlanificacionRequest;
import com.saca.api.entity.Administrador;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.entity.PerfilPlantillaPlanificacion;
import com.saca.api.entity.TipoBloquePlantilla;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.JornadaLaboralRepository;
import com.saca.api.service.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlantillaPlanificacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private JornadaLaboralRepository jornadaLaboralRepository;

    private String tokenAdmin;
    private String tokenEstudiante;
    private String tokenEstudianteConJornada;

    @BeforeEach
    void prepararUsuarios() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Administrador admin = new Administrador();
        admin.setNombre("Admin plantillas");
        admin.setCorreo("admin.plantillas." + sufijo + "@ues.edu.sv");
        admin.setContrasenia("hash");
        tokenAdmin = tokenService.emitir(administradorRepository.save(admin)).valor();

        Estudiante estudiante = crearEstudiante("SP", sufijo);
        tokenEstudiante = tokenService.emitir(estudiante).valor();

        Estudiante estudianteConJornada = crearEstudiante("CP", sufijo);
        jornadaLaboralRepository.save(new JornadaLaboral("LUNES",
                LocalTime.of(9, 0), LocalTime.of(17, 0), estudianteConJornada.getId()));
        tokenEstudianteConJornada = tokenService.emitir(estudianteConJornada).valor();
    }

    @Test
    void administraPlantillasYSeleccionaPorJornadaLaboral() throws Exception {
        JsonNode sinJornada = crearPlantilla("Sin jornada", PerfilPlantillaPlanificacion.SIN_JORNADA_LABORAL,
                "ESTUDIO");
        JsonNode conJornada = crearPlantilla("Con jornada", PerfilPlantillaPlanificacion.CON_JORNADA_LABORAL,
                "DESCANSO");

        JsonNode seleccionSinJornada = objectMapper.readTree(mockMvc.perform(
                        get("/api/plantillas-planificacion/vigente")
                                .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(seleccionSinJornada.get("id").asLong()).isEqualTo(sinJornada.get("id").asLong());
        assertThat(seleccionSinJornada.get("franjas").get(0).get("tipoBloque").asText()).isEqualTo("ESTUDIO");

        JsonNode seleccionConJornada = objectMapper.readTree(mockMvc.perform(
                        get("/api/plantillas-planificacion/vigente")
                                .header("Authorization", "Bearer " + tokenEstudianteConJornada))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(seleccionConJornada.get("id").asLong()).isEqualTo(conJornada.get("id").asLong());

        PlantillaPlanificacionRequest actualizada = request("Sin jornada actualizada",
                PerfilPlantillaPlanificacion.SIN_JORNADA_LABORAL, true, "ESTUDIO");
        JsonNode resultadoEdicion = objectMapper.readTree(mockMvc.perform(
                        put("/api/admin/plantillas-planificacion/" + sinJornada.get("id").asLong())
                                .header("Authorization", "Bearer " + tokenAdmin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(actualizada)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(resultadoEdicion.get("nombre").asText()).isEqualTo("Sin jornada actualizada");

        JsonNode reemplazoActivo = crearPlantilla("Sin jornada nueva",
                PerfilPlantillaPlanificacion.SIN_JORNADA_LABORAL, "ESTUDIO");
        JsonNode plantillasAdmin = objectMapper.readTree(mockMvc.perform(
                        get("/api/admin/plantillas-planificacion")
                                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode plantillaAnterior = null;
        JsonNode plantillaNueva = null;
        for (JsonNode plantilla : plantillasAdmin) {
            if (plantilla.get("id").asLong() == sinJornada.get("id").asLong()) {
                plantillaAnterior = plantilla;
            }
            if (plantilla.get("id").asLong() == reemplazoActivo.get("id").asLong()) {
                plantillaNueva = plantilla;
            }
        }
        assertThat(plantillaAnterior).isNotNull();
        assertThat(plantillaAnterior.get("activa").asBoolean()).isFalse();
        assertThat(plantillaNueva).isNotNull();
        assertThat(plantillaNueva.get("activa").asBoolean()).isTrue();
        JsonNode seleccionActual = objectMapper.readTree(mockMvc.perform(
                        get("/api/plantillas-planificacion/vigente")
                                .header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(seleccionActual.get("id").asLong()).isEqualTo(reemplazoActivo.get("id").asLong());
    }

    @Test
    void rechazaFranjasSuperpuestas() throws Exception {
        PlantillaPlanificacionRequest request = new PlantillaPlanificacionRequest("Solapada",
                PerfilPlantillaPlanificacion.SIN_JORNADA_LABORAL, true,
                List.of(
                        franja("ESTUDIO", "08:00", "10:00"),
                        franja("DESCANSO", "09:00", "11:00")));

        mockMvc.perform(post("/api/admin/plantillas-planificacion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private JsonNode crearPlantilla(String nombre, PerfilPlantillaPlanificacion perfil, String tipo)
            throws Exception {
        PlantillaPlanificacionRequest request = request(nombre, perfil, true, tipo);
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/plantillas-planificacion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private PlantillaPlanificacionRequest request(
            String nombre, PerfilPlantillaPlanificacion perfil, boolean activa, String tipo) {
        return new PlantillaPlanificacionRequest(nombre, perfil, activa,
                List.of(franja(tipo, "08:00", "10:00")));
    }

    private FranjaPlantillaRequest franja(String tipo, String desde, String hasta) {
        return new FranjaPlantillaRequest(DiaSemana.LUNES, LocalTime.parse(desde), LocalTime.parse(hasta),
                TipoBloquePlantilla.valueOf(tipo));
    }

    private Estudiante crearEstudiante(String carnetPrefijo, String sufijo) {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Estudiante plantilla");
        estudiante.setCarnet(carnetPrefijo + sufijo.toUpperCase());
        estudiante.setCorreoInstitucional(carnetPrefijo.toLowerCase() + "." + sufijo + "@ues.edu.sv");
        estudiante.setContrasenia("hash");
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        return estudianteRepository.save(estudiante);
    }
}
