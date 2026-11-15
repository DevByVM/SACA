package com.saca.api.dto.response;

import java.time.LocalTime;

public record BloqueSugeridoResponse(
		String clave,
		LocalTime inicio,
		LocalTime fin,
		int minutos,
		Long materiaInscritaId,
		String materia) {
}
