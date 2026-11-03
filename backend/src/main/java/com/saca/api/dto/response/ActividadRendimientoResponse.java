package com.saca.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.saca.api.entity.TipoActividadAcademica;

public record ActividadRendimientoResponse(
		Long idActividad,
		String nombre,
		TipoActividadAcademica tipoActividad,
		String estado,
		LocalDateTime fechaEntrega,
		BigDecimal porcentajeEvaluacion,
		BigDecimal nota,
		boolean calificada,
		boolean sinPonderacion,
		// Puntos que la nota aporta a la nota final: nota × porcentaje / 100
		BigDecimal aporte) {
}
