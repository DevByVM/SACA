package com.saca.api.dto.response;

import java.time.LocalDate;
import java.util.List;

// Carga semana a semana desde la semana actual hasta el fin del ciclo activo.
// Sin ciclo activo, o con el ciclo activo ya terminado, la lista de semanas queda vacía.
public record CargaPorSemanasResponse(
		boolean cicloActivo,
		// Inicio más temprano y fin más tardío de los ciclos activos; null si no hay ciclo activo
		LocalDate inicioCiclo,
		LocalDate finCiclo,
		List<SemanaCargaResponse> semanas,
		boolean semanasLimitadas,
		int actividadesSinFecha) {
}
