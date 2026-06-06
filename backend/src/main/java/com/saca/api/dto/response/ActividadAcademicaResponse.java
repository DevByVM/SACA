package com.saca.api.dto.response;

import java.time.LocalDate;

public record ActividadAcademicaResponse(
        Long idActividad,
        String nombre,
        LocalDate fechaEntrega,
        Double porcentajeEvaluacion,
        Double tiempoEstimadoHoras,
        String estado,
        LocalDate fechaCompletada,
        Integer tipoActividadId,
        Long materiaInscrita,
        Integer nota
) {
}
