package com.saca.api.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.saca.api.entity.SituacionRendimiento;

public record RendimientoMateriaResponse(
		Long materiaInscritaId,
		String codigo,
		String nombre,
		BigDecimal pesoCalificado,
		BigDecimal pesoPendiente,
		BigDecimal pesoSinAsignar,
		BigDecimal promedioPonderado,
		BigDecimal notaAcumulada,
		BigDecimal notaMaximaAlcanzable,
		BigDecimal notaMinima,
		BigDecimal notaNecesaria,
		SituacionRendimiento situacion,
		boolean planIncompleto,
		List<ActividadRendimientoResponse> actividades) {
}
