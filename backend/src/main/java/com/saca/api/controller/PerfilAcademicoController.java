package com.saca.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.ActualizarPerfilAcademicoRequest;
import com.saca.api.dto.PerfilAcademicoResponse;
import com.saca.api.service.PerfilAcademicoService;

@RestController
@RequestMapping("/api/perfil-academico")
public class PerfilAcademicoController {

    @Autowired
    private PerfilAcademicoService service;

    // Siempre el perfil académico del estudiante del token; el cliente no elige el estudiante
    @GetMapping
    public ResponseEntity<PerfilAcademicoResponse> obtener(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.obtenerPorEstudiante(EstudianteAutenticado.id(jwt)));
    }

    @PutMapping
    public ResponseEntity<String> actualizar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ActualizarPerfilAcademicoRequest request
    ) {
        return ResponseEntity.ok(
                service.guardarOActualizar(EstudianteAutenticado.id(jwt), request)
        );
    }
}