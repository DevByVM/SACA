package com.saca.api.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.service.IntervalosMinutos.Intervalo;

// Tiempo de estudio disponible en una ventana de fechas. Es un dato informativo:
// no interviene en la carga académica, en sus niveles ni en sus umbrales (semanal ni por semanas del ciclo).
//
// Para cada día de la ventana:
// - reservado: unión de los bloques ESTUDIO de ciclos ACTIVO, con el día dentro de las fechas de su ciclo;
// - restricciones: unión de las clases (ciclos ACTIVO y día dentro de su ciclo, de cualquier ciclo activo),
//   las jornadas laborales del día y los bloques NO_DISPONIBLE con la misma regla que ESTUDIO;
// - descontado: minutos de la intersección entre reservado y restricciones;
// - disponible: reservado - descontado.
// Al trabajar con uniones, un minuto nunca se cuenta ni se descuenta dos veces. Los bloques CLASE y LIBRE de
// la disponibilidad no intervienen: las clases se toman de los horarios reales.
@Service
public class TiempoDisponibleService {

	public record Dia(LocalDate fecha, int minutosReservados, int minutosDescontados, int minutosDisponibles) {
	}

	public record Resultado(
			LocalDateTime desde,
			LocalDateTime hasta,
			int minutosReservados,
			int minutosDescontados,
			int minutosDisponibles,
			List<Dia> dias) {
	}

	// Cálculo puro sobre [desde, hasta]. Para la semana actual, desde es el momento de la consulta y no se
	// cuenta tiempo pasado; para una semana futura, desde es el lunes a las 00:00 y hasta el domingo al final
	// del día. Los segundos se descartan: se trabaja con minutos enteros.
	public Resultado calcular(
			LocalDateTime desde,
			LocalDateTime hasta,
			List<DisponibilidadSemanal> bloques,
			List<CicloAcademico> ciclos,
			List<HorarioClase> horarios,
			List<JornadaLaboral> jornadas) {
		Map<Long, CicloAcademico> ciclosActivos = ciclos.stream()
				.filter(ciclo -> ciclo.getEstado() == EstadoCiclo.ACTIVO)
				.collect(Collectors.toMap(CicloAcademico::getId, Function.identity(), (a, b) -> a));

		List<Dia> dias = new ArrayList<>();
		for (LocalDate fecha = desde.toLocalDate(); !fecha.isAfter(hasta.toLocalDate()); fecha = fecha.plusDays(1)) {
			int inicioVentana = fecha.equals(desde.toLocalDate()) ? minutoDeInicio(desde.toLocalTime()) : 0;
			int finVentana = fecha.equals(hasta.toLocalDate()) ? minutoDeFin(hasta.toLocalTime()) : IntervalosMinutos.MINUTOS_DIA;

			List<Intervalo> reservado = IntervalosMinutos.recortar(
					bloquesDelDia(bloques, "ESTUDIO", ciclosActivos, fecha), inicioVentana, finVentana);

			List<Intervalo> restricciones = new ArrayList<>(bloquesDelDia(bloques, "NO_DISPONIBLE", ciclosActivos, fecha));
			restricciones.addAll(clasesDelDia(horarios, fecha));
			restricciones.addAll(jornadasDelDia(jornadas, fecha));

			int minutosReservados = IntervalosMinutos.duracion(reservado);
			int minutosDescontados = IntervalosMinutos.duracion(IntervalosMinutos.interseccion(reservado, restricciones));
			dias.add(new Dia(fecha, minutosReservados, minutosDescontados, minutosReservados - minutosDescontados));
		}

		int reservados = dias.stream().mapToInt(Dia::minutosReservados).sum();
		int descontados = dias.stream().mapToInt(Dia::minutosDescontados).sum();
		return new Resultado(desde, hasta, reservados, descontados, reservados - descontados, List.copyOf(dias));
	}

	private static List<Intervalo> bloquesDelDia(
			List<DisponibilidadSemanal> bloques, String tipo, Map<Long, CicloAcademico> ciclosActivos, LocalDate fecha) {
		List<Intervalo> intervalos = new ArrayList<>();
		for (DisponibilidadSemanal bloque : bloques) {
			CicloAcademico ciclo = ciclosActivos.get(bloque.getCicloId());
			if (bloque.getTipoBloque() != null
					&& tipo.equals(bloque.getTipoBloque().trim().toUpperCase())
					&& ciclo != null
					&& dentroDelCiclo(fecha, ciclo)
					&& esDelDia(bloque.getDiaSemana(), fecha.getDayOfWeek())) {
				agregar(intervalos, bloque.getHoraInicio(), bloque.getHoraFin());
			}
		}
		return intervalos;
	}

	// Misma regla que el calendario: ciclo ACTIVO y fecha dentro del ciclo de la materia
	private static List<Intervalo> clasesDelDia(List<HorarioClase> horarios, LocalDate fecha) {
		List<Intervalo> intervalos = new ArrayList<>();
		for (HorarioClase horario : horarios) {
			CicloAcademico ciclo = horario.getMateriaInscrita() == null ? null : horario.getMateriaInscrita().getCicloAcademico();
			if (ciclo != null
					&& ciclo.getEstado() == EstadoCiclo.ACTIVO
					&& dentroDelCiclo(fecha, ciclo)
					&& horario.getDiaSemana() != null
					&& horario.getDiaSemana().ordinal() + 1 == fecha.getDayOfWeek().getValue()) {
				agregar(intervalos, horario.getHoraInicio(), horario.getHoraFin());
			}
		}
		return intervalos;
	}

	// La jornada laboral no pertenece a un ciclo: cuenta todas las semanas en su día
	private static List<Intervalo> jornadasDelDia(List<JornadaLaboral> jornadas, LocalDate fecha) {
		List<Intervalo> intervalos = new ArrayList<>();
		for (JornadaLaboral jornada : jornadas) {
			if (esDelDia(jornada.getDiaSemana(), fecha.getDayOfWeek())) {
				agregar(intervalos, jornada.getHoraInicio(), jornada.getHoraFin());
			}
		}
		return intervalos;
	}

	// Un registro sin horas o con inicio >= fin (no admitido por las validaciones, pero posible en datos
	// antiguos) no aporta minutos en lugar de producir valores negativos
	private static void agregar(List<Intervalo> intervalos, LocalTime inicio, LocalTime fin) {
		if (inicio != null && fin != null && inicio.isBefore(fin)) {
			intervalos.add(new Intervalo(minuto(inicio), minuto(fin)));
		}
	}

	private static int minuto(LocalTime hora) {
		return hora.getHour() * 60 + hora.getMinute();
	}

	// El minuto en curso ya empezó: se cuenta desde el siguiente para no incluir tiempo pasado
	private static int minutoDeInicio(LocalTime hora) {
		int minuto = minuto(hora);
		return hora.getSecond() > 0 || hora.getNano() > 0 ? minuto + 1 : minuto;
	}

	// LocalTime.MAX (23:59:59.999…) representa el final del día
	private static int minutoDeFin(LocalTime hora) {
		return hora.isAfter(LocalTime.of(23, 59, 59)) ? IntervalosMinutos.MINUTOS_DIA : minuto(hora);
	}

	private static boolean esDelDia(String texto, DayOfWeek dia) {
		return DiaSemanaTexto.aDia(texto).map(reconocido -> reconocido == dia).orElse(false);
	}

	private static boolean dentroDelCiclo(LocalDate fecha, CicloAcademico ciclo) {
		return (ciclo.getFechaInicio() == null || !fecha.isBefore(ciclo.getFechaInicio()))
				&& (ciclo.getFechaFin() == null || !fecha.isAfter(ciclo.getFechaFin()));
	}
}
