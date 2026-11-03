package com.saca.api.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Operaciones sobre intervalos de un mismo día expresados en minutos desde las 00:00 (0 a 1440).
// Los intervalos son semiabiertos [inicio, fin): 08:00-09:00 y 09:00-10:00 se tocan pero no se solapan,
// igual que en las validaciones de disponibilidad, jornada y horarios de clase.
public final class IntervalosMinutos {

	public static final int MINUTOS_DIA = 24 * 60;

	private IntervalosMinutos() {
	}

	public record Intervalo(int inicio, int fin) {
		public int duracion() {
			return fin - inicio;
		}
	}

	// Ordena y fusiona los intervalos que se solapan o se tocan; descarta los vacíos o invertidos.
	// El resultado no tiene solapamientos, así que ningún minuto se cuenta dos veces.
	public static List<Intervalo> unir(List<Intervalo> intervalos) {
		List<Intervalo> ordenados = intervalos.stream()
				.filter(intervalo -> intervalo.inicio() < intervalo.fin())
				.sorted(Comparator.comparingInt(Intervalo::inicio).thenComparingInt(Intervalo::fin))
				.toList();

		List<Intervalo> unidos = new ArrayList<>();
		for (Intervalo actual : ordenados) {
			if (!unidos.isEmpty() && actual.inicio() <= unidos.get(unidos.size() - 1).fin()) {
				Intervalo ultimo = unidos.remove(unidos.size() - 1);
				unidos.add(new Intervalo(ultimo.inicio(), Math.max(ultimo.fin(), actual.fin())));
			} else {
				unidos.add(actual);
			}
		}
		return List.copyOf(unidos);
	}

	// Recorta los intervalos a la ventana [desde, hasta); lo que queda fuera se descarta
	public static List<Intervalo> recortar(List<Intervalo> intervalos, int desde, int hasta) {
		List<Intervalo> recortados = new ArrayList<>();
		for (Intervalo intervalo : intervalos) {
			int inicio = Math.max(intervalo.inicio(), desde);
			int fin = Math.min(intervalo.fin(), hasta);
			if (inicio < fin) {
				recortados.add(new Intervalo(inicio, fin));
			}
		}
		return unir(recortados);
	}

	// Intersección de dos conjuntos de intervalos; cada uno se une antes, así que el resultado no se
	// solapa aunque las entradas sí lo hagan
	public static List<Intervalo> interseccion(List<Intervalo> a, List<Intervalo> b) {
		List<Intervalo> x = unir(a);
		List<Intervalo> y = unir(b);
		List<Intervalo> comunes = new ArrayList<>();
		int i = 0;
		int j = 0;
		while (i < x.size() && j < y.size()) {
			int inicio = Math.max(x.get(i).inicio(), y.get(j).inicio());
			int fin = Math.min(x.get(i).fin(), y.get(j).fin());
			if (inicio < fin) {
				comunes.add(new Intervalo(inicio, fin));
			}
			if (x.get(i).fin() < y.get(j).fin()) {
				i++;
			} else {
				j++;
			}
		}
		return List.copyOf(comunes);
	}

	// Minutos cubiertos por la unión de los intervalos
	public static int duracion(List<Intervalo> intervalos) {
		return unir(intervalos).stream().mapToInt(Intervalo::duracion).sum();
	}
}
