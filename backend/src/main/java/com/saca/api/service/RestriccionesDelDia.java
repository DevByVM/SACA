package com.saca.api.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.saca.api.service.IntervalosMinutos.Intervalo;

public final class RestriccionesDelDia {

	private RestriccionesDelDia() {
	}

	public record FranjaSemanal(DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFin) {
		public FranjaSemanal {
			Objects.requireNonNull(diaSemana);
			Objects.requireNonNull(horaInicio);
			Objects.requireNonNull(horaFin);
		}
	}

	public record VentanasDisponibles(List<Intervalo> preferidas, List<Intervalo> alternativas) {
		public VentanasDisponibles {
			preferidas = List.copyOf(preferidas);
			alternativas = List.copyOf(alternativas);
		}
	}

	public static VentanasDisponibles calcularVentanas(
			DayOfWeek dia,
			List<FranjaSemanal> franjasEstudioPlantilla,
			boolean plantillaActiva,
			List<FranjaSemanal> franjasEstudioOLibre,
			boolean hayDisponibilidadRegistrada,
			List<FranjaSemanal> restricciones) {
		Objects.requireNonNull(dia);

		List<Intervalo> ventanasBase = hayDisponibilidadRegistrada
				? intervalosDelDia(franjasEstudioOLibre, dia)
				: List.of(new Intervalo(
						minuto(SugerenciaHorarioParametros.INICIO_VENTANA_PROVISIONAL),
						minuto(SugerenciaHorarioParametros.FIN_VENTANA_PROVISIONAL)));
		List<Intervalo> abiertas = restar(ventanasBase, intervalosDelDia(restricciones, dia));
		List<Intervalo> preferidas = plantillaActiva
				? IntervalosMinutos.interseccion(abiertas, intervalosDelDia(franjasEstudioPlantilla, dia))
				: List.of();

		return new VentanasDisponibles(preferidas, restar(abiertas, preferidas));
	}

	public static List<Intervalo> intervalosDelDia(List<FranjaSemanal> franjas, DayOfWeek dia) {
		Objects.requireNonNull(franjas);
		Objects.requireNonNull(dia);

		List<Intervalo> intervalos = new ArrayList<>();
		for (FranjaSemanal franja : franjas) {
			int inicio = minuto(franja.horaInicio());
			int fin = minuto(franja.horaFin());
			if (inicio < fin) {
				if (franja.diaSemana() == dia) {
					intervalos.add(new Intervalo(inicio, fin));
				}
			} else if (inicio > fin) {
				if (franja.diaSemana() == dia) {
					intervalos.add(new Intervalo(inicio, IntervalosMinutos.MINUTOS_DIA));
				}
				if (franja.diaSemana().plus(1) == dia && fin > 0) {
					intervalos.add(new Intervalo(0, fin));
				}
			}
		}
		return IntervalosMinutos.unir(intervalos);
	}

	public static List<Intervalo> restar(List<Intervalo> ventanas, List<Intervalo> ocupados) {
		List<Intervalo> resultado = new ArrayList<>();
		List<Intervalo> basesUnidas = IntervalosMinutos.unir(ventanas);
		List<Intervalo> bloqueosUnidos = IntervalosMinutos.unir(ocupados);
		int indiceBloqueo = 0;

		for (Intervalo base : basesUnidas) {
			int cursor = base.inicio();
			while (indiceBloqueo < bloqueosUnidos.size()
					&& bloqueosUnidos.get(indiceBloqueo).fin() <= cursor) {
				indiceBloqueo++;
			}

			int indiceActual = indiceBloqueo;
			while (indiceActual < bloqueosUnidos.size()) {
				Intervalo bloqueo = bloqueosUnidos.get(indiceActual);
				if (bloqueo.inicio() >= base.fin()) {
					break;
				}
				if (bloqueo.inicio() > cursor) {
					resultado.add(new Intervalo(cursor, Math.min(bloqueo.inicio(), base.fin())));
				}
				cursor = Math.max(cursor, bloqueo.fin());
				if (cursor >= base.fin()) {
					break;
				}
				indiceActual++;
			}

			if (cursor < base.fin()) {
				resultado.add(new Intervalo(cursor, base.fin()));
			}
		}
		return List.copyOf(resultado);
	}

	private static int minuto(LocalTime hora) {
		return hora.getHour() * 60 + hora.getMinute();
	}
}
