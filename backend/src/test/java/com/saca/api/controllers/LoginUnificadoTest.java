package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.ContraseniaPendienteService;
import com.saca.api.service.VerificadorContrasenias;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Todos los inicios de sesión fallidos responden exactamente igual y hacen una sola
// comparación BCrypt (real o ficticia), para no revelar si la cuenta existe ni en qué estado está
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LoginUnificadoTest {

    private static final String CLAVE = "Segura123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private ContraseniaPendienteService contraseniaPendiente;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private VerificadorContrasenias verificador;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Estudiante crear(boolean activa, Timestamp bloqueadoHasta, EstadoCorreo estado, String hash) {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Prueba Login");
        estudiante.setCarnet(CarnetsDePrueba.nuevo("LU"));
        estudiante.setCorreoInstitucional("login." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv");
        estudiante.setContrasenia(hash);
        estudiante.setCuentaActiva(activa);
        estudiante.setIntentosFallidos(0);
        estudiante.setBloqueadoHasta(bloqueadoHasta);
        estudiante.setEstadoCorreo(estado);
        return repository.save(estudiante);
    }

    private Estudiante activa(String hash) {
        return crear(true, null, null, hash);
    }

    // Con contrasenia null el campo no se envía
    private MockHttpServletResponse login(String correo, String contrasenia) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("correoInstitucional", correo));
        if (contrasenia != null) {
            cuerpo.put("contrasenia", contrasenia);
        }
        clearInvocations(verificador);
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo))).andReturn().getResponse();
    }

    private void assertComparacionFicticia() {
        verify(verificador, times(1)).compararConFicticio(any());
        verify(verificador, never()).coincide(any(), any());
    }

    private void assertComparacionReal() {
        verify(verificador, times(1)).coincide(any(), any());
        verify(verificador, never()).compararConFicticio(any());
    }

    @Test
    void todosLosFallosRespondenExactamenteIgualYSinToken() throws Exception {
        Map<String, MockHttpServletResponse> respuestas = new LinkedHashMap<>();
        respuestas.put("cuenta inexistente", login("no.existe." + UUID.randomUUID() + "@ues.edu.sv", CLAVE));
        respuestas.put("contraseña incorrecta", login(activa(passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), "Incorrecta1"));
        respuestas.put("cuenta inactiva", login(crear(false, null, null, passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), CLAVE));
        respuestas.put("cuenta bloqueada", login(crear(true, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)), null,
                passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), CLAVE));
        respuestas.put("pendiente con contraseña aleatoria", login(crear(true, null, EstadoCorreo.PENDIENTE,
                contraseniaPendiente.generarHash()).getCorreoInstitucional(), CLAVE));
        respuestas.put("pendiente con contraseña conocida", login(crear(true, null, EstadoCorreo.PENDIENTE,
                passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), CLAVE));
        respuestas.put("contraseña ausente", login(activa(passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), null));

        String referencia = respuestas.get("cuenta inexistente").getContentAsString();
        JsonNode cuerpo = objectMapper.readTree(referencia);
        assertThat(cuerpo.get("autenticado").asBoolean()).isFalse();
        assertThat(cuerpo.get("mensaje").asText()).isEqualTo("Credenciales incorrectas");
        assertThat(cuerpo.get("token").isNull()).isTrue();

        respuestas.forEach((escenario, respuesta) -> {
            assertThat(respuesta.getStatus()).as(escenario).isEqualTo(200);
            try {
                assertThat(respuesta.getContentAsString()).as(escenario).isEqualTo(referencia);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void laComparacionFicticiaSeHaceCuandoNoHayContraseniaQueComparar() throws Exception {
        login("no.existe." + UUID.randomUUID() + "@ues.edu.sv", CLAVE);
        assertComparacionFicticia();

        login(crear(false, null, null, passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), CLAVE);
        assertComparacionFicticia();

        login(crear(true, Timestamp.valueOf(LocalDateTime.now().plusMinutes(5)), null,
                passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), CLAVE);
        assertComparacionFicticia();
    }

    @Test
    void conUnHashGuardadoSeHaceUnaSolaComparacionReal() throws Exception {
        login(activa(passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), "Incorrecta1");
        assertComparacionReal();

        // La contraseña aleatoria de una cuenta pendiente es un BCrypt válido: se compara como cualquier otra
        login(crear(true, null, EstadoCorreo.PENDIENTE, contraseniaPendiente.generarHash()).getCorreoInstitucional(), CLAVE);
        assertComparacionReal();

        login(activa(passwordEncoder.encode(CLAVE)).getCorreoInstitucional(), CLAVE);
        assertComparacionReal();
    }

    @Test
    void unValorGuardadoQueNoEsBCryptTambienCuestaUnaComparacion() throws Exception {
        Estudiante conValorAntiguo = activa("valor-no-bcrypt");

        MockHttpServletResponse respuesta = login(conValorAntiguo.getCorreoInstitucional(), "valor-no-bcrypt");

        assertThat(objectMapper.readTree(respuesta.getContentAsString()).get("mensaje").asText()).isEqualTo("Credenciales incorrectas");
        verify(verificador, times(1)).coincide(any(), any());
        verify(verificador, times(1)).compararConFicticio(any());
    }

    @Test
    void lasCuentasAnterioresYConfirmadasSiguenEntrando() throws Exception {
        for (EstadoCorreo estado : new EstadoCorreo[] {null, EstadoCorreo.CONFIRMADO}) {
            Estudiante estudiante = crear(true, null, estado, passwordEncoder.encode(CLAVE));

            JsonNode cuerpo = objectMapper.readTree(login(estudiante.getCorreoInstitucional(), CLAVE).getContentAsString());

            assertThat(cuerpo.get("autenticado").asBoolean()).as(String.valueOf(estado)).isTrue();
            assertThat(cuerpo.get("token").asText()).as(String.valueOf(estado)).isNotBlank();
        }
    }

    @Test
    void losFallosNoRegistranContraseniasNiHashes(CapturedOutput salida) throws Exception {
        String hash = passwordEncoder.encode(CLAVE);
        Estudiante estudiante = activa(hash);

        login(estudiante.getCorreoInstitucional(), "Incorrecta1");
        login("no.existe." + UUID.randomUUID() + "@ues.edu.sv", "OtraClave99");

        assertThat(salida.getAll()).doesNotContain("Incorrecta1").doesNotContain("OtraClave99").doesNotContain(hash);
    }
}
