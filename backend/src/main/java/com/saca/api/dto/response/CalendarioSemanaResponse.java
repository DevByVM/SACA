package com.saca.api.dto.response;

import java.time.LocalDate;
import java.util.List;

// Semana de lunes (inicio) a domingo (fin), con los eventos ordenados por fecha y hora
public record CalendarioSemanaResponse(
		LocalDate inicio,
		LocalDate fin,
		List<EventoCalendarioResponse> eventos,
		int actividadesSinFecha) {
}
