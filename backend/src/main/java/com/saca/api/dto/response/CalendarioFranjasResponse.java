package com.saca.api.dto.response;

import java.time.LocalDate;
import java.util.List;

// Franjas de jornada laboral y disponibilidad (NO_DISPONIBLE y ESTUDIO) del periodo, ordenadas por fecha y hora
public record CalendarioFranjasResponse(
		LocalDate desde,
		LocalDate hasta,
		List<FranjaCalendarioResponse> franjas) {
}
