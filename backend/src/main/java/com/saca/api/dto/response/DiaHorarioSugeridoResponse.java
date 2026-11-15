package com.saca.api.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DiaHorarioSugeridoResponse(
		LocalDate fecha,
		String diaSemana,
		int minutosTotales,
		List<BloqueSugeridoResponse> bloques) {
}
