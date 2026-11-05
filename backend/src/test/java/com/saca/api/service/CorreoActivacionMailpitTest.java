package com.saca.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Envío real a Mailpit (perfil "correo" de docker-compose.correo.yml). Solo se ejecuta con
// SACA_PRUEBA_MAILPIT=true y Mailpit escuchando en 127.0.0.1:1025 (SMTP) y 127.0.0.1:8025 (API)
@EnabledIfEnvironmentVariable(named = "SACA_PRUEBA_MAILPIT", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1025",
        "saca.correo.remitente=SACA UES <no-responder@saca.local>",
        "saca.correo.url-frontend=http://localhost:5173"
})
class CorreoActivacionMailpitTest {

    private static final String API_MAILPIT = "http://127.0.0.1:8025/api/v1";

    @Autowired
    private CorreoActivacionService service;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    private JsonNode get(String ruta) throws Exception {
        HttpResponse<String> respuesta = http.send(HttpRequest.newBuilder(URI.create(API_MAILPIT + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return objectMapper.readTree(respuesta.body());
    }

    @Test
    void elCorreoLlegaAMailpitConElEnlaceYLosAcentos() throws Exception {
        String destino = "pm" + UUID.randomUUID().toString().substring(0, 6) + "@ues.edu.sv";
        String token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_" + UUID.randomUUID().toString().substring(0, 5);

        service.enviarActivacion(destino, token, LocalDateTime.of(2026, 11, 4, 15, 30));

        JsonNode busqueda = get("/search?query=" + URLEncoder.encode("to:" + destino, StandardCharsets.UTF_8));
        assertThat(busqueda.get("messages")).hasSize(1);
        JsonNode resumen = busqueda.get("messages").get(0);
        assertThat(resumen.get("Subject").asText()).isEqualTo("Activa tu cuenta de SACA UES");
        assertThat(resumen.get("From").get("Address").asText()).isEqualTo("no-responder@saca.local");

        JsonNode mensaje = get("/message/" + resumen.get("ID").asText());
        assertThat(mensaje.get("Text").asText())
                .contains("http://localhost:5173/#token=" + token)
                .contains("SACA UES - Sistema de Análisis de Carga Académica")
                .contains("04/11/2026 a las 15:30");
    }
}
