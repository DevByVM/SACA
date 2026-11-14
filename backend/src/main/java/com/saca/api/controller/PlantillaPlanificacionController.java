package com.saca.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.request.PlantillaPlanificacionRequest;
import com.saca.api.dto.response.PlantillaPlanificacionResponse;
import com.saca.api.service.PlantillaPlanificacionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PlantillaPlanificacionController {

    private final PlantillaPlanificacionService service;

    public PlantillaPlanificacionController(PlantillaPlanificacionService service) {
        this.service = service;
    }

    @GetMapping("/admin/plantillas-planificacion")
    public List<PlantillaPlanificacionResponse> listar() {
        return service.listar();
    }

    @PostMapping("/admin/plantillas-planificacion")
    @ResponseStatus(HttpStatus.CREATED)
    public PlantillaPlanificacionResponse crear(@Valid @RequestBody PlantillaPlanificacionRequest request) {
        return service.crear(request);
    }

    @PutMapping("/admin/plantillas-planificacion/{id}")
    public PlantillaPlanificacionResponse actualizar(
            @PathVariable Long id, @Valid @RequestBody PlantillaPlanificacionRequest request) {
        return service.actualizar(id, request);
    }

    @GetMapping("/plantillas-planificacion/vigente")
    public PlantillaPlanificacionResponse vigente(@AuthenticationPrincipal Jwt jwt) {
        return service.vigenteParaEstudiante(EstudianteAutenticado.id(jwt));
    }
}
