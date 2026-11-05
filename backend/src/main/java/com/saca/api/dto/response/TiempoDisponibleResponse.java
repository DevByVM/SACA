package com.saca.api.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Tiempo de estudio disponible del periodo [desde, hasta]. Es informativo: no interviene en la
// carga académica ni en sus niveles. Los días anteriores a hoy no se incluyen y hoy se cuenta desde
// calculadoDesde, el momento de la consulta; si el periodo empieza en el futuro, calculadoDesde es su inicio.
public record TiempoDisponibleResponse(
		LocalDate desde,
		LocalDate hasta,
		LocalDateTime calculadoDesde,
		int minutosReservados,
		int minutosDescontados,
		int minutosDisponibles,
		List<TiempoDisponibleDiaResponse> dias) {
}
