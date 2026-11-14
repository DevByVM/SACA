package com.saca.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.saca.api.TipoActividadPrueba.crear;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

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
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.Administrador;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.AdministradorRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.service.TokenService;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParametrosCargaIntegracionTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EstudianteRepository estudiantes;
    @Autowired private AdministradorRepository administradores;
    @Autowired private CicloAcademicoRepository ciclos;
    @Autowired private MateriaInscritaRepository materias;
    @Autowired private ActividadAcademicaRepository actividades;
    @Autowired private TokenService tokens;
    @Autowired private Clock reloj;
    @Autowired private EntityManager entityManager;

    private String crearEstudiante(String nombre, double horas) {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre(nombre);
        estudiante.setCorreoInstitucional(unico + "@ues.edu.sv");
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("PruebaLocal37123"));
        estudiante.setCuentaActiva(true);
        estudiantes.save(estudiante);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        CicloAcademico ciclo = new CicloAcademico();
        ciclo.setEstudiante(estudiante);
        ciclo.setNombre("Ciclo de prueba");
        ciclo.setAnio(ahora.getYear());
        ciclo.setFechaInicio(ahora.toLocalDate().minusDays(20));
        ciclo.setFechaFin(ahora.toLocalDate().plusDays(21));
        ciclo.setEstado(EstadoCiclo.ACTIVO);
        ciclos.save(ciclo);
        MateriaInscrita materia = new MateriaInscrita();
        materia.setCicloAcademico(ciclo);
        materia.setNombre("Materia " + nombre);
        materia.setCodigo(unico);
        materia.setGrupoTeorico("GT01");
        materias.save(materia);
        for (int dias : new int[] {0, 8}) {
            ActividadAcademica actividad = new ActividadAcademica();
            actividad.setNombre(nombre + " actividad " + dias);
            actividad.setMateriaInscrita(materia);
            actividad.setTipoActividad(crear(TipoActividadAcademica.PARCIAL));
            actividad.setEstado("ACTIVO");
            actividad.setTiempoEstimadoHoras(horas);
            actividad.setPorcentajeEvaluacion(5);
            actividad.setFechaInicio(ahora);
            actividad.setFechaEntrega(ahora.plusDays(dias).plusHours(1));
            actividades.save(actividad);
        }
        return tokens.emitir(estudiante).valor();
    }

    private JsonNode consultar(String ruta, String token) throws Exception {
        return mapper.readTree(mvc.perform(get(ruta).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void guardar(String token, double elevada, double critica) throws Exception {
        mvc.perform(put("/api/admin/parametros-carga").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("umbralElevada", elevada, "umbralCritica", critica))))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    private void comprobar(String token, String propietario, String ajeno, String nivel, double elevada, double critica) throws Exception {
        JsonNode semanal = consultar("/api/carga/semanal", token);
        assertThat(semanal.get("horas").get("nivel").asText()).isEqualTo(nivel);
        assertThat(semanal.get("horas").get("umbralElevada").asDouble()).isEqualTo(elevada);
        assertThat(semanal.get("horas").get("umbralCritica").asDouble()).isEqualTo(critica);
        assertThat(semanal.get("exigencia").get("nivel").asText()).isEqualTo("NORMAL");
        assertThat(semanal.toString()).contains(propietario).doesNotContain(ajeno);
        assertThat(semanal.get("motivos").get(0).asText()).contains(nivel.equals("CRITICA")
                ? String.valueOf(critica).replaceAll("\\.0$", "") : String.valueOf(elevada).replaceAll("\\.0$", ""));
        JsonNode semanas = consultar("/api/carga/semanas", token);
        assertThat(semanas.toString()).contains(propietario).doesNotContain(ajeno);
        int conActividades = 0;
        for (JsonNode semana : semanas.get("semanas")) {
            JsonNode horas = semana.get("carga").get("horas");
            assertThat(horas.get("umbralElevada").asDouble()).isEqualTo(elevada);
            assertThat(horas.get("umbralCritica").asDouble()).isEqualTo(critica);
            if (horas.get("horasPendientes").asDouble() > 0) {
                conActividades++;
                assertThat(horas.get("nivel").asText()).isEqualTo(nivel);
            }
        }
        assertThat(conActividades).isEqualTo(2);
    }

    @Test
    void siguienteConsultaAplicaElCambioGlobalADosEstudiantesYSusSemanasConLimitesInclusivos() throws Exception {
        Administrador admin = new Administrador();
        admin.setNombre("Admin Parámetros");
        admin.setCorreo(UUID.randomUUID() + "@ues.edu.sv");
        admin.setContrasenia(new BCryptPasswordEncoder().encode("PruebaLocal37123"));
        String tokenAdmin = tokens.emitir(administradores.save(admin)).valor();
        String a = crearEstudiante("EstudianteA", 12.5);
        String b = crearEstudiante("EstudianteB", 28.75);

        guardar(tokenAdmin, 12.5, 28.75);
        comprobar(a, "EstudianteA", "EstudianteB", "NORMAL", 12.5, 28.75);
        comprobar(b, "EstudianteB", "EstudianteA", "ELEVADA", 12.5, 28.75);
        guardar(tokenAdmin, 10, 20);
        comprobar(a, "EstudianteA", "EstudianteB", "ELEVADA", 10, 20);
        comprobar(b, "EstudianteB", "EstudianteA", "CRITICA", 10, 20);
    }
}
