package com.saca.api.dto.response;

import java.time.LocalDate;
import java.util.List;

// Periodo arbitrario del calendario (máximo 42 días, una cuadrícula mensual completa)
public record CalendarioPeriodoResponse(
		LocalDate desde,
		LocalDate hasta,
		List<EventoCalendarioResponse> eventos,
		// Un resumen por cada día del periodo, en orden
		List<ResumenDiaCalendarioResponse> dias,
		int actividadesSinFecha) {
}
