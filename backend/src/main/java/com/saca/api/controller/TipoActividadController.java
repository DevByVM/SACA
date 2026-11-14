package com.saca.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.dto.request.TipoActividadRequest;
import com.saca.api.dto.response.TipoActividadResponse;
import com.saca.api.service.TipoActividadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class TipoActividadController {

    private final TipoActividadService service;

    public TipoActividadController(TipoActividadService service) {
        this.service = service;
    }

    @GetMapping("/tipos-actividad")
    public List<TipoActividadResponse> listarActivos() {
        return service.listarActivos();
    }

    @GetMapping("/admin/tipos-actividad")
    public List<TipoActividadResponse> listarTodos() {
        return service.listarTodos();
    }

    @PostMapping("/admin/tipos-actividad")
    public ResponseEntity<TipoActividadResponse> crear(@Valid @RequestBody TipoActividadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/admin/tipos-actividad/{id}")
    public TipoActividadResponse actualizar(@PathVariable Integer id,
            @Valid @RequestBody TipoActividadRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/admin/tipos-actividad/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
