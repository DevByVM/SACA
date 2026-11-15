package com.saca.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record HorarioSugeridoResponse(
		int version,
		LocalDateTime generadoEn,
		String zonaHoraria,
		PeriodoHorarioSugeridoResponse periodo,
		int granularidadMinutos,
		PlantillaHorarioSugeridaResponse plantilla,
		String mensaje,
		List<DiaHorarioSugeridoResponse> dias,
		List<MateriaSinAsignarResponse> sinAsignar,
		ResumenHorarioSugeridoResponse resumen,
		List<String> advertencias) {
}
