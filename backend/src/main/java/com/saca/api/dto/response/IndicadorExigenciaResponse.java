package com.saca.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.saca.api.entity.NivelCarga;

public record IndicadorExigenciaResponse(
		// Suma de porcentajes de materias distintas: indica presión semanal, no la nota de ninguna materia
		BigDecimal pesoAcumulado,
		BigDecimal pesoMaximo,
		int evaluacionesAltoPeso,
		boolean concentracion,
		List<LocalDate> diasConcentracion,
		NivelCarga nivel,
		BigDecimal umbralAltoPeso,
		BigDecimal umbralElevada,
		BigDecimal umbralCritica) {
}
