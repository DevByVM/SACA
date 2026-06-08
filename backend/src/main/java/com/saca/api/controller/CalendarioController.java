package com.saca.api.controller;

// Importaciones necesarias
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.saca.api.dto.CalendarioDTO;
import com.saca.api.service.CalendarioService;

/**
 * Controlador encargado de gestionar las solicitudes
 * relacionadas con el calendario académico.
 *
 * Este controlador que expone endpoints REST que permiten
 * consultar el horario semanal de un estudiante.
 */
@RestController
@RequestMapping("/api/calendario")
@CrossOrigin(origins = "*") // Permite peticiones desde cualquier origen
public class CalendarioController {

    /**
     * Inyección automática del servicio encargado
     * de la lógica de negocio del calendario.
     */
    @Autowired
    private CalendarioService service;

    /**
     * Obtiene el calendario semanal de un estudiante.
     *
     * Endpoint:
     * GET /api/calendario/semanal/{estudianteId}
     *
     * Parámetro:
     * estudianteId -> Identificador único del estudiante.
     *
     * Ejemplo:
     * GET /api/calendario/semanal/1
     *
     * Retorna:
     * Una lista de objetos CalendarioDTO con la información
     * de las materias y horarios del estudiante.
     *
     * @param estudianteId ID del estudiante
     * @return Lista de eventos del calendario semanal
     */
    @GetMapping("/semanal/{estudianteId}")
    public List<CalendarioDTO> obtenerCalendario(
            @PathVariable Long estudianteId) {

        // Delega la consulta al servicio
        return service.obtenerCalendarioSemanal(estudianteId);
    }
}