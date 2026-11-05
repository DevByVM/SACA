package com.saca.api.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.entity.EstadoCorreo;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.TokenActivacionService;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// Contraseñas de más de 72 bytes UTF-8 (límite de BCrypt) por HTTP. Al fijarlas, 400 antes de
// consumir el enlace o escribir nada; al comprobarlas (login y contraseña actual del perfil), nunca coinciden,
// ni siquiera como prolongación de una de 72 bytes. Los hashes existentes no cambian
@SpringBootTest(properties = "SACA_LIMITE_ACTIVACION_MIN=1000000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContraseniaLargaTest {

    private static final String MENSAJE_LARGA =
            "La contraseña es demasiado larga: admite hasta 72 bytes (las letras con tilde y la ñ cuentan doble)";
    private static final String CLAVE = "Segura123";
    private static final String ASCII_72 = "A1" + "a".repeat(70);
    private static final String ASCII_73 = ASCII_72 + "a";
    private static final String ENIE_72 = "A1" + "ñ".repeat(35);
    private static final String ENIE_74 = "A1" + "ñ".repeat(36);
    private static final String EMOJI_72 = "A1" + "a".repeat(66) + "😀";
    private static final String EMOJI_73 = EMOJI_72 + "a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EstudianteRepository repository;

    @Autowired
    private TokenActivacionService tokens;

    @Autowired
    private TokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Estudiante cuenta(EstadoCorreo estado, String contrasenia) {
        String carnet = CarnetsDePrueba.nuevo("CL");
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Prueba Larga");
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(carnet.toLowerCase(Locale.ROOT) + "@ues.edu.sv");
        estudiante.setContrasenia(passwordEncoder.encode(contrasenia));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        estudiante.setEstadoCorreo(estado);
        return repository.save(estudiante);
    }

    private MockHttpServletResponse publicar(String ruta, Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo))).andReturn().getResponse();
    }

    private MockHttpServletResponse activar(String token, String contrasenia) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("token", token);
        cuerpo.put("nombre", "Nombre Prueba");
        cuerpo.put("contrasenia", contrasenia);
        return publicar("/api/auth/activacion", cuerpo);
    }

    private boolean login(Estudiante estudiante, String contrasenia) throws Exception {
        return json(publicar("/api/auth/login", Map.of(
                "correoInstitucional", estudiante.getCorreoInstitucional(), "contrasenia", contrasenia)))
                .get("autenticado").asBoolean();
    }

    private MockHttpServletResponse perfil(Estudiante estudiante, String nueva, String actual) throws Exception {
        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Prueba Larga");
        cuerpo.put("contrasenia", nueva);
        cuerpo.put("contraseniaActual", actual);
        return mockMvc.perform(put("/api/auth/perfil")
                .header("Authorization", "Bearer " + tokenService.emitir(estudiante).valor())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andReturn().getResponse();
    }

    private JsonNode json(MockHttpServletResponse respuesta) throws Exception {
        return objectMapper.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
    }

    private Estudiante leer(Estudiante estudiante) {
        return repository.findById(estudiante.getId()).orElseThrow();
    }

    // ================= activación

    @Test
    void laActivacionAdmiteHasta72BytesYEsaContraseniaSirveParaEntrar() throws Exception {
        for (String contrasenia : new String[] {ASCII_72, ENIE_72, EMOJI_72}) {
            Estudiante pendiente = cuenta(EstadoCorreo.PENDIENTE, "Secreto123");
            String token = tokens.generar(pendiente).valor();

            assertThat(activar(token, contrasenia).getStatus()).isEqualTo(200);

            assertThat(leer(pendiente).getEstadoCorreo()).isEqualTo(EstadoCorreo.CONFIRMADO);
            assertThat(login(pendiente, contrasenia)).isTrue();
            assertThat(login(pendiente, contrasenia + "X")).isFalse();
        }
    }

    @Test
    void laActivacionRechazaMasDe72BytesSinConsumirElEnlace() throws Exception {
        Estudiante pendiente = cuenta(EstadoCorreo.PENDIENTE, "Secreto123");
        String token = tokens.generar(pendiente).valor();

        for (String contrasenia : new String[] {ASCII_73, ENIE_74, EMOJI_73}) {
            MockHttpServletResponse respuesta = activar(token, contrasenia);
            assertThat(respuesta.getStatus()).isEqualTo(400);
            assertThat(json(respuesta).get("message").asText()).isEqualTo(MENSAJE_LARGA);
        }

        assertThat(json(publicar("/api/auth/activacion/consultar", Map.of("token", token))).get("estado").asText())
                .isEqualTo("VALIDO");
        assertThat(leer(pendiente).getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
        assertThat(activar(token, CLAVE).getStatus()).isEqualTo(200);
    }

    // ================= perfil

    @Test
    void elPerfilRechazaUnaContraseniaNuevaDeMasDe72BytesCon400() throws Exception {
        Estudiante estudiante = cuenta(EstadoCorreo.CONFIRMADO, CLAVE);
        String hash = estudiante.getContrasenia();

        for (String contrasenia : new String[] {ASCII_73, ENIE_74, EMOJI_73}) {
            MockHttpServletResponse respuesta = perfil(estudiante, contrasenia, CLAVE);
            assertThat(respuesta.getStatus()).isEqualTo(400);
            assertThat(json(respuesta).get("message").asText()).isEqualTo(MENSAJE_LARGA);
        }
        assertThat(leer(estudiante).getContrasenia()).isEqualTo(hash);

        assertThat(perfil(estudiante, ENIE_72, CLAVE).getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("Perfil actualizado correctamente");
        assertThat(login(estudiante, ENIE_72)).isTrue();
    }

    // La contraseña actual se comprueba con el verificador central: una prolongación de la de 72 bytes no vale
    @Test
    void laContraseniaActualDelPerfilNoAceptaProlongaciones() throws Exception {
        Estudiante estudiante = cuenta(EstadoCorreo.CONFIRMADO, ASCII_72);
        String hash = estudiante.getContrasenia();

        assertThat(perfil(estudiante, "Distinta456", ASCII_72 + "X").getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("La contraseña actual es incorrecta");
        assertThat(leer(estudiante).getContrasenia()).isEqualTo(hash);

        assertThat(perfil(estudiante, "Distinta456", ASCII_72).getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("Perfil actualizado correctamente");
        assertThat(login(estudiante, "Distinta456")).isTrue();
    }

    // ================= login

    @Test
    void unaProlongacionDeUnaContraseniaDe72BytesNoIniciaSesionYCuentaComoFallo() throws Exception {
        for (String contrasenia : new String[] {ASCII_72, ENIE_72, EMOJI_72}) {
            Estudiante estudiante = cuenta(EstadoCorreo.CONFIRMADO, contrasenia);

            assertThat(login(estudiante, contrasenia + "X")).isFalse();
            assertThat(login(estudiante, contrasenia + "😀")).isFalse();
            assertThat(leer(estudiante).getIntentosFallidos()).isEqualTo(2);

            assertThat(login(estudiante, contrasenia)).isTrue();
            assertThat(leer(estudiante).getIntentosFallidos()).isZero();
        }
    }
}
