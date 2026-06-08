package com.saca.api.dto.response;

import com.saca.api.entity.TipoActividadAcademica;

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
        TipoActividadAcademica tipoActividad,
        Long materiaInscritaId,
        Integer notaId
) {
}
