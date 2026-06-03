package com.saca.api.dto.request;

import java.time.LocalDate;

import com.saca.api.entity.EstadoCiclo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarCicloAcademicoRequest(
		@NotBlank(message = "El nombre del ciclo es obligatorio")
		@Size(max = 80, message = "El nombre del ciclo no debe superar 80 caracteres")
		String nombre,

		@NotNull(message = "El anio es obligatorio")
		@Min(value = 2000, message = "El anio debe ser mayor o igual a 2000")
		@Max(value = 2100, message = "El anio debe ser menor o igual a 2100")
		Integer anio,

		@NotNull(message = "La fecha de inicio es obligatoria")
		LocalDate fechaInicio,

		@NotNull(message = "La fecha de fin es obligatoria")
		LocalDate fechaFin,

		@NotNull(message = "El estado del ciclo es obligatorio")
		EstadoCiclo estado) {
}
