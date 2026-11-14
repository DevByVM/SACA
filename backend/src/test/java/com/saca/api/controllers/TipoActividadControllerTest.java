package com.saca.api.controllers;

import java.time.LocalDate;
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
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.Administrador;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.TipoActividad;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import com.saca.api.repository.TipoActividadRepository;
import com.saca.api.service.TokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TipoActividadControllerTest {

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
    private CicloAcademicoRepository cicloRepository;

    @Autowired
    private MateriaInscritaRepository materiaRepository;

    @Autowired
    private TipoActividadRepository tipoRepository;

    @Autowired
    private ActividadAcademicaRepository actividadRepository;

    @Autowired
    private PreferenciaNotificacionRepository preferenciaRepository;

    private String tokenAdmin;
    private String tokenEstudiante;
    private Estudiante estudiante;

    @BeforeEach
    void prepararUsuarios() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);

        Administrador admin = new Administrador();
        admin.setNombre("Admin tipo");
        admin.setCorreo("admin.tipo." + sufijo + "@ues.edu.sv");
        admin.setContrasenia("hash");
        tokenAdmin = tokenService.emitir(administradorRepository.save(admin)).valor();

        estudiante = new Estudiante();
        estudiante.setNombre("Estudiante tipo");
        estudiante.setCarnet("TA" + sufijo.toUpperCase());
        estudiante.setCorreoInstitucional("est.tipo." + sufijo + "@ues.edu.sv");
        estudiante.setContrasenia("hash");
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante = estudianteRepository.save(estudiante);
        tokenEstudiante = tokenService.emitir(estudiante).valor();
    }

    @Test
    void administraTiposYLosExponeAEstudiantes() throws Exception {
        String nombre = "Tipo dinámico " + UUID.randomUUID();
        JsonNode creado = objectMapper.readTree(mockMvc.perform(post("/api/admin/tipos-actividad")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("nombre", nombre))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString());
        int id = creado.get("id").asInt();

        mockMvc.perform(get("/api/tipos-actividad").header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$[?(@.id == " + id + ")].nombre").value(org.hamcrest.Matchers.contains(nombre)));

        mockMvc.perform(post("/api/admin/tipos-actividad")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("nombre", nombre.toUpperCase()))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        String otroNombre = "Otro tipo " + UUID.randomUUID();
        JsonNode otro = objectMapper.readTree(mockMvc.perform(post("/api/admin/tipos-actividad")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("nombre", otroNombre))))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(put("/api/admin/tipos-actividad/" + id)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("nombre", otroNombre))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        mockMvc.perform(delete("/api/admin/tipos-actividad/" + id)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertThat(tipoRepository.existsById(id)).isFalse();
        assertThat(otro.get("id").asInt()).isNotEqualTo(id);
    }

    @Test
    void impideEliminarTiposReferenciadosPorActividadesOPreferencias() throws Exception {
        TipoActividad tipo = crearTipo("Referenciado " + UUID.randomUUID());

        PreferenciaNotificacion preferencia = new PreferenciaNotificacion();
        preferencia.setEstudiante(estudiante);
        preferencia.setTiposActividadHabilitados(java.util.Set.of(tipo));
        preferenciaRepository.saveAndFlush(preferencia);

        mockMvc.perform(delete("/api/admin/tipos-actividad/" + tipo.getIdTipoActividad())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        preferencia.getTiposActividadHabilitados().clear();
        preferenciaRepository.saveAndFlush(preferencia);

        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setEstudiante(estudiante);
        ciclo.setNombre("Ciclo de tipo");
        ciclo.setAnio(LocalDate.now().getYear());
        ciclo.setFechaInicio(LocalDate.now().minusMonths(1));
        ciclo.setFechaFin(LocalDate.now().plusMonths(1));
        ciclo.setEstado(EstadoCiclo.ACTIVO);
        MateriaInscrita materia = new MateriaInscrita();
        materia.setCicloAcademico(cicloRepository.saveAndFlush(ciclo));
        materia.setNombre("Materia tipo");
        materia.setCodigo("TP" + UUID.randomUUID().toString().substring(0, 5));
        materia.setGrupoTeorico("01");
        materia = materiaRepository.saveAndFlush(materia);

        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setNombre("Actividad con tipo");
        actividad.setTipoActividad(tipo);
        actividad.setMateriaInscrita(materia);
        actividad.setPorcentajeEvaluacion(10);
        actividad.setTiempoEstimadoHoras(1);
        actividadRepository.saveAndFlush(actividad);

        mockMvc.perform(delete("/api/admin/tipos-actividad/" + tipo.getIdTipoActividad())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    private TipoActividad crearTipo(String nombre) {
        TipoActividad tipo = new TipoActividad();
        tipo.setNombre(nombre);
        tipo.setActivo(true);
        return tipoRepository.saveAndFlush(tipo);
    }
}
