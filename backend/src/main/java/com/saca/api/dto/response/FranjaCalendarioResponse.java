package com.saca.api.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

// Franja de tiempo de un día concreto en el calendario (SDACAS-32). La jornada laboral y la
// disponibilidad semanal son registros independientes; aquí solo se proyectan sobre las fechas.
public record FranjaCalendarioResponse(
		LocalDate fecha,
		LocalTime horaInicio,
		LocalTime horaFin,
		Tipo tipo,
		// Id de la jornada laboral o del bloque de disponibilidad del que procede
		Long origenId) {

	public enum Tipo {
		// Tiempo restringido para estudiar
		JORNADA_LABORAL,
		NO_DISPONIBLE,
		// Tiempo reservado para estudiar
		ESTUDIO
	}
}
