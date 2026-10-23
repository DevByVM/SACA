package com.saca.api.controllers;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.request.ActividadAcademicaRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.service.ActividadAcademicaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/actividades")
public class ActividadAcademicaController {
    private final ActividadAcademicaService service;

    public ActividadAcademicaController(ActividadAcademicaService service) {
        this.service = service;
    }

    // El estudiante siempre es el del token; un estudianteId distinto en la URL responde 404
    @GetMapping("/{actividadId}")
    public ActividadAcademicaResponse obtenerActividadById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long actividadId){
        return  service.getById(actividadId, EstudianteAutenticado.id(jwt));
    }

    @GetMapping("/estudiante/{estudianteId}/activo")
    public List<ActividadAcademicaResponse> listarPorCicloActivoYEstdiante(@AuthenticationPrincipal Jwt jwt, @PathVariable Long estudianteId){
        return service.obtenerPorCicloActivoYEstdiante(EstudianteAutenticado.idPropio(jwt, estudianteId));
    }

    //se solicita desde una ruta tipo: /api/actividades?cicloId=1&estudianteId=1
    @GetMapping()
    public List<ActividadAcademicaResponse> listarPorCiclo(@AuthenticationPrincipal Jwt jwt, @RequestParam Long estudianteId,@RequestParam Long cicloId){
        return service.obtenerPorCicloYEstdiante(cicloId, EstudianteAutenticado.idPropio(jwt, estudianteId));
    }

    @PostMapping
    public ResponseEntity<ActividadAcademicaResponse> crear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ActividadAcademicaRequest request) {
        ActividadAcademicaResponse response = service.crear(request, EstudianteAutenticado.id(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{actividadId}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long actividadId){
        service.eliminar(actividadId, EstudianteAutenticado.id(jwt));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{actividadId}")
    public ResponseEntity<ActividadAcademicaResponse> actualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ActividadAcademicaRequest request, @PathVariable Long actividadId) {
        ActividadAcademicaResponse response = service.actualizar(request, actividadId, EstudianteAutenticado.id(jwt));
        return ResponseEntity.ok(response);
    }


}
