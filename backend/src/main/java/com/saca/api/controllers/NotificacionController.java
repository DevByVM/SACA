package com.saca.api.controllers;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.request.PreferenciaNotificacionRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.dto.response.PreferenciaNotificacionResponse;
import com.saca.api.service.NotificacionService;
import com.saca.api.service.PreferenciaNotificacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final PreferenciaNotificacionService preferenciaService;
    private final NotificacionService notificacionService;

    public NotificacionController(
            PreferenciaNotificacionService preferenciaService,
            NotificacionService notificacionService
    ) {
        this.preferenciaService = preferenciaService;
        this.notificacionService = notificacionService;
    }

    // Solo se admite el estudianteId del propio token; uno distinto responde 404
    @GetMapping("/preferencias/{estudianteId}")
    public PreferenciaNotificacionResponse obtenerPreferencias(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long estudianteId) {
        return preferenciaService.obtenerPorEstudiante(EstudianteAutenticado.idPropio(jwt, estudianteId));
    }

    @PutMapping("/preferencias/{estudianteId}")
    public ResponseEntity<PreferenciaNotificacionResponse> guardarPreferencias(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long estudianteId,
            @Valid @RequestBody PreferenciaNotificacionRequest request
    ) {
        return ResponseEntity.ok(preferenciaService.guardarOActualizar(EstudianteAutenticado.idPropio(jwt, estudianteId), request));
    }

    @GetMapping("/estudiante/{estudianteId}/proximas")
    public List<ActividadAcademicaResponse> obtenerNotificacionesProximas(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long estudianteId) {
        return notificacionService.obtenerProximasPorEstudiante(EstudianteAutenticado.idPropio(jwt, estudianteId));
    }
}
