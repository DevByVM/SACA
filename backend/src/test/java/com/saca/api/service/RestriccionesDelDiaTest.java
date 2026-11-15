package com.saca.api.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.saca.api.service.IntervalosMinutos.Intervalo;
import com.saca.api.service.RestriccionesDelDia.FranjaSemanal;

import static org.assertj.core.api.Assertions.assertThat;

class RestriccionesDelDiaTest {

	@Test
	void separaLasFranjasDePlantillaDeLasAlternativasYRestaRestricciones() {
		var ventanas = RestriccionesDelDia.calcularVentanas(
				DayOfWeek.MONDAY,
				List.of(franja(DayOfWeek.MONDAY, "09:00", "11:00")),
				true,
				List.of(franja(DayOfWeek.MONDAY, "08:00", "12:00")),
				true,
				List.of(franja(DayOfWeek.MONDAY, "10:00", "10:30")));

		assertThat(ventanas.preferidas()).containsExactly(i(9, 0, 10, 0), i(10, 30, 11, 0));
		assertThat(ventanas.alternativas()).containsExactly(i(8, 0, 9, 0), i(11, 0, 12, 0));
	}

	@Test
	void unaJornadaQueCruzaMedianocheBloqueaAmbosDias() {
		List<FranjaSemanal> jornada = List.of(franja(DayOfWeek.MONDAY, "22:00", "06:00"));

		assertThat(RestriccionesDelDia.intervalosDelDia(jornada, DayOfWeek.MONDAY))
				.containsExactly(new Intervalo(22 * 60, IntervalosMinutos.MINUTOS_DIA));
		assertThat(RestriccionesDelDia.intervalosDelDia(jornada, DayOfWeek.TUESDAY))
				.containsExactly(new Intervalo(0, 6 * 60));
	}

	@Test
	void diaSinRegistroUsaLaVentanaLibrePredeterminadaMenosLasRestricciones() {
		var ventanas = RestriccionesDelDia.calcularVentanas(
				DayOfWeek.MONDAY,
				List.of(),
				false,
				List.of(),
				false,
				List.of(franja(DayOfWeek.MONDAY, "09:00", "10:00")));

		assertThat(ventanas.preferidas()).isEmpty();
		assertThat(ventanas.alternativas()).containsExactly(i(6, 0, 9, 0), i(10, 0, 22, 0));
	}

	@Test
	void restarUneRestriccionesSolapadasAntesDeCrearHuecos() {
		assertThat(RestriccionesDelDia.restar(
				List.of(i(8, 0, 12, 0)),
				List.of(i(9, 0, 10, 0), i(9, 30, 11, 0))))
				.containsExactly(i(8, 0, 9, 0), i(11, 0, 12, 0));
	}

	private static FranjaSemanal franja(DayOfWeek dia, String inicio, String fin) {
		return new FranjaSemanal(dia, LocalTime.parse(inicio), LocalTime.parse(fin));
	}

	private static Intervalo i(int inicioHora, int inicioMinuto, int finHora, int finMinuto) {
		return new Intervalo(inicioHora * 60 + inicioMinuto, finHora * 60 + finMinuto);
	}
}
