package com.saca.api.controller;

// Importaciones necesarias
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.CalendarioDTO;
import com.saca.api.dto.response.CalendarioPeriodoResponse;
import com.saca.api.dto.response.CalendarioSemanaResponse;
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
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long estudianteId) {

        // Delega la consulta al servicio; solo se admite el estudianteId del token (otro responde 404)
        return service.obtenerCalendarioSemanal(EstudianteAutenticado.idPropio(jwt, estudianteId));
    }

    // SDACAS-21: clases y actividades de la semana (lunes a domingo) que contiene la fecha indicada.
    // Sin fecha se usa la semana actual; el estudiante siempre es el del token.
    @GetMapping("/semana")
    public CalendarioSemanaResponse obtenerSemana(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return service.obtenerSemana(EstudianteAutenticado.id(jwt), fecha);
    }

    // Periodo de hasta 42 días (cuadrícula mensual) con los eventos y un resumen por día
    @GetMapping("/periodo")
    public CalendarioPeriodoResponse obtenerPeriodo(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.obtenerPeriodo(EstudianteAutenticado.id(jwt), desde, hasta);
    }
}