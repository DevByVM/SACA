package com.saca.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.saca.api.dto.ActualizarPerfilAcademicoRequest;
import com.saca.api.dto.PerfilAcademicoResponse;
import com.saca.api.service.PerfilAcademicoService;

@RestController
@RequestMapping("/api/perfil-academico")
@CrossOrigin(origins = "*")
public class PerfilAcademicoController {

    @Autowired
    private PerfilAcademicoService service;

    
    @GetMapping("/{estudianteId}")
    public ResponseEntity<PerfilAcademicoResponse> obtener(@PathVariable Long estudianteId) {
        return ResponseEntity.ok(service.obtenerPorEstudiante(estudianteId));
    }

    @PutMapping("/{estudianteId}")
    public ResponseEntity<String> actualizar(
            @PathVariable Long estudianteId,
            @RequestBody ActualizarPerfilAcademicoRequest request
    ) {
        return ResponseEntity.ok(
                service.guardarOActualizar(estudianteId, request)
        );
    }
}