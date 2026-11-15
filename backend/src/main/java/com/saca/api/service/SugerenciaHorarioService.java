package com.saca.api.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.saca.api.dto.response.BloqueSugeridoResponse;
import com.saca.api.dto.response.DiaHorarioSugeridoResponse;
import com.saca.api.dto.response.HorarioSugeridoResponse;
import com.saca.api.dto.response.MateriaSinAsignarResponse;
import com.saca.api.dto.response.MotivoSinAsignar;
import com.saca.api.dto.response.PeriodoHorarioSugeridoResponse;
import com.saca.api.dto.response.PlantillaHorarioSugeridaResponse;
import com.saca.api.dto.response.ResumenHorarioSugeridoResponse;
import com.saca.api.service.IntervalosMinutos.Intervalo;
import com.saca.api.service.RestriccionesDelDia.VentanasDisponibles;

@Service
public class SugerenciaHorarioService {

	private final Clock relojAplicacion;

	public SugerenciaHorarioService(Clock relojAplicacion) {
		this.relojAplicacion = Objects.requireNonNull(relojAplicacion);
	}

	public record MateriaActiva(
			Long materiaInscritaId,
			String materia,
			LocalDate cicloDesde,
			LocalDate cicloHasta) {
	}

	public record ActividadPendiente(
			Long materiaInscritaId,
			double horasEstimadas,
			LocalDateTime fechaInicio,
			LocalDateTime fechaEntrega,
			Integer tipoActividadId,
			double porcentajeEvaluacion) {
	}

	public HorarioSugeridoResponse generar(
			LocalDate desde,
			List<MateriaActiva> materiasActivas,
			List<ActividadPendiente> actividades,
			Map<LocalDate, VentanasDisponibles> ventanasPorFecha,
			PlantillaHorarioSugeridaResponse plantilla,
			String mensaje,
			List<String> advertenciasIniciales) {
		Objects.requireNonNull(desde);
		Objects.requireNonNull(materiasActivas);
		Objects.requireNonNull(actividades);
		Objects.requireNonNull(ventanasPorFecha);

		LocalDate hasta = desde.plusDays(SugerenciaHorarioParametros.DIAS_SEMANA - 1);
		LocalDateTime ahora = LocalDateTime.now(relojAplicacion);
		LinkedHashSet<String> advertencias = new LinkedHashSet<>(advertenciasIniciales);
		List<CargaMateria> cargas = cargarCargas(
				desde, hasta, ahora, materiasActivas, actividades, advertencias);
		List<DiaPlan> dias = new ArrayList<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
			dias.add(new DiaPlan(fecha));
		}

		boolean fueraDePlantilla = asignarEtapa(cargas, dias, ventanasPorFecha, true);
		fueraDePlantilla |= asignarEtapa(cargas, dias, ventanasPorFecha, false);
		if (fueraDePlantilla) {
			advertencias.add("FUERA_DE_PLANTILLA");
		}

		List<DiaHorarioSugeridoResponse> respuestaDias = dias.stream()
				.map(dia -> new DiaHorarioSugeridoResponse(
						dia.fecha,
						nombreDia(dia.fecha.getDayOfWeek()),
						dia.minutosTotales,
						dia.bloques.stream().map(BloquePlan::aRespuesta).toList()))
				.toList();
		List<MateriaSinAsignarResponse> sinAsignar = cargas.stream()
				.filter(carga -> carga.minutosAsignados < carga.minutosObjetivo)
				.map(SugerenciaHorarioService::crearMateriaSinAsignar)
				.toList();
		int minutosRecomendados = cargas.stream().mapToInt(carga -> carga.minutosObjetivo).sum();
		int minutosNoProgramados = cargas.stream()
				.mapToInt(carga -> Math.max(0, carga.minutosObjetivo - carga.minutosAsignados))
				.sum();

		return new HorarioSugeridoResponse(
				SugerenciaHorarioParametros.VERSION,
				ahora,
				SugerenciaHorarioParametros.ZONA_HORARIA,
				new PeriodoHorarioSugeridoResponse(desde, hasta),
				SugerenciaHorarioParametros.GRANULARIDAD_MINUTOS,
				plantilla,
				mensaje,
				respuestaDias,
				sinAsignar,
				new ResumenHorarioSugeridoResponse(
						minutosRecomendados, minutosNoProgramados, cargas.size()),
				List.copyOf(advertencias));
	}

	private List<CargaMateria> cargarCargas(
			LocalDate desde,
			LocalDate hasta,
			LocalDateTime ahora,
			List<MateriaActiva> materiasActivas,
			List<ActividadPendiente> actividades,
			LinkedHashSet<String> advertencias) {
		List<CargaMateria> cargas = materiasActivas.stream()
				.filter(materia -> materia.materiaInscritaId() != null)
				.map(CargaMateria::new)
				.toList();
		Map<Long, CargaMateria> porId = cargas.stream()
				.collect(java.util.stream.Collectors.toMap(
						carga -> carga.materiaInscritaId,
						carga -> carga,
						(anterior, duplicada) -> anterior,
						java.util.LinkedHashMap::new));

		for (ActividadPendiente actividad : actividades) {
			CargaMateria carga = porId.get(actividad.materiaInscritaId());
			if (carga == null) {
				continue;
			}
			LocalDateTime evaluacionFija = inicioEvaluacionFija(actividad);
			if (evaluacionFija != null
					&& !evaluacionFija.toLocalDate().isBefore(desde)
					&& !evaluacionFija.toLocalDate().isAfter(hasta)
					&& (carga.inicioEvaluacion == null || evaluacionFija.isBefore(carga.inicioEvaluacion))) {
				carga.inicioEvaluacion = evaluacionFija;
			}

			LocalDateTime fechaLimite = fechaLimiteEfectiva(actividad);
			if (fechaLimite == null) {
				advertencias.add("ACTIVIDAD_SIN_FECHA_LIMITE");
				continue;
			}
			if (!Double.isFinite(actividad.horasEstimadas()) || actividad.horasEstimadas() <= 0) {
				advertencias.add("TIEMPO_ESTIMADO_INVALIDO");
				continue;
			}
			if (fechaLimite.isBefore(ahora)) {
				advertencias.add("ACTIVIDAD_VENCIDA");
				continue;
			}
			if (fechaLimite.toLocalDate().isBefore(desde)) {
				advertencias.add("ACTIVIDAD_ANTES_DEL_PERIODO");
				continue;
			}

			long diasRestantes = Math.max(0, ChronoUnit.DAYS.between(ahora.toLocalDate(), fechaLimite.toLocalDate()));
			long semanasRestantes = Math.max(1, (diasRestantes + 6) / 7);
			carga.agregarCargaSemanal(actividad.horasEstimadas() / semanasRestantes);
			if (fechaLimite.isBefore(carga.fechaPrioritaria)) {
				carga.fechaPrioritaria = fechaLimite;
				carga.porcentajeEvaluacion = actividad.porcentajeEvaluacion();
			}
		}
		return cargas.stream()
				.sorted(Comparator
						.comparing((CargaMateria carga) -> carga.fechaPrioritaria)
						.thenComparing(Comparator.comparingDouble(
								(CargaMateria carga) -> carga.porcentajeEvaluacion).reversed())
						.thenComparing(carga -> carga.materia.toLowerCase(Locale.ROOT)))
				.toList();
	}

	private boolean asignarEtapa(
			List<CargaMateria> cargas,
			List<DiaPlan> dias,
			Map<LocalDate, VentanasDisponibles> ventanasPorFecha,
			boolean usarPreferidas) {
		boolean asignoEnAlternativa = false;
		for (DiaPlan dia : dias) {
			VentanasDisponibles ventanas = ventanasPorFecha.get(dia.fecha);
			if (ventanas == null) {
				continue;
			}
			List<Intervalo> intervalos = usarPreferidas ? ventanas.preferidas() : ventanas.alternativas();
			for (Intervalo intervalo : intervalos) {
				int cursor = redondearArriba(intervalo.inicio());
				int finVentana = Math.min(intervalo.fin(), IntervalosMinutos.MINUTOS_DIA - 1);
				while (cursor + SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS <= finVentana
						&& dia.minutosTotales < SugerenciaHorarioParametros.TOPE_DIARIO_MINUTOS) {
					int limiteVentana = finVentana;
					BloquePlan siguiente = null;
					for (BloquePlan bloque : dia.bloques) {
						if (bloque.fin + SugerenciaHorarioParametros.DESCANSO_ENTRE_SESIONES_MINUTOS <= cursor) {
							continue;
						}
						if (bloque.inicio <= cursor) {
							cursor = bloque.fin + SugerenciaHorarioParametros.DESCANSO_ENTRE_SESIONES_MINUTOS;
							siguiente = bloque;
							break;
						}
						limiteVentana = Math.min(
								limiteVentana,
								bloque.inicio - SugerenciaHorarioParametros.DESCANSO_ENTRE_SESIONES_MINUTOS);
						siguiente = bloque;
						break;
					}
					if (siguiente != null && siguiente.fin <= cursor) {
						continue;
					}

					CargaMateria carga = elegirCarga(cargas, dia.fecha, cursor, limiteVentana);
					if (carga == null) {
						if (siguiente != null) {
							cursor = Math.max(
									cursor + SugerenciaHorarioParametros.GRANULARIDAD_MINUTOS,
									siguiente.fin + SugerenciaHorarioParametros.DESCANSO_ENTRE_SESIONES_MINUTOS);
							continue;
						}
						break;
					}
					int limiteMateria = limiteParaMateria(carga, dia.fecha, limiteVentana);
					int minutosRestantes = carga.minutosObjetivo - carga.minutosAsignados;
					int minutosDiarios = SugerenciaHorarioParametros.TOPE_DIARIO_MINUTOS - dia.minutosTotales;
					int minutos = redondearAbajo(Math.min(
							Math.min(limiteMateria - cursor, minutosRestantes),
							Math.min(SugerenciaHorarioParametros.DURACION_MAXIMA_SESION_MINUTOS, minutosDiarios)));
					if (minutos < SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS) {
						if (siguiente != null) {
							cursor = siguiente.fin + SugerenciaHorarioParametros.DESCANSO_ENTRE_SESIONES_MINUTOS;
							continue;
						}
						break;
					}

					BloquePlan bloque = new BloquePlan(
							"materia-" + carga.materiaInscritaId + "-" + dia.fecha + "-" + cursor,
							cursor,
							cursor + minutos,
							minutos,
							carga.materiaInscritaId,
							carga.materia);
					dia.bloques.add(bloque);
					dia.bloques.sort(Comparator.comparingInt(actual -> actual.inicio));
					dia.minutosTotales += minutos;
					carga.minutosAsignados += minutos;
					cursor += minutos + SugerenciaHorarioParametros.DESCANSO_ENTRE_SESIONES_MINUTOS;
					if (!usarPreferidas) {
						asignoEnAlternativa = true;
					}
				}
			}
		}
		return asignoEnAlternativa;
	}

	private CargaMateria elegirCarga(List<CargaMateria> cargas, LocalDate fecha, int cursor, int limite) {
		return cargas.stream()
				.filter(carga -> carga.minutosAsignados < carga.minutosObjetivo)
				.filter(carga -> !fecha.isBefore(carga.cicloDesde) && !fecha.isAfter(carga.cicloHasta))
				.filter(carga -> carga.inicioEvaluacion == null
						|| !fecha.isAfter(carga.inicioEvaluacion.toLocalDate()))
				.filter(carga -> limiteParaMateria(carga, fecha, limite) - cursor
						>= SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS)
				.findFirst()
				.orElse(null);
	}

	private int limiteParaMateria(CargaMateria carga, LocalDate fecha, int limite) {
		if (carga.inicioEvaluacion != null && fecha.equals(carga.inicioEvaluacion.toLocalDate())) {
			return Math.min(limite, minuto(carga.inicioEvaluacion.toLocalTime()));
		}
		return limite;
	}

	private LocalDateTime inicioEvaluacionFija(ActividadPendiente actividad) {
		if ((Objects.equals(actividad.tipoActividadId(), 1) || Objects.equals(actividad.tipoActividadId(), 2))
				&& actividad.fechaInicio() != null
				&& actividad.fechaEntrega() != null
				&& actividad.fechaInicio().toLocalDate().equals(actividad.fechaEntrega().toLocalDate())
				&& actividad.fechaInicio().isBefore(actividad.fechaEntrega())) {
			return actividad.fechaInicio();
		}
		return null;
	}

	private LocalDateTime fechaLimiteEfectiva(ActividadPendiente actividad) {
		LocalDateTime evaluacion = inicioEvaluacionFija(actividad);
		return evaluacion != null ? evaluacion : actividad.fechaEntrega();
	}

	private static MateriaSinAsignarResponse crearMateriaSinAsignar(CargaMateria carga) {
		int minutosPendientes = Math.max(0, carga.minutosObjetivo - carga.minutosAsignados);
		return new MateriaSinAsignarResponse(
				carga.materiaInscritaId,
				carga.materia,
				carga.minutosObjetivo / 60.0,
				carga.minutosAsignados / 60.0,
				minutosPendientes / 60.0,
				MotivoSinAsignar.SIN_ESPACIO);
	}

	private static int minuto(LocalTime hora) {
		return hora.getHour() * 60 + hora.getMinute();
	}

	private static int redondearArriba(int minutos) {
		int granularidad = SugerenciaHorarioParametros.GRANULARIDAD_MINUTOS;
		return ((minutos + granularidad - 1) / granularidad) * granularidad;
	}

	private static int redondearAbajo(int minutos) {
		int granularidad = SugerenciaHorarioParametros.GRANULARIDAD_MINUTOS;
		return Math.max(0, (minutos / granularidad) * granularidad);
	}

	private static LocalTime hora(int minutos) {
		return LocalTime.of(minutos / 60, minutos % 60);
	}

	private static String nombreDia(DayOfWeek dia) {
		return switch (dia) {
			case MONDAY -> "LUNES";
			case TUESDAY -> "MARTES";
			case WEDNESDAY -> "MIERCOLES";
			case THURSDAY -> "JUEVES";
			case FRIDAY -> "VIERNES";
			case SATURDAY -> "SABADO";
			case SUNDAY -> "DOMINGO";
		};
	}

	private static final class CargaMateria {
		private final Long materiaInscritaId;
		private final String materia;
		private final LocalDate cicloDesde;
		private final LocalDate cicloHasta;
		private int minutosObjetivo = SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS;
		private int minutosAsignados;
		private double horasSemanales;
		private double porcentajeEvaluacion;
		private LocalDateTime fechaPrioritaria = LocalDateTime.MAX;
		private LocalDateTime inicioEvaluacion;

		private CargaMateria(MateriaActiva materiaActiva) {
			materiaInscritaId = materiaActiva.materiaInscritaId();
			materia = materiaActiva.materia();
			cicloDesde = materiaActiva.cicloDesde();
			cicloHasta = materiaActiva.cicloHasta();
		}

		private void agregarCargaSemanal(double horas) {
			horasSemanales += horas;
			int minutos = (int) Math.ceil(horasSemanales * 60);
			int granularidad = SugerenciaHorarioParametros.GRANULARIDAD_MINUTOS;
			minutosObjetivo = Math.max(
					SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS,
					((minutos + granularidad - 1) / granularidad) * granularidad);
		}
	}

	private static final class DiaPlan {
		private final LocalDate fecha;
		private final List<BloquePlan> bloques = new ArrayList<>();
		private int minutosTotales;

		private DiaPlan(LocalDate fecha) {
			this.fecha = fecha;
		}
	}

	private static final class BloquePlan {
		private final String clave;
		private final int inicio;
		private final int fin;
		private final int minutos;
		private final Long materiaInscritaId;
		private final String materia;

		private BloquePlan(
				String clave,
				int inicio,
				int fin,
				int minutos,
				Long materiaInscritaId,
				String materia) {
			this.clave = clave;
			this.inicio = inicio;
			this.fin = fin;
			this.minutos = minutos;
			this.materiaInscritaId = materiaInscritaId;
			this.materia = materia;
		}

		private BloqueSugeridoResponse aRespuesta() {
			return new BloqueSugeridoResponse(
					clave,
					hora(inicio),
					hora(fin),
					minutos,
					materiaInscritaId,
					materia);
		}
	}
}
