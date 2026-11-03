package com.saca.api.service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.util.Optional;

import com.saca.api.entity.DiaSemana;

// Los días llegan en formatos distintos según el módulo: la jornada laboral usa «Miércoles», la
// disponibilidad semanal «MIERCOLES» y los horarios de clase el enum DiaSemana. Esta utilidad los
// compara sin tildes ni mayúsculas para que ninguna discrepancia de formato haga perder una franja.
public final class DiaSemanaTexto {

	private DiaSemanaTexto() {
	}

	public static String normalizar(String dia) {
		return Normalizer.normalize(dia.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
	}

	// Vacío si el texto no corresponde a un día de lunes a domingo
	public static Optional<DayOfWeek> aDia(String dia) {
		if (dia == null || dia.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(DayOfWeek.of(DiaSemana.valueOf(normalizar(dia)).ordinal() + 1));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
