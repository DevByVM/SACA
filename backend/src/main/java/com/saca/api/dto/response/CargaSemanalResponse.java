package com.saca.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.saca.api.entity.NivelCarga;

public record CargaSemanalResponse(
		LocalDateTime desde,
		LocalDateTime hasta,
		NivelCarga nivelGeneral,
		IndicadorHorasResponse horas,
		IndicadorExigenciaResponse exigencia,
		// Explicación en lenguaje natural de cada indicador y de la clasificación general
		List<String> motivos,
		List<ActividadAcademicaResponse> actividadesPendientes,
		// Completadas o ya calificadas dentro de la ventana: no suman carga, pero se pueden consultar
		List<ActividadAcademicaResponse> actividadesNoPendientes,
		int actividadesSinFecha,
		int actividadesSinPorcentaje) {
}
