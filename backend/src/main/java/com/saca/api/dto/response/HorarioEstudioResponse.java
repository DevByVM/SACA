package com.saca.api.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HorarioEstudioResponse(
		Long id,
		LocalDate desde,
		LocalDate hasta,
		LocalDateTime confirmadoEn,
		int minutosTotales,
		List<BloqueHorarioEstudioResponse> bloques) {
}
