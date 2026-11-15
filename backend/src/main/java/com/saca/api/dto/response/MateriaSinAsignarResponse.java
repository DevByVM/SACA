package com.saca.api.dto.response;

public record MateriaSinAsignarResponse(
		Long materiaInscritaId,
		String materia,
		Double horasSemanalesRecomendadas,
		Double horasSemanalesProgramadas,
		Double horasSemanalesSinProgramar,
		MotivoSinAsignar motivo) {
}
