package com.saca.api.dto.response;

import java.time.LocalDate;

// Tiempo de estudio de un día (SDACAS-32): reservado = disponible + descontado
public record TiempoDisponibleDiaResponse(
		LocalDate fecha,
		int minutosReservados,
		int minutosDescontados,
		int minutosDisponibles) {
}
