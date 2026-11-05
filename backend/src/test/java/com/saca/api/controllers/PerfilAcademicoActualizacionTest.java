package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PerfilAcademicoRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Nota mínima de aprobación en el perfil académico (sin valor por defecto) y actualización
// parcial del perfil: un campo omitido se conserva y uno enviado como null se borra
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilAcademicoActualizacionTest {

    private static final String PERFIL_OK = "Perfil académico actualizado correctamente";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private PerfilAcademicoRepository perfilAcademicoRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    private Estudiante estudiante;
    private String token;

    @BeforeEach
    void prepararDatos() {
        String unico = UUID.randomUUID().toString().substring(0, 8);
        estudiante = new Estudiante();
        estudiante.setNombre("Estudiante Nota");
        estudiante.setCarnet("NM" + unico);
        estudiante.setCorreoInstitucional("nota." + unico + "@ues.edu.sv");
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante = estudianteRepository.save(estudiante);
        token = tokenService.emitir(estudiante).valor();
    }

    private MvcResult guardar(String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/perfil-academico")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo)).andReturn();
    }

    private JsonNode consultar() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/perfil-academico").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    private String mensajeDeError(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString()).get("message").asText();
    }

    @Test
    void seGuardaYSeRecuperaLaNotaMinima() throws Exception {
        MvcResult guardado = guardar("""
                {"carrera":"Ingeniería de Sistemas","facultad":"FIA","notaMinimaAprobacion":6}
                """);

        assertThat(guardado.getResponse().getStatus()).isEqualTo(200);
        assertThat(guardado.getResponse().getContentAsString()).isEqualTo(PERFIL_OK);
        assertThat(consultar().get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("6");
    }

    @Test
    void sinConfigurarLaNotaMinimaEsNull() throws Exception {
        guardar("""
                {"carrera":"Ingeniería de Sistemas","facultad":"FIA"}
                """);

        assertThat(consultar().get("notaMinimaAprobacion").isNull()).isTrue();
    }

    @Test
    void seAdmitenDecimalesYLosLimitesCeroYDiez() throws Exception {
        assertThat(guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":6.75}").getResponse().getStatus()).isEqualTo(200);
        assertThat(consultar().get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("6.75");

        assertThat(guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":0}").getResponse().getStatus()).isEqualTo(200);
        assertThat(guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":10}").getResponse().getStatus()).isEqualTo(200);
        assertThat(consultar().get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void seRechazanValoresMenoresQueCeroOMayoresQueDiez() throws Exception {
        guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":6}");

        MvcResult negativa = guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":-0.5}");
        MvcResult mayor = guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":10.01}");

        assertThat(negativa.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensajeDeError(negativa)).contains("La nota mínima de aprobación no puede ser menor a 0");
        assertThat(mayor.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensajeDeError(mayor)).contains("La nota mínima de aprobación no puede ser mayor a 10");
        assertThat(consultar().get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("6");
    }

    @Test
    void seRechazanMasDeDosDecimales() throws Exception {
        MvcResult resultado = guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":6.555}");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(mensajeDeError(resultado)).contains("como máximo dos decimales");
    }

    @Test
    void unaActualizacionQueOmiteElCampoNoBorraLaNotaMinima() throws Exception {
        guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":7.5}");

        MvcResult parcial = guardar("{\"carrera\":\"Otra carrera\",\"facultad\":\"F\"}");

        assertThat(parcial.getResponse().getStatus()).isEqualTo(200);
        JsonNode perfil = consultar();
        assertThat(perfil.get("carrera").asText()).isEqualTo("Otra carrera");
        assertThat(perfil.get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("7.5");
    }

    @Test
    void enviarNullExplicitamenteQuitaLaConfiguracion() throws Exception {
        guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":7.5}");

        guardar("{\"carrera\":\"C\",\"facultad\":\"F\",\"notaMinimaAprobacion\":null}");

        assertThat(consultar().get("notaMinimaAprobacion").isNull()).isTrue();
        assertThat(perfilAcademicoRepository.findByEstudianteId(estudiante.getId()).orElseThrow().getNotaMinimaAprobacion()).isNull();
    }

    // ================= Actualización parcial del resto del perfil

    private static final String PERFIL_COMPLETO = """
            {"carrera":"Ingeniería de Sistemas","facultad":"FIA","fechaIngreso":"2023-03-01",
             "planEstudio":"Plan 2018","notaMinimaAprobacion":6}
            """;

    @Test
    void losCamposOmitidosConservanSuValor() throws Exception {
        guardar(PERFIL_COMPLETO);

        MvcResult parcial = guardar("{\"carrera\":\"Ingeniería Industrial\"}");

        assertThat(parcial.getResponse().getContentAsString()).isEqualTo(PERFIL_OK);
        JsonNode perfil = consultar();
        assertThat(perfil.get("carrera").asText()).isEqualTo("Ingeniería Industrial");
        assertThat(perfil.get("facultad").asText()).isEqualTo("FIA");
        assertThat(perfil.get("fechaIngreso").asText()).isEqualTo("2023-03-01");
        assertThat(perfil.get("planEstudio").asText()).isEqualTo("Plan 2018");
        assertThat(perfil.get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("6");
    }

    @Test
    void unCampoEnviadoComoNullSeBorraSinAfectarALosDemas() throws Exception {
        guardar(PERFIL_COMPLETO);

        guardar("{\"facultad\":null,\"fechaIngreso\":null,\"planEstudio\":null}");

        JsonNode perfil = consultar();
        assertThat(perfil.get("facultad").isNull()).isTrue();
        assertThat(perfil.get("fechaIngreso").isNull()).isTrue();
        assertThat(perfil.get("planEstudio").isNull()).isTrue();
        assertThat(perfil.get("carrera").asText()).isEqualTo("Ingeniería de Sistemas");
        assertThat(perfil.get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("6");
    }

    // Así guarda hoy la pantalla «Perfil académico»: siempre envía todos los campos
    @Test
    void unaActualizacionCompletaReemplazaTodosLosCampos() throws Exception {
        guardar(PERFIL_COMPLETO);

        guardar("""
                {"carrera":"Arquitectura","facultad":"FIA","fechaIngreso":"2024-02-15",
                 "planEstudio":"","notaMinimaAprobacion":7}
                """);

        JsonNode perfil = consultar();
        assertThat(perfil.get("carrera").asText()).isEqualTo("Arquitectura");
        assertThat(perfil.get("fechaIngreso").asText()).isEqualTo("2024-02-15");
        assertThat(perfil.get("planEstudio").asText()).isEmpty();
        assertThat(perfil.get("notaMinimaAprobacion").decimalValue()).isEqualByComparingTo("7");
    }

    @Test
    void unPerfilNuevoCreadoConUnaPeticionParcialSoloTieneLosCamposEnviados() throws Exception {
        MvcResult creado = guardar("{\"carrera\":\"Ingeniería de Sistemas\"}");

        assertThat(creado.getResponse().getContentAsString()).isEqualTo(PERFIL_OK);
        JsonNode perfil = consultar();
        assertThat(perfil.get("carrera").asText()).isEqualTo("Ingeniería de Sistemas");
        assertThat(perfil.get("facultad").isNull()).isTrue();
        assertThat(perfil.get("notaMinimaAprobacion").isNull()).isTrue();
    }
}
