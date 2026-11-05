package com.saca.api.dto.response;

import java.time.LocalDate;

// Tiempo de estudio de un día: reservado = disponible + descontado
public record TiempoDisponibleDiaResponse(
		LocalDate fecha,
		int minutosReservados,
		int minutosDescontados,
		int minutosDisponibles) {
}
