package com.saca.api.controllers;

import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.service.ActividadAcademicaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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


}
