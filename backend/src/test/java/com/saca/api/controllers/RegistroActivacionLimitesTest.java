package com.saca.api.controllers;

import com.saca.api.CarnetsDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Los límites por cliente (por IP) se aplican al registro (registro y reenvío) y a la consulta y
// activación del enlace, antes de cualquier otra comprobación. Aquí con 2 por ventana para alcanzarlos enseguida
@SpringBootTest(properties = {
        "SACA_CORREO_REMITENTE=saca@pruebas.ues.edu.sv",
        "SACA_FRONTEND_URL=https://saca.pruebas.local",
        "SACA_LIMITE_REGISTRO_10_MIN=2",
        "SACA_LIMITE_ACTIVACION_MIN=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroActivacionLimitesTest {

    @TestConfiguration
    static class Configuracion {
        @Bean
        JavaMailSender clienteSmtpDePrueba() {
            return mock(JavaMailSender.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private MockHttpServletResponse publicar(String ruta, String ip, String cuerpo) throws Exception {
        return mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .with(peticion -> {
                    peticion.setRemoteAddr(ip);
                    return peticion;
                })).andReturn().getResponse();
    }

    private static void assertLimitado(MockHttpServletResponse respuesta) throws Exception {
        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(respuesta.getHeader("Retry-After"))).isPositive();
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("Demasiadas solicitudes desde tu red");
    }

    @Test
    void elRegistroCuentaEnElLimiteDeRegistroYReenvio() throws Exception {
        String ip = "203.0.113.150";
        for (int i = 0; i < 2; i++) {
            assertThat(publicar("/api/auth/registro", ip, "{\"carnet\":\"" + CarnetsDePrueba.nuevo("RL") + "\"}")
                    .getStatus()).isEqualTo(200);
        }
        // También cuentan los inválidos: el límite se aplica antes de validar
        assertLimitado(publicar("/api/auth/registro", ip, "{\"carnet\":\"invalido\"}"));
        assertThat(publicar("/api/auth/registro", "203.0.113.151", "{\"carnet\":\"" + CarnetsDePrueba.nuevo("RL") + "\"}")
                .getStatus()).isEqualTo(200);
    }

    // El reenvío comparte el contador del registro: aquí 2 cada 10 minutos entre los dos
    @Test
    void elReenvioCompartePorClienteElLimiteDelRegistro() throws Exception {
        String ip = "203.0.113.170";
        String carnet = CarnetsDePrueba.nuevo("RL");
        assertThat(publicar("/api/auth/registro", ip, "{\"carnet\":\"" + carnet + "\"}").getStatus()).isEqualTo(200);
        assertThat(publicar("/api/auth/reenviar-activacion", ip, "{\"identificador\":\"" + carnet + "\"}").getStatus())
                .isEqualTo(200);

        assertLimitado(publicar("/api/auth/reenviar-activacion", ip, "{\"identificador\":\"" + carnet + "\"}"));
        assertLimitado(publicar("/api/auth/registro", ip, "{\"carnet\":\"" + CarnetsDePrueba.nuevo("RL") + "\"}"));
        assertThat(publicar("/api/auth/reenviar-activacion", "203.0.113.171", "{\"identificador\":\"" + carnet + "\"}")
                .getStatus()).isEqualTo(200);
    }

    @Test
    void laConsultaYLaActivacionCompartenSuLimite() throws Exception {
        String ip = "203.0.113.160";
        String token = "{\"token\":\"" + "C".repeat(43) + "\"}";
        assertThat(publicar("/api/auth/activacion/consultar", ip, token).getStatus()).isEqualTo(200);
        assertThat(publicar("/api/auth/activacion", ip,
                "{\"token\":\"" + "C".repeat(43) + "\",\"nombre\":\"Nombre Prueba\",\"contrasenia\":\"Segura123\"}")
                .getStatus()).isEqualTo(400);

        assertLimitado(publicar("/api/auth/activacion/consultar", ip, token));
        assertLimitado(publicar("/api/auth/activacion", ip, token));
        assertThat(publicar("/api/auth/activacion/consultar", "203.0.113.161", token).getStatus()).isEqualTo(200);
    }
}
