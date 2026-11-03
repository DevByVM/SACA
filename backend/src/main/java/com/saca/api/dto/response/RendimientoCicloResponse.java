package com.saca.api.dto.response;

import java.math.BigDecimal;
import java.util.List;

// cicloAcademicoId es null cuando no se indicó ciclo y el estudiante no tiene uno activo
public record RendimientoCicloResponse(
		Long cicloAcademicoId,
		String cicloAcademicoNombre,
		BigDecimal notaMinimaAprobacion,
		List<RendimientoMateriaResponse> materias) {
}
