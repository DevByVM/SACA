package com.saca.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

// Indicadores de un día del calendario. Las horas pendientes y la concentración usan las reglas y
// umbrales de la carga semanal, solo con actividades pendientes; no dependen de los filtros.
public record ResumenDiaCalendarioResponse(
		LocalDate fecha,
		int clases,
		int actividades,
		BigDecimal horasPendientes,
		int evaluacionesAltoPeso,
		boolean concentracion) {
}
