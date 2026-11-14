package com.saca.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.dto.response.CatalogoMateriaResponse;
import com.saca.api.service.CatalogoMateriaService;

@RestController
@RequestMapping("/api/catalogo-materias")
public class CatalogoMateriaController {

    private final CatalogoMateriaService service;

    public CatalogoMateriaController(CatalogoMateriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<CatalogoMateriaResponse> listarActivas() {
        return service.listarActivas();
    }
}
