package com.saca.api.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CrearActividadAcademicaRequest(
        @NotBlank(message = "El nombre de la actividad no debe estar vacio")
        @Size(max = 80, message = "El nombre debe tener máximo 80 caracteres")
        String nombre,

        @NotNull(message = "La Fecha de entrega es obligatoria")
        @FutureOrPresent(message = "La fecha de entrega no debe ser menor a hoy")
        LocalDate fechaEntrega,

        @Min(value = 0, message = "El minimo de porcentaje es 1")
        @Max(value = 100, message = "El maximo de porcentaje es 2")
        Double porcentajeEvaluacion,

        @NotNull(message = "El estimado de horas es obligatorio")
        Double tiempoEstimadoHoras,

        @NotNull(message = "El estado es obligatorio")
        String estado,

        @PastOrPresent(message = "La fecha no debe ser mayor a hoy")
        LocalDate fechaCompletada,

        @NotNull(message = "El tipo de Actividad es obligatorio")
        Integer tipoActividadId,
        @NotNull(message = "La materia es obligatoria")
        Long materiaInscritaId,
        @Min(value = 1)
        Integer notaId
) {

}
