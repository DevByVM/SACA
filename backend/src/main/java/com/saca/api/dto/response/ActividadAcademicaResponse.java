package com.saca.api.dto.response;

import java.time.LocalDateTime;

public record ActividadAcademicaResponse(
        Long idActividad,
        String nombre,
        LocalDateTime fechaInicio,
        LocalDateTime fechaEntrega,
        Double porcentajeEvaluacion,
        Double tiempoEstimadoHoras,
        String estado,
        LocalDateTime fechaCompletada,
        Integer tipoActividadId,
        Long materiaInscritaId,
        Integer notaId,
        String tipoActividadNombre,
        String materiaInscritaCodigo


) {
}
