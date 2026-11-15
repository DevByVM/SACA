package com.saca.api.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record BloqueHorarioEstudioResponse(
		Long id,
		LocalDate fecha,
		LocalTime inicio,
		LocalTime fin,
		int minutos,
		Long materiaInscritaId,
		String materia) {
}
