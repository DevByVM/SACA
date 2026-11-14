package com.saca.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.dto.request.ParametrosCargaRequest;
import com.saca.api.dto.response.ParametrosCargaResponse;
import com.saca.api.service.ParametrosCargaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/parametros-carga")
public class ParametrosCargaController {

    private final ParametrosCargaService service;

    public ParametrosCargaController(ParametrosCargaService service) {
        this.service = service;
    }

    @GetMapping
    public ParametrosCargaResponse obtener() {
        return service.obtener();
    }

    @PutMapping
    public ParametrosCargaResponse guardar(@Valid @RequestBody ParametrosCargaRequest request) {
        return service.guardar(request);
    }
}
