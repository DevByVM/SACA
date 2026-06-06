package com.saca.api.dto.response;

import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividad;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

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
