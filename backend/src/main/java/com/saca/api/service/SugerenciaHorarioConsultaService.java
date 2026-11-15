package com.saca.api.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.response.HorarioSugeridoResponse;
import com.saca.api.dto.response.PeriodoHorarioSugeridoResponse;
import com.saca.api.dto.response.PlantillaHorarioSugeridaResponse;
import com.saca.api.dto.response.ResumenHorarioSugeridoResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.FranjaPlantillaPlanificacion;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.entity.PerfilPlantillaPlanificacion;
import com.saca.api.entity.PlantillaPlanificacion;
import com.saca.api.entity.TipoBloquePlantilla;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.JornadaLaboralRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.PlantillaPlanificacionRepository;
import com.saca.api.service.RestriccionesDelDia.FranjaSemanal;
import com.saca.api.service.RestriccionesDelDia.VentanasDisponibles;
import com.saca.api.service.SugerenciaHorarioService.ActividadPendiente;
import com.saca.api.service.SugerenciaHorarioService.MateriaActiva;

@Service
public class SugerenciaHorarioConsultaService {

	private final Clock relojAplicacion;
	private final SugerenciaHorarioService sugerenciaHorarioService;
	private final ActividadAcademicaRepository actividadAcademicaRepository;
	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final DisponibilidadSemanalRepository disponibilidadSemanalRepository;
	private final HorarioClaseRepository horarioClaseRepository;
	private final JornadaLaboralRepository jornadaLaboralRepository;
	private final MateriaInscritaRepository materiaInscritaRepository;
	private final PlantillaPlanificacionRepository plantillaPlanificacionRepository;

	public SugerenciaHorarioConsultaService(
			Clock relojAplicacion,
			SugerenciaHorarioService sugerenciaHorarioService,
			ActividadAcademicaRepository actividadAcademicaRepository,
			CicloAcademicoRepository cicloAcademicoRepository,
			DisponibilidadSemanalRepository disponibilidadSemanalRepository,
			HorarioClaseRepository horarioClaseRepository,
			JornadaLaboralRepository jornadaLaboralRepository,
			MateriaInscritaRepository materiaInscritaRepository,
			PlantillaPlanificacionRepository plantillaPlanificacionRepository) {
		this.relojAplicacion = relojAplicacion;
		this.sugerenciaHorarioService = sugerenciaHorarioService;
		this.actividadAcademicaRepository = actividadAcademicaRepository;
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.disponibilidadSemanalRepository = disponibilidadSemanalRepository;
		this.horarioClaseRepository = horarioClaseRepository;
		this.jornadaLaboralRepository = jornadaLaboralRepository;
		this.materiaInscritaRepository = materiaInscritaRepository;
		this.plantillaPlanificacionRepository = plantillaPlanificacionRepository;
	}

	@Transactional(readOnly = true)
	public HorarioSugeridoResponse obtener(Long estudianteId) {
		LocalDateTime ahora = LocalDateTime.now(relojAplicacion).truncatedTo(ChronoUnit.SECONDS);
		LocalDate desde = ahora.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		LocalDate hasta = desde.plusDays(SugerenciaHorarioParametros.DIAS_SEMANA - 1);
		List<CicloAcademico> ciclosActivos = cicloAcademicoRepository
				.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, EstadoCiclo.ACTIVO)
				.stream()
				.filter(ciclo -> !ciclo.getFechaFin().isBefore(desde) && !ciclo.getFechaInicio().isAfter(hasta))
				.toList();
		if (ciclosActivos.isEmpty()) {
			return sinCicloVigente(ahora, desde, hasta);
		}

		Map<Long, CicloAcademico> ciclosPorId = new HashMap<>();
		ciclosActivos.forEach(ciclo -> ciclosPorId.put(ciclo.getId(), ciclo));
		Set<Long> idsCiclosActivos = ciclosPorId.keySet();
		List<MateriaActiva> materiasActivas = materiaInscritaRepository
				.findByCicloAcademicoEstudianteIdOrderByNombreAsc(estudianteId)
				.stream()
				.filter(materia -> materia.getCicloAcademico() != null
						&& idsCiclosActivos.contains(materia.getCicloAcademico().getId()))
				.map(materia -> new MateriaActiva(
						materia.getId(),
						materia.getNombre(),
						materia.getCicloAcademico().getFechaInicio(),
						materia.getCicloAcademico().getFechaFin()))
				.toList();
		Set<Long> idsMateriasActivas = materiasActivas.stream()
				.map(MateriaActiva::materiaInscritaId)
				.collect(java.util.stream.Collectors.toSet());

		List<String> advertencias = new ArrayList<>();
		List<ActividadPendiente> actividades = actividadAcademicaRepository.findParaCalendario(estudianteId).stream()
				.filter(this::estaPendiente)
				.filter(actividad -> actividad.getMateriaInscrita() != null
						&& idsMateriasActivas.contains(actividad.getMateriaInscrita().getId()))
				.map(this::aActividadPendiente)
				.toList();

		List<JornadaLaboral> jornadas = jornadaLaboralRepository.findByEstudianteId(estudianteId);
		List<DisponibilidadSemanal> disponibilidades = disponibilidadSemanalRepository
				.findByEstudianteId(estudianteId);
		List<HorarioClase> horariosClase = horarioClaseRepository.findParaCalendario(estudianteId);

		PlantillaPlanificacion plantilla = buscarPlantilla(jornadas.isEmpty()
				? PerfilPlantillaPlanificacion.SIN_JORNADA_LABORAL
				: PerfilPlantillaPlanificacion.CON_JORNADA_LABORAL);
		boolean plantillaActiva = plantilla != null;
		PlantillaHorarioSugeridaResponse plantillaRespuesta = new PlantillaHorarioSugeridaResponse(
				plantillaActiva ? plantilla.getId() : null,
				!plantillaActiva);
		List<FranjaSemanal> franjasEstudioPlantilla = franjasEstudioPlantilla(plantilla, advertencias);
		if (!plantillaActiva) {
			advertencias.add("PLANTILLA_PROVISIONAL");
		}
		List<FranjaSemanal> franjasJornada = franjasJornada(jornadas, advertencias);

		Map<LocalDate, VentanasDisponibles> ventanasPorFecha = new HashMap<>();
		for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
			ventanasPorFecha.put(fecha, calcularVentanas(
					fecha,
					disponibilidades,
					horariosClase,
					ciclosPorId,
					franjasJornada,
					franjasEstudioPlantilla,
					plantillaActiva,
					actividades,
					advertencias));
		}

		return sugerenciaHorarioService.generar(
				desde,
				materiasActivas,
				actividades,
				ventanasPorFecha,
				plantillaRespuesta,
				null,
				advertencias);
	}

	private VentanasDisponibles calcularVentanas(
			LocalDate fecha,
			List<DisponibilidadSemanal> disponibilidades,
			List<HorarioClase> horariosClase,
			Map<Long, CicloAcademico> ciclosPorId,
			List<FranjaSemanal> jornadas,
			List<FranjaSemanal> franjasEstudioPlantilla,
			boolean plantillaActiva,
			List<ActividadPendiente> actividades,
			List<String> advertencias) {
		List<FranjaSemanal> ventanas = new ArrayList<>();
		List<FranjaSemanal> restricciones = new ArrayList<>(jornadas);
		boolean hayDisponibilidadRegistrada = false;
		LocalDate fechaAnterior = fecha.minusDays(1);

		for (DisponibilidadSemanal bloque : disponibilidades) {
			DayOfWeek dia = DiaSemanaTexto.aDia(bloque.getDiaSemana()).orElse(null);
			if (dia == null) {
				advertencias.add("DIA_DISPONIBILIDAD_INVALIDO");
				continue;
			}
			LocalDate fechaBloque;
			if (dia == fecha.getDayOfWeek()) {
				fechaBloque = fecha;
			} else if (dia == fechaAnterior.getDayOfWeek()) {
				fechaBloque = fechaAnterior;
			} else {
				continue;
			}
			CicloAcademico ciclo = ciclosPorId.get(bloque.getCicloId());
			if (!cicloActivoEn(ciclo, fechaBloque)) {
				continue;
			}
			if (fechaBloque.equals(fecha)) {
				hayDisponibilidadRegistrada = true;
			}

			FranjaSemanal franja = new FranjaSemanal(dia, bloque.getHoraInicio(), bloque.getHoraFin());
			String tipo = bloque.getTipoBloque() == null
					? ""
					: bloque.getTipoBloque().trim().toUpperCase(Locale.ROOT);
			if ("ESTUDIO".equals(tipo) || "LIBRE".equals(tipo)) {
				ventanas.add(franja);
			} else {
				restricciones.add(franja);
				if (!"CLASE".equals(tipo) && !"NO_DISPONIBLE".equals(tipo)) {
					advertencias.add("TIPO_DISPONIBILIDAD_DESCONOCIDO");
				}
			}
		}

		for (HorarioClase horario : horariosClase) {
			if (horario.getMateriaInscrita() == null
					|| horario.getMateriaInscrita().getCicloAcademico() == null) {
				continue;
			}
			CicloAcademico ciclo = horario.getMateriaInscrita().getCicloAcademico();
			DiaSemana dia = horario.getDiaSemana();
			if (dia == null) {
				advertencias.add("DIA_CLASE_INVALIDO");
				continue;
			}
			DayOfWeek diaHorario = DayOfWeek.of(dia.ordinal() + 1);
			LocalDate fechaHorario = diaHorario == fecha.getDayOfWeek() ? fecha
					: diaHorario == fechaAnterior.getDayOfWeek() ? fechaAnterior
							: null;
			if (fechaHorario != null && cicloActivoEn(ciclo, fechaHorario)) {
				restricciones.add(new FranjaSemanal(diaHorario, horario.getHoraInicio(), horario.getHoraFin()));
			}
		}

		for (ActividadPendiente actividad : actividades) {
			LocalDateTime inicio = inicioEvaluacionFija(actividad);
			if (inicio != null && inicio.toLocalDate().equals(fecha)) {
				LocalDateTime fin = actividad.fechaEntrega();
				restricciones.add(new FranjaSemanal(
						fecha.getDayOfWeek(),
						inicio.toLocalTime(),
						fin.toLocalTime()));
			}
		}

		return RestriccionesDelDia.calcularVentanas(
				fecha.getDayOfWeek(),
				franjasEstudioPlantilla,
				plantillaActiva,
				ventanas,
				hayDisponibilidadRegistrada,
				restricciones);
	}

	private boolean cicloActivoEn(CicloAcademico ciclo, LocalDate fecha) {
		return ciclo != null
				&& !fecha.isBefore(ciclo.getFechaInicio())
				&& !fecha.isAfter(ciclo.getFechaFin());
	}

	private ActividadPendiente aActividadPendiente(ActividadAcademica actividad) {
		Integer tipoActividadId = actividad.getTipoActividad() == null
				? null
				: actividad.getTipoActividad().getIdTipoActividad();
		return new ActividadPendiente(
				actividad.getMateriaInscrita().getId(),
				actividad.getTiempoEstimadoHoras(),
				actividad.getFechaInicio(),
				actividad.getFechaEntrega(),
				tipoActividadId,
				actividad.getPorcentajeEvaluacion());
	}

	private LocalDateTime inicioEvaluacionFija(ActividadPendiente actividad) {
		if ((Integer.valueOf(1).equals(actividad.tipoActividadId())
				|| Integer.valueOf(2).equals(actividad.tipoActividadId()))
				&& actividad.fechaInicio() != null
				&& actividad.fechaEntrega() != null
				&& actividad.fechaInicio().toLocalDate().equals(actividad.fechaEntrega().toLocalDate())
				&& actividad.fechaInicio().isBefore(actividad.fechaEntrega())) {
			return actividad.fechaInicio();
		}
		return null;
	}

	private boolean estaPendiente(ActividadAcademica actividad) {
		return (actividad.getEstado() == null || !"COMPLETADA".equalsIgnoreCase(actividad.getEstado()))
				&& actividad.getFechaCompletada() == null;
	}

	private List<FranjaSemanal> franjasJornada(
			List<JornadaLaboral> jornadas,
			List<String> advertencias) {
		List<FranjaSemanal> franjas = new ArrayList<>();
		for (JornadaLaboral jornada : jornadas) {
			DayOfWeek dia = DiaSemanaTexto.aDia(jornada.getDiaSemana()).orElse(null);
			if (dia == null) {
				advertencias.add("DIA_JORNADA_INVALIDO");
				continue;
			}
			franjas.add(new FranjaSemanal(dia, jornada.getHoraInicio(), jornada.getHoraFin()));
		}
		return List.copyOf(franjas);
	}

	private PlantillaPlanificacion buscarPlantilla(PerfilPlantillaPlanificacion perfil) {
		return plantillaPlanificacionRepository.findByPerfilAndActivaTrue(perfil).stream()
				.findFirst()
				.orElse(null);
	}

	private List<FranjaSemanal> franjasEstudioPlantilla(
			PlantillaPlanificacion plantilla,
			List<String> advertencias) {
		if (plantilla == null) {
			return List.of();
		}
		List<FranjaSemanal> franjas = new ArrayList<>();
		for (FranjaPlantillaPlanificacion franja : plantilla.getFranjas()) {
			if (franja.getTipoBloque() != TipoBloquePlantilla.ESTUDIO) {
				continue;
			}
			if (franja.getDiaSemana() == null) {
				advertencias.add("DIA_PLANTILLA_INVALIDO");
				continue;
			}
			franjas.add(new FranjaSemanal(
					DayOfWeek.of(franja.getDiaSemana().ordinal() + 1),
					franja.getHoraInicio(),
					franja.getHoraFin()));
		}
		return List.copyOf(franjas);
	}

	private HorarioSugeridoResponse sinCicloVigente(
			LocalDateTime ahora,
			LocalDate desde,
			LocalDate hasta) {
		return new HorarioSugeridoResponse(
				SugerenciaHorarioParametros.VERSION,
				ahora,
				SugerenciaHorarioParametros.ZONA_HORARIA,
				new PeriodoHorarioSugeridoResponse(desde, hasta),
				SugerenciaHorarioParametros.GRANULARIDAD_MINUTOS,
				new PlantillaHorarioSugeridaResponse(null, true),
				"No hay un ciclo académico activo durante la semana seleccionada.",
				List.of(),
				List.of(),
				new ResumenHorarioSugeridoResponse(0, 0, 0),
				List.of());
	}
}
