package com.saca.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saca.api.entity.*;
import com.saca.api.repository.*;
import com.saca.api.service.*;
import com.saca.api.dto.request.RestablecerContraseniaRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestionUsuariosAdminTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EstudianteRepository estudiantes;
    @Autowired AdministradorRepository administradores;
    @Autowired TokenService sesiones;
    @Autowired RestablecimientoContraseniaService restablecimientos;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CorreoActivacionService correo;
    Estudiante estudiante;
    String adminToken;
    String studentToken;

    @BeforeEach
    void preparar() {
        when(correo.disponible()).thenReturn(true);
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Administrador admin = new Administrador();
        admin.setNombre("Admin Prueba");
        admin.setCorreo("admin" + sufijo + "@ues.edu.sv");
        admin.setContrasenia(new BCryptPasswordEncoder().encode("AdminPrueba123"));
        adminToken = sesiones.emitir(administradores.save(admin)).valor();
        estudiante = new Estudiante();
        estudiante.setNombre("Ana Prueba");
        estudiante.setCarnet("PR" + sufijo);
        estudiante.setCorreoInstitucional("pr" + sufijo + "@ues.edu.sv");
        estudiante.setContrasenia(new BCryptPasswordEncoder().encode("Anterior123"));
        estudiante.setCuentaActiva(true);
        estudiante.setEstadoCorreo(EstadoCorreo.CONFIRMADO);
        estudiante.setIntentosFallidos(0);
        estudiante = estudiantes.saveAndFlush(estudiante);
        studentToken = sesiones.emitir(estudiante).valor();
    }

    private String ruta() { return "/api/admin/usuarios/" + estudiante.getId(); }
    private String edicion(String carnet, String destino, boolean habilitada) throws Exception {
        return json.writeValueAsString(java.util.Map.of("nombre", "Ana Editada", "carnet", carnet,
                "correoInstitucional", destino, "habilitada", habilitada));
    }
    private String solicitar() throws Exception {
        String respuesta = mvc.perform(post(ruta() + "/restablecimiento").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("#restablecimiento", "hash", "contrasenia");
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(correo, atLeastOnce()).enviarRestablecimiento(eq(estudiante.getCorreoInstitucional()), token.capture(), any());
        return token.getValue();
    }
    private org.springframework.test.web.servlet.ResultActions completar(String token, String clave) throws Exception {
        return mvc.perform(post("/api/auth/restablecimiento").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("token", token, "contrasenia", clave))));
    }
    private JsonNode login(String clave) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("correoInstitucional", estudiante.getCorreoInstitucional(),
                        "contrasenia", clave))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void administradorEditaDatosYEstadoSinCambiarCredenciales() throws Exception {
        String hash = estudiante.getContrasenia();
        String destino = "editada" + estudiante.getId() + "@ues.edu.sv";
        mvc.perform(put(ruta()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(edicion("ED23001", destino, false))).andExpect(status().isOk());
        Estudiante guardado = estudiantes.findById(estudiante.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Ana Editada");
        assertThat(guardado.getCarnet()).isEqualTo("ED23001");
        assertThat(guardado.getCorreoInstitucional()).isEqualTo(destino);
        assertThat(guardado.getCuentaActiva()).isFalse();
        assertThat(guardado.getContrasenia()).isEqualTo(hash);
        mvc.perform(get("/api/auth/perfil").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(ruta() + "/estado").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"habilitada\":true}"))
                .andExpect(status().isOk());
        assertThat(estudiantes.findById(estudiante.getId()).orElseThrow().getCuentaActiva()).isTrue();
    }

    @Test
    void rechazaDuplicadosDatosInvalidosYAccesoAjeno() throws Exception {
        Estudiante otro = new Estudiante();
        otro.setCarnet("DU23001");
        otro.setCorreoInstitucional("duplicado" + estudiante.getId() + "@ues.edu.sv");
        otro.setContrasenia(estudiante.getContrasenia());
        estudiantes.saveAndFlush(otro);
        for (String cuerpo : java.util.List.of(edicion("DU23001", estudiante.getCorreoInstitucional(), true),
                edicion("VA23001", otro.getCorreoInstitucional(), true), edicion("mal", "x@gmail.com", true))) {
            mvc.perform(put(ruta()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo)).andExpect(status().isBadRequest());
        }
        for (String token : java.util.List.of("", studentToken)) {
            int esperado = token.isEmpty() ? 401 : 403;
            mvc.perform(put(ruta()).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content(edicion("VA23001", estudiante.getCorreoInstitucional(), true)))
                    .andExpect(status().is(esperado));
            mvc.perform(post(ruta() + "/restablecimiento").header("Authorization", "Bearer " + token))
                    .andExpect(status().is(esperado));
        }
        assertThat(estudiantes.findById(estudiante.getId()).orElseThrow().getNombre()).isEqualTo("Ana Prueba");
    }

    @Test
    void contraseniaAnteriorSigueVigenteHastaCompletarYElEnlaceEsDeUnSoloUso() throws Exception {
        String token = solicitar();
        assertThat(login("Anterior123").get("autenticado").asBoolean()).isTrue();
        completar(token, "Anterior123").andExpect(status().isBadRequest());
        assertThat(restablecimientos.consultar(token)).isEqualTo("VALIDO");
        mvc.perform(post("/api/auth/restablecimiento/consultar").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("token", token))))
                .andExpect(status().isOk());
        completar(token, "NuevaSegura123").andExpect(status().isOk());
        assertThat(login("NuevaSegura123").get("autenticado").asBoolean()).isTrue();
        assertThat(login("Anterior123").get("autenticado").asBoolean()).isFalse();
        completar(token, "TerceraClave123").andExpect(status().isBadRequest());
        assertThat(restablecimientos.consultar(token)).isEqualTo("USADO");
        assertThat(estudiantes.findById(estudiante.getId()).orElseThrow().getCuentaActiva()).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("NuevaSegura123",
                estudiantes.findById(estudiante.getId()).orElseThrow().getContrasenia())).isTrue();
    }

    @Test
    void enlaceVencidoSustituidoOClaveInvalidaNoCambiaLaCuenta() throws Exception {
        String primero = solicitar();
        String segundo = solicitar();
        completar(primero, "NuevaSegura123").andExpect(status().isBadRequest());
        completar(segundo, "corta").andExpect(status().isBadRequest());
        assertThat(restablecimientos.consultar(segundo)).isEqualTo("VALIDO");
        jdbc.update("update tokens_restablecimiento set expira_en = ? where estudiante_id = ?", LocalDateTime.now().minusHours(2), estudiante.getId());
        completar(segundo, "NuevaSegura123").andExpect(status().isBadRequest());
        assertThat(restablecimientos.consultar(segundo)).isEqualTo("EXPIRADO");
        assertThat(login("Anterior123").get("autenticado").asBoolean()).isTrue();
    }

    @Test
    void restablecimientoMantieneLaCuentaInhabilitada() throws Exception {
        estudiante.setCuentaActiva(false);
        estudiantes.saveAndFlush(estudiante);
        String token = solicitar();
        completar(token, "NuevaSegura123").andExpect(status().isOk());
        assertThat(estudiantes.findById(estudiante.getId()).orElseThrow().getCuentaActiva()).isFalse();
        assertThat(login("NuevaSegura123").get("autenticado").asBoolean()).isFalse();
    }

    @Test
    void cambiarCorreoInvalidaElRestablecimientoAnterior() throws Exception {
        String token = solicitar();
        mvc.perform(put(ruta()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(edicion("CC23001", "nuevo" + estudiante.getId() + "@ues.edu.sv", true)))
                .andExpect(status().isOk());
        completar(token, "NuevaSegura123").andExpect(status().isBadRequest());
    }

    @Test
    void cuentaPendienteConservaActivacionYRechazaRestablecimiento() throws Exception {
        estudiante.setEstadoCorreo(EstadoCorreo.PENDIENTE);
        estudiantes.saveAndFlush(estudiante);
        mvc.perform(post(ruta() + "/restablecimiento").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ruta()).header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                .content(edicion("PA23001", "pendiente" + estudiante.getId() + "@ues.edu.sv", true)))
                .andExpect(status().isOk());
        verify(correo).enviarActivacion(eq("pendiente" + estudiante.getId() + "@ues.edu.sv"), anyString(), any());
        assertThat(estudiantes.findById(estudiante.getId()).orElseThrow().getEstadoCorreo()).isEqualTo(EstadoCorreo.PENDIENTE);
    }

    @Test
    void altaDuplicadaNoDuplicaLaCuentaNiAlteraSusDatos() throws Exception {
        long antes = estudiantes.count();
        String carnet = "AL" + String.format("%05d", estudiante.getId() % 100000);
        estudiante.setCarnet(carnet);
        estudiante.setCorreoInstitucional(carnet.toLowerCase() + "@ues.edu.sv");
        estudiantes.saveAndFlush(estudiante);
        mvc.perform(post("/api/admin/usuarios").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"carnet\":\"" + carnet + "\"}"))
                .andExpect(status().isAccepted());
        assertThat(estudiantes.count()).isEqualTo(antes);
        assertThat(estudiantes.findById(estudiante.getId()).orElseThrow().getNombre()).isEqualTo("Ana Prueba");
        verify(correo, never()).enviarActivacion(anyString(), anyString(), any());
    }

    @Test
    void falloSmtpRevierteLaEmisionYNoCambiaContrasenia() throws Exception {
        doThrow(new CorreoActivacionService.CorreoNoEnviadoException(new IllegalStateException()))
                .when(correo).enviarRestablecimiento(anyString(), anyString(), any());
        mvc.perform(post(ruta() + "/restablecimiento").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("select count(*) from tokens_restablecimiento where estudiante_id = ?", Long.class, estudiante.getId())).isZero();
        assertThat(login("Anterior123").get("autenticado").asBoolean()).isTrue();
    }

    @Test
    void dosConsumosSimultaneosSoloPermitenUnCambio() throws Exception {
        String token = solicitar();
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Boolean> tarea = () -> {
            inicio.await();
            try {
                restablecimientos.restablecer(new RestablecerContraseniaRequest(token, "NuevaSegura123"));
                return true;
            } catch (com.saca.api.exception.ValidacionNegocioException e) {
                return false;
            }
        };
        try {
            Future<Boolean> a = ejecutor.submit(tarea);
            Future<Boolean> b = ejecutor.submit(tarea);
            inicio.countDown();
            assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { ejecutor.shutdownNow(); }
    }
}
