package com.saca.api.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.saca.api.dto.CalendarioDTO;
import com.saca.api.service.CalendarioService;

@RestController
@RequestMapping("/api/calendario")
@CrossOrigin(origins = "*")
public class CalendarioController {

    @Autowired
    private CalendarioService service;

    @GetMapping("/semanal/{estudianteId}")
    public List<CalendarioDTO> obtenerCalendario(
            @PathVariable Long estudianteId) {

        return service.obtenerCalendarioSemanal(estudianteId);
    }
}