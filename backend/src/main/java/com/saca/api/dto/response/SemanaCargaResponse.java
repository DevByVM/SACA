package com.saca.api.dto.response;

import java.time.LocalDate;

// Una semana de lunes (inicio) a domingo (fin). En la semana actual la carga se calcula desde el momento
// de la consulta, así que no incluye actividades ya vencidas.
public record SemanaCargaResponse(
		LocalDate inicio,
		LocalDate fin,
		boolean semanaActual,
		CargaSemanalResponse carga) {
}
