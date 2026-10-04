package com.saca.api.controllers;

import com.saca.api.dto.request.PreferenciaNotificacionRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.dto.response.PreferenciaNotificacionResponse;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.service.NotificacionService;
import com.saca.api.service.PreferenciaNotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificacionController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenciaNotificacionService preferenciaService;

    @MockitoBean
    private NotificacionService notificacionService;

    @Test
    void obtieneLasPreferenciasDelEstudiante() throws Exception {
        given(preferenciaService.obtenerPorEstudiante(7L)).willReturn(respuestaDePreferencias());

        mockMvc.perform(get("/api/notificaciones/preferencias/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estudianteId").value(7))
                .andExpect(jsonPath("$.tiposDisponibles[0]").value("PARCIAL"))
                .andExpect(jsonPath("$.tiposActividadHabilitados[0]").value("PARCIAL"));
    }

    @Test
    void guardaLasPreferenciasDelEstudiante() throws Exception {
        given(preferenciaService.guardarOActualizar(eq(7L), any(PreferenciaNotificacionRequest.class)))
                .willReturn(respuestaDePreferencias());

        mockMvc.perform(put("/api/notificaciones/preferencias/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tiposActividadHabilitados": ["PARCIAL"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposActividadHabilitados[0]").value("PARCIAL"));

        then(preferenciaService).should()
                .guardarOActualizar(eq(7L), any(PreferenciaNotificacionRequest.class));
    }

    @Test
    void rechazaUnaActualizacionSinTiposDeActividad() throws Exception {
        mockMvc.perform(put("/api/notificaciones/preferencias/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("tiposActividadHabilitados: Debe indicar los tipos de actividad para las notificaciones"));
    }

    @Test
    void obtieneLasNotificacionesProximasDelEstudiante() throws Exception {
        given(notificacionService.obtenerProximasPorEstudiante(7L)).willReturn(List.of(
                new ActividadAcademicaResponse(
                        12L,
                        "Proyecto de arquitectura",
                        null,
                        null,
                        20.0,
                        8.0,
                        "ACTIVO",
                        null,
                        TipoActividadAcademica.PROYECTO,
                        3L,
                        null,
                        null,
                        "PROYECTO",
                        "DSI215"
                )
        ));

        mockMvc.perform(get("/api/notificaciones/estudiante/7/proximas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idActividad").value(12))
                .andExpect(jsonPath("$[0].tipoActividad").value("PROYECTO"))
                .andExpect(jsonPath("$[0].materiaInscritaCodigo").value("DSI215"));
    }

    private PreferenciaNotificacionResponse respuestaDePreferencias() {
        return new PreferenciaNotificacionResponse(
                7L,
                List.of(
                        TipoActividadAcademica.PARCIAL,
                        TipoActividadAcademica.LABORATORIO,
                        TipoActividadAcademica.PROYECTO,
                        TipoActividadAcademica.INVESTIGACION
                ),
                List.of(TipoActividadAcademica.PARCIAL)
        );
    }
}
