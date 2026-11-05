package com.saca.api.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.CarnetsDePrueba;
import com.saca.api.entity.Estudiante;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// El correo deriva del carnet, así que ninguno de los dos se modifica desde el perfil; un carnet
// anterior (sin el formato, vacío o null) se conserva tal cual. El formato y la normalización del carnet en el
// registro se prueban en RegistroActivacionTest.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroCarnetTest {

    private static final String CARNET_Y_CORREO_FIJOS = "El carnet y el correo institucional no se pueden modificar desde el perfil";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EstudianteRepository estudianteRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    // ================= utilidades

    private static String correoUnico() {
        return "carnet." + UUID.randomUUID().toString().substring(0, 8) + "@ues.edu.sv";
    }

    private static String texto(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Estudiante guardarConCarnet(String carnet) {
        Estudiante estudiante = new Estudiante();
        estudiante.setNombre("Estudiante Anterior");
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(correoUnico());
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("Segura123"));
        estudiante.setCuentaActiva(true);
        estudiante.setIntentosFallidos(0);
        return estudianteRepository.save(estudiante);
    }

    private String enviarPerfil(Estudiante estudiante, Map<String, Object> datos) throws Exception {
        return texto(mockMvc.perform(put("/api/auth/perfil")
                .header("Authorization", "Bearer " + tokenService.emitir(estudiante).valor())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(datos))).andReturn());
    }

    // Envía el propio correo y el carnet indicado; con carnet null el campo no se envía
    private String actualizarPerfil(Estudiante estudiante, String carnet) throws Exception {
        Map<String, Object> datos = new HashMap<>(Map.of(
                "nombre", "Nombre Actualizado",
                "correoInstitucional", estudiante.getCorreoInstitucional(), "contrasenia", ""));
        if (carnet != null) {
            datos.put("carnet", carnet);
        }
        return enviarPerfil(estudiante, datos);
    }

    private String carnetGuardado(Long id) {
        return estudianteRepository.findById(id).orElseThrow().getCarnet();
    }

    // ================= perfil: el carnet y el correo no se modifican

    @Test
    void sinEnviarCarnetNiCorreoSeActualizaElRestoDelPerfil() throws Exception {
        Estudiante estudiante = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));

        assertThat(enviarPerfil(estudiante, Map.of("nombre", "Nombre Actualizado", "contrasenia", "")))
                .isEqualTo("Perfil actualizado correctamente");

        Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Nombre Actualizado");
        assertThat(guardado.getCarnet()).isEqualTo(estudiante.getCarnet());
        assertThat(guardado.getCorreoInstitucional()).isEqualTo(estudiante.getCorreoInstitucional());
    }

    @Test
    void sinEnviarCarnetNiCorreoSePuedeCambiarLaContrasenia() throws Exception {
        Estudiante estudiante = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));

        assertThat(enviarPerfil(estudiante, Map.of("nombre", "Nombre Actualizado",
                "contrasenia", "NuevaClave123", "contraseniaActual", "Segura123")))
                .isEqualTo("Perfil actualizado correctamente");

        String hash = estudianteRepository.findById(estudiante.getId()).orElseThrow().getContrasenia();
        assertThat(new BCryptPasswordEncoder().matches("NuevaClave123", hash)).isTrue();
    }

    @Test
    void enviarElPropioCarnetYCorreoConOtrasMayusculasNoCambiaNada() throws Exception {
        Estudiante estudiante = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));
        String carnet = estudiante.getCarnet();
        String correo = estudiante.getCorreoInstitucional();

        assertThat(enviarPerfil(estudiante, Map.of("nombre", "Nombre Actualizado", "contrasenia", "",
                "carnet", carnet.toLowerCase(Locale.ROOT), "correoInstitucional", correo.toUpperCase(Locale.ROOT))))
                .isEqualTo("Perfil actualizado correctamente");

        Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getCarnet()).isEqualTo(carnet);
        assertThat(guardado.getCorreoInstitucional()).isEqualTo(correo);
    }

    @Test
    void cambiarElCarnetDesdeElPerfilSeRechazaSeaValidoOInvalido() throws Exception {
        Estudiante estudiante = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));
        String carnetInicial = estudiante.getCarnet();

        for (String otro : new String[] {CarnetsDePrueba.nuevo("RC"), "zz00000", "AB12", "  "}) {
            assertThat(actualizarPerfil(estudiante, otro)).as(otro).isEqualTo(CARNET_Y_CORREO_FIJOS);
        }

        Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getCarnet()).isEqualTo(carnetInicial);
        assertThat(guardado.getNombre()).isEqualTo("Estudiante Anterior");
    }

    @Test
    void cambiarElCorreoDesdeElPerfilSeRechaza() throws Exception {
        Estudiante estudiante = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));
        String correoInicial = estudiante.getCorreoInstitucional();

        assertThat(enviarPerfil(estudiante, Map.of("nombre", "Nombre Actualizado", "contrasenia", "",
                "correoInstitucional", correoUnico()))).isEqualTo(CARNET_Y_CORREO_FIJOS);

        Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getCorreoInstitucional()).isEqualTo(correoInicial);
        assertThat(guardado.getNombre()).isEqualTo("Estudiante Anterior");
    }

    @Test
    void noSePuedeTomarElCarnetDeOtroEstudianteAunqueCambienLasMayusculas() throws Exception {
        Estudiante otro = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));
        Estudiante estudiante = guardarConCarnet(CarnetsDePrueba.nuevo("RC"));
        String carnetPropio = estudiante.getCarnet();

        assertThat(actualizarPerfil(estudiante, otro.getCarnet().toLowerCase(Locale.ROOT))).isEqualTo(CARNET_Y_CORREO_FIJOS);
        assertThat(carnetGuardado(estudiante.getId())).isEqualTo(carnetPropio);
    }

    @Test
    void unCarnetAnteriorSinElFormatoSeConservaYNoPuedeCambiarse() throws Exception {
        String anterior = "legado" + UUID.randomUUID().toString().substring(0, 6);
        Estudiante estudiante = guardarConCarnet(anterior);

        assertThat(actualizarPerfil(estudiante, anterior)).isEqualTo("Perfil actualizado correctamente");
        assertThat(actualizarPerfil(estudiante, anterior.toUpperCase(Locale.ROOT))).isEqualTo("Perfil actualizado correctamente");
        assertThat(actualizarPerfil(estudiante, CarnetsDePrueba.nuevo("RC"))).isEqualTo(CARNET_Y_CORREO_FIJOS);

        Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getCarnet()).isEqualTo(anterior);
        assertThat(guardado.getNombre()).isEqualTo("Nombre Actualizado");
    }

    // La columna carnet es única: solo cabe una fila con carnet vacío, así que estas pruebas la borran al terminar
    @Test
    void unCarnetAnteriorVacioNoImpideActualizarElRestoDelPerfil() throws Exception {
        Estudiante estudiante = guardarConCarnet("");
        try {
            assertThat(actualizarPerfil(estudiante, "")).isEqualTo("Perfil actualizado correctamente");
            assertThat(actualizarPerfil(estudiante, "   ")).isEqualTo("Perfil actualizado correctamente");
            assertThat(actualizarPerfil(estudiante, null)).isEqualTo("Perfil actualizado correctamente");

            Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
            assertThat(guardado.getCarnet()).isEmpty();
            assertThat(guardado.getNombre()).isEqualTo("Nombre Actualizado");
        } finally {
            estudianteRepository.deleteById(estudiante.getId());
        }
    }

    @Test
    void unCarnetAnteriorNullNoImpideActualizarElRestoDelPerfil() throws Exception {
        Estudiante estudiante = guardarConCarnet(null);

        assertThat(actualizarPerfil(estudiante, null)).isEqualTo("Perfil actualizado correctamente");
        assertThat(actualizarPerfil(estudiante, "")).isEqualTo("Perfil actualizado correctamente");

        Estudiante guardado = estudianteRepository.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getCarnet()).isNull();
        assertThat(guardado.getNombre()).isEqualTo("Nombre Actualizado");
    }

    @Test
    void unCarnetAnteriorVacioONullNoPuedeSustituirseDesdeElPerfil() throws Exception {
        for (String anterior : new String[] {"", null}) {
            Estudiante estudiante = guardarConCarnet(anterior);
            try {
                assertThat(actualizarPerfil(estudiante, CarnetsDePrueba.nuevo("RC").toLowerCase(Locale.ROOT)))
                        .as(String.valueOf(anterior)).isEqualTo(CARNET_Y_CORREO_FIJOS);
                assertThat(actualizarPerfil(estudiante, "AB12")).as(String.valueOf(anterior)).isEqualTo(CARNET_Y_CORREO_FIJOS);
                assertThat(carnetGuardado(estudiante.getId())).as(String.valueOf(anterior)).isEqualTo(anterior);
            } finally {
                estudianteRepository.deleteById(estudiante.getId());
            }
        }
    }
}
