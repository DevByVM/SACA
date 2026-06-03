package com.saca.api.dto.response;

import java.time.LocalDate;

import com.saca.api.entity.EstadoCiclo;

public record CicloAcademicoResponse(
		Long id,
		Long estudianteId,
		String nombre,
		Integer anio,
		LocalDate fechaInicio,
		LocalDate fechaFin,
		EstadoCiclo estado) {
}
