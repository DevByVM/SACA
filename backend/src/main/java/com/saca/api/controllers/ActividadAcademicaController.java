package com.saca.api.controllers;

import com.saca.api.dto.request.ActividadAcademicaRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.service.ActividadAcademicaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/actividades")
public class ActividadAcademicaController {
    private final ActividadAcademicaService service;

    public ActividadAcademicaController(ActividadAcademicaService service) {
        this.service = service;
    }

    @GetMapping("/{actividadId}")
    public ActividadAcademicaResponse obtenerActividadById(@PathVariable Long actividadId){
        return  service.getById(actividadId);
    }

    @GetMapping("/estudiante/{estudianteId}/activo")
    public List<ActividadAcademicaResponse> listarPorCicloActivoYEstdiante(@PathVariable Long estudianteId){
        return service.obtenerPorCicloActivoYEstdiante(estudianteId);
    }

    //se solicita desde una ruta tipo: /api/actividades?cicloId=1&estudianteId=1
    @GetMapping()
    public List<ActividadAcademicaResponse> listarPorCiclo(@RequestParam Long estudianteId,@RequestParam Long cicloId){
        return service.obtenerPorCicloYEstdiante(cicloId,estudianteId);
    }

    @PostMapping
    public ResponseEntity<ActividadAcademicaResponse> crear(@Valid @RequestBody ActividadAcademicaRequest request) {
        ActividadAcademicaResponse response = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{actividadId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long actividadId){
        service.eliminar(actividadId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{actividadId}")
    public ResponseEntity<ActividadAcademicaResponse> actualizar(@Valid @RequestBody ActividadAcademicaRequest request, @PathVariable Long actividadId) {
        ActividadAcademicaResponse response = service.actualizar(request,actividadId);
        return ResponseEntity.ok(response);
    }


}
