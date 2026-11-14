package com.saca.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.Administrador;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.ParametrosCarga;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.ParametrosCargaRepository;
import com.saca.api.service.ParametrosCargaService;
import com.saca.api.service.TokenService;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParametrosCargaAdminTest {

    private static final String RUTA = "/api/admin/parametros-carga";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ParametrosCargaRepository parametros;
    @Autowired private ParametrosCargaService service;
    @Autowired private AdministradorRepository administradores;
    @Autowired private EstudianteRepository estudiantes;
    @Autowired private TokenService tokens;
    @Autowired private EntityManager entityManager;
    private String tokenAdmin;
    private String tokenEstudiante;

    @BeforeEach
    void preparar() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String hash = new BCryptPasswordEncoder().encode("PruebaLocal37123");
        Administrador admin = new Administrador();
        admin.setCorreo("parametros" + sufijo + "@ues.edu.sv");
        admin.setNombre("Admin Prueba");
        admin.setContrasenia(hash);
        tokenAdmin = tokens.emitir(administradores.save(admin)).valor();
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Estudiante Prueba");
        estudiante.setCorreoInstitucional("estudiante" + sufijo + "@ues.edu.sv");
        estudiante.setContrasenia(hash);
        estudiante.setCuentaActiva(true);
        tokenEstudiante = tokens.emitir(estudiantes.save(estudiante)).valor();
    }

    private JsonNode consultar() throws Exception {
        return mapper.readTree(mvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void inicializaUnaConfiguracionGlobalConVeinteYTreintaYCinco() throws Exception {
        assertThat(parametros.existsById(ParametrosCarga.CLAVE_GLOBAL)).isTrue();
        assertThat(parametros.count()).isEqualTo(1);
        JsonNode respuesta = consultar();
        assertThat(respuesta.get("umbralElevada").asDouble()).isEqualTo(20);
        assertThat(respuesta.get("umbralCritica").asDouble()).isEqualTo(35);
    }

    @Test
    void guardaDecimalesYLosRecuperaDesdeLaBaseSinCrearOtraConfiguracion() throws Exception {
        mvc.perform(put(RUTA).header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"umbralElevada\":12.5,\"umbralCritica\":28.75}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        JsonNode respuesta = consultar();
        assertThat(respuesta.get("umbralElevada").asDouble()).isEqualTo(12.5);
        assertThat(respuesta.get("umbralCritica").asDouble()).isEqualTo(28.75);
        assertThat(parametros.count()).isEqualTo(1);
        service.run(null);
        entityManager.flush();
        entityManager.clear();
        assertThat(consultar().get("umbralElevada").asDouble()).isEqualTo(12.5);
        assertThat(consultar().get("umbralCritica").asDouble()).isEqualTo(28.75);
    }

    @Test
    void rechazaValoresInvalidosSinCambiarLosGuardados() throws Exception {
        for (String cuerpo : List.of("{}", "{\"umbralElevada\":20}",
                "{\"umbralElevada\":null,\"umbralCritica\":35}",
                "{\"umbralElevada\":0,\"umbralCritica\":35}",
                "{\"umbralElevada\":-1,\"umbralCritica\":35}",
                "{\"umbralElevada\":20,\"umbralCritica\":20}",
                "{\"umbralElevada\":36,\"umbralCritica\":35}",
                "{\"umbralElevada\":20,\"umbralCritica\":0}",
                "{\"umbralElevada\":20,\"umbralCritica\":1e309}")) {
            mvc.perform(put(RUTA).header("Authorization", "Bearer " + tokenAdmin)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        }
        assertThat(consultar().get("umbralElevada").asDouble()).isEqualTo(20);
        assertThat(consultar().get("umbralCritica").asDouble()).isEqualTo(35);
    }

    @Test
    void soloAdministracionPuedeConsultarYGuardar() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"umbralElevada\":10,\"umbralCritica\":30}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenEstudiante))
                .andExpect(status().isForbidden());
        mvc.perform(put(RUTA).header("Authorization", "Bearer " + tokenEstudiante)
                .contentType(MediaType.APPLICATION_JSON).content("{\"umbralElevada\":10,\"umbralCritica\":30}"))
                .andExpect(status().isForbidden());
        assertThat(consultar().get("umbralElevada").asDouble()).isEqualTo(20);
    }
}
