package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.TokenActivacionRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Sin servidor SMTP (la configuración de pruebas no lo define) el registro responde 503 y
// no crea nada, y el reenvío responde lo de siempre sin tocar nada, exista o no la cuenta: ninguna respuesta
// revela si existe
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RegistroSinCorreoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenActivacionRepository tokenRepository;

    private MockHttpServletResponse registrar(String carnet) throws Exception {
        return mockMvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content("{\"carnet\":\"" + carnet + "\"}")).andReturn().getResponse();
    }

    private MockHttpServletResponse reenviar(String identificador) throws Exception {
        return mockMvc.perform(post("/api/auth/reenviar-activacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identificador\":\"" + identificador + "\"}")).andReturn().getResponse();
    }

    private Estudiante cuentaCon(EstadoCorreo estado) {
        String carnet = CarnetsDePrueba.nuevo("SC");
        Estudiante estudiante = new Estudiante();
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv");
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setEstadoCorreo(estado);
        return estudianteRepository.save(estudiante);
    }

    private void assertNoDisponible(MockHttpServletResponse respuesta) throws Exception {
        assertThat(respuesta.getStatus()).isEqualTo(503);
        JsonNode cuerpo = objectMapper.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
        assertThat(cuerpo.get("message").asText())
                .isEqualTo("El registro de cuentas no está disponible en este momento. Inténtalo más tarde.");
    }

    @Test
    void sinCorreoElRegistroResponde503YNoCreaLaCuenta() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("SC");

        assertNoDisponible(registrar(carnet));

        assertThat(estudianteRepository.findByCorreoInstitucional(carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv")).isEmpty();
    }

    @Test
    void sinCorreoUnaCuentaExistenteRecibeLaMismaRespuestaYNoCambia() throws Exception {
        String carnet = CarnetsDePrueba.nuevo("SC");
        Estudiante estudiante = new Estudiante();
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv");
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setEstadoCorreo(EstadoCorreo.PENDIENTE);
        estudiante = estudianteRepository.save(estudiante);

        assertNoDisponible(registrar(carnet));

        assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(estudiante.getId())).isEmpty();
    }

    // El reenvío, en cambio, responde lo de siempre (200 y el mismo cuerpo) sin consultar ni tocar la base: la
    // respuesta no puede depender de la cuenta
    @Test
    void sinCorreoElReenvioRespondeLoDeSiempreSinTocarNada(CapturedOutput salida) throws Exception {
        String inexistente = CarnetsDePrueba.nuevo("SC");
        Estudiante pendiente = cuentaCon(EstadoCorreo.PENDIENTE);
        Estudiante confirmada = cuentaCon(EstadoCorreo.CONFIRMADO);
        Estudiante anterior = cuentaCon(null);

        for (String carnet : new String[] {inexistente, pendiente.getCarnet(), confirmada.getCarnet(), anterior.getCarnet()}) {
            for (String identificador : new String[] {carnet, carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv"}) {
                MockHttpServletResponse respuesta = reenviar(identificador);
                assertThat(respuesta.getStatus()).isEqualTo(200);
                assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"mensaje\":\""
                        + "Si hay una cuenta pendiente de activación con esos datos, recibirás un nuevo enlace en tu correo "
                        + "institucional.\",\"correoInstitucional\":\"" + carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv\"}");
            }
        }

        assertThat(estudianteRepository.findByCorreoInstitucional(inexistente.toLowerCase(Locale.ROOT) + "@ues.edu.sv")).isEmpty();
        for (Estudiante cuenta : new Estudiante[] {pendiente, confirmada, anterior}) {
            assertThat(tokenRepository.findByEstudianteIdOrderByCreadoEnAsc(cuenta.getId())).isEmpty();
        }
        assertThat(reenviar("otro@gmail.com").getStatus()).isEqualTo(400);
        assertThat(salida.getAll()).contains("Reenvío de activación sin correo configurado")
                .doesNotContain(pendiente.getCorreoInstitucional());
    }

    // Un carnet inválido sigue siendo un 400: la validación no depende del correo
    @Test
    void sinCorreoUnCarnetInvalidoSigueSiendoUnErrorDelCliente() throws Exception {
        assertThat(registrar("invalido").getStatus()).isEqualTo(400);
    }
}
