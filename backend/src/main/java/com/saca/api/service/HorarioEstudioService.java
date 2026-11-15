package com.saca.api.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.BloqueHorarioEstudioRequest;
import com.saca.api.dto.request.ConfirmarHorarioEstudioRequest;
import com.saca.api.dto.response.BloqueHorarioEstudioResponse;
import com.saca.api.dto.response.HorarioEstudioResponse;
import com.saca.api.entity.BloqueHorarioEstudio;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.HorarioEstudio;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.HorarioEstudioRepository;
import com.saca.api.service.IntervalosMinutos.Intervalo;
import com.saca.api.service.RestriccionesDelDia.VentanasDisponibles;
import com.saca.api.service.SugerenciaHorarioConsultaService.DatosSemana;
import com.saca.api.service.SugerenciaHorarioService.MateriaActiva;

// Guarda el horario de estudio que el estudiante confirma a partir de la sugerencia, ya editado.
// Los bloques se validan contra las mismas ventanas libres con las que se genera la sugerencia
// (clases, jornada laboral, disponibilidad y evaluaciones con hora fija), sin repetir ese cálculo.
@Service
public class HorarioEstudioService {

	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM");
	private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final Clock relojAplicacion;
	private final SugerenciaHorarioConsultaService sugerenciaHorarioConsultaService;
	private final HorarioEstudioRepository horarioEstudioRepository;
	private final EstudianteRepository estudianteRepository;

	public HorarioEstudioService(
			Clock relojAplicacion,
			SugerenciaHorarioConsultaService sugerenciaHorarioConsultaService,
			HorarioEstudioRepository horarioEstudioRepository,
			EstudianteRepository estudianteRepository) {
		this.relojAplicacion = relojAplicacion;
		this.sugerenciaHorarioConsultaService = sugerenciaHorarioConsultaService;
		this.horarioEstudioRepository = horarioEstudioRepository;
		this.estudianteRepository = estudianteRepository;
	}

	@Transactional(readOnly = true)
	public Optional<HorarioEstudioResponse> obtener(Long estudianteId, LocalDate desde) {
		validarLunes(desde);
		return horarioEstudioRepository.findByEstudianteIdAndSemanaInicio(estudianteId, desde)
				.map(this::aRespuesta);
	}

	// Crea el horario de la semana o reemplaza el ya confirmado; todo en una transacción, así que
	// un bloque inválido o un fallo al guardar no deja un horario a medias
	@Transactional
	public HorarioEstudioResponse confirmar(Long estudianteId, ConfirmarHorarioEstudioRequest solicitud) {
		LocalDate desde = solicitud.desde();
		validarLunes(desde);
		LocalDate lunesActual = LocalDate.now(relojAplicacion).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		if (desde.isBefore(lunesActual)) {
			throw new ValidacionNegocioException("No se puede confirmar el horario de una semana que ya terminó");
		}
		LocalDate hasta = desde.plusDays(SugerenciaHorarioParametros.DIAS_SEMANA - 1);

		// Se bloquea la fila del estudiante, que siempre existe, para atender de una en una sus
		// confirmaciones simultáneas. Va antes de cualquier otra lectura: en REPEATABLE READ la instantánea
		// se fija en la primera lectura, y así la confirmación que esperaba ve el horario recién guardado y
		// lo reemplaza. Bloquear el horario con SELECT ... FOR UPDATE no sirve: si aún no existe, MySQL
		// toma un bloqueo de hueco compartido y dos inserciones concurrentes acaban en interbloqueo.
		Estudiante estudiante = estudianteRepository.bloquearPorId(estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado"));

		DatosSemana datos = sugerenciaHorarioConsultaService.datosSemana(estudianteId, desde)
				.orElseThrow(() -> new ValidacionNegocioException(
						"No hay un ciclo académico activo durante la semana seleccionada."));
		Map<Long, String> materias = new HashMap<>();
		for (MateriaActiva materia : datos.materiasActivas()) {
			materias.put(materia.materiaInscritaId(), materia.materia());
		}
		List<BloqueHorarioEstudio> bloques = validarBloques(
				solicitud.bloques(), desde, hasta, materias, datos.ventanasPorFecha());

		HorarioEstudio horario = horarioEstudioRepository
				.findByEstudianteIdAndSemanaInicio(estudianteId, desde)
				.orElseGet(() -> new HorarioEstudio(estudiante, desde, hasta));
		horario.confirmar(bloques, LocalDateTime.now(relojAplicacion).truncatedTo(ChronoUnit.SECONDS));
		try {
			horario = horarioEstudioRepository.saveAndFlush(horario);
		} catch (DataIntegrityViolationException e) {
			// Red de seguridad: con el bloqueo anterior no debería ocurrir, pero la restricción única manda
			throw new ValidacionNegocioException(
					"El horario de esta semana se confirmó al mismo tiempo desde otra sesión. Vuelve a intentarlo.");
		}
		return aRespuesta(horario);
	}

	private List<BloqueHorarioEstudio> validarBloques(
			List<BloqueHorarioEstudioRequest> solicitados,
			LocalDate desde,
			LocalDate hasta,
			Map<Long, String> materias,
			Map<LocalDate, VentanasDisponibles> ventanasPorFecha) {
		List<BloqueHorarioEstudioRequest> ordenados = solicitados.stream()
				.sorted(Comparator.comparing(BloqueHorarioEstudioRequest::fecha)
						.thenComparing(BloqueHorarioEstudioRequest::inicio))
				.toList();

		List<BloqueHorarioEstudio> bloques = new ArrayList<>();
		BloqueHorarioEstudioRequest anterior = null;
		for (BloqueHorarioEstudioRequest bloque : ordenados) {
			String descripcion = describir(bloque);
			if (bloque.inicio().getSecond() != 0 || bloque.inicio().getNano() != 0
					|| bloque.fin().getSecond() != 0 || bloque.fin().getNano() != 0) {
				throw new ValidacionNegocioException(descripcion + " debe indicar las horas solo con horas y minutos");
			}
			if (bloque.fecha().isBefore(desde) || bloque.fecha().isAfter(hasta)) {
				throw new ValidacionNegocioException(descripcion + " está fuera de la semana del horario");
			}
			if (!bloque.inicio().isBefore(bloque.fin())) {
				throw new ValidacionNegocioException(descripcion + " debe terminar después de empezar");
			}
			int inicio = minuto(bloque.inicio());
			int fin = minuto(bloque.fin());
			if (fin - inicio < SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS) {
				throw new ValidacionNegocioException(descripcion + " debe durar al menos "
						+ SugerenciaHorarioParametros.DURACION_MINIMA_BLOQUE_MINUTOS + " minutos");
			}
			String nombreMateria = materias.get(bloque.materiaInscritaId());
			if (nombreMateria == null) {
				throw new ValidacionNegocioException(
						descripcion + " tiene una materia que no pertenece a un ciclo activo del estudiante");
			}
			if (anterior != null && anterior.fecha().equals(bloque.fecha())
					&& anterior.fin().isAfter(bloque.inicio())) {
				throw new ValidacionNegocioException(descripcion + " se superpone con " + describir(anterior).toLowerCase());
			}
			if (!cabeEnVentanas(inicio, fin, ventanasPorFecha.get(bloque.fecha()))) {
				throw new ValidacionNegocioException(descripcion
						+ " choca con una clase, la jornada laboral, una evaluación o un horario no disponible");
			}
			bloques.add(new BloqueHorarioEstudio(
					bloque.fecha(), bloque.inicio(), bloque.fin(), bloque.materiaInscritaId(), nombreMateria));
			anterior = bloque;
		}
		return bloques;
	}

	// El bloque debe quedar dentro de un único tramo libre del día (preferido o alternativo)
	private static boolean cabeEnVentanas(int inicio, int fin, VentanasDisponibles ventanas) {
		if (ventanas == null) {
			return false;
		}
		List<Intervalo> libres = new ArrayList<>(ventanas.preferidas());
		libres.addAll(ventanas.alternativas());
		return IntervalosMinutos.unir(libres).stream()
				.anyMatch(libre -> libre.inicio() <= inicio && fin <= libre.fin());
	}

	private static void validarLunes(LocalDate desde) {
		if (desde == null || desde.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new ValidacionNegocioException("La semana del horario debe empezar en lunes");
		}
	}

	private static String describir(BloqueHorarioEstudioRequest bloque) {
		return "El bloque del " + bloque.fecha().format(FORMATO_FECHA)
				+ " de " + bloque.inicio().format(FORMATO_HORA)
				+ " a " + bloque.fin().format(FORMATO_HORA);
	}

	private static int minuto(LocalTime hora) {
		return hora.getHour() * 60 + hora.getMinute();
	}

	private HorarioEstudioResponse aRespuesta(HorarioEstudio horario) {
		List<BloqueHorarioEstudioResponse> bloques = horario.getBloques().stream()
				.sorted(Comparator.comparing(BloqueHorarioEstudio::getFecha)
						.thenComparing(BloqueHorarioEstudio::getHoraInicio))
				.map(bloque -> new BloqueHorarioEstudioResponse(
						bloque.getId(),
						bloque.getFecha(),
						bloque.getHoraInicio(),
						bloque.getHoraFin(),
						minuto(bloque.getHoraFin()) - minuto(bloque.getHoraInicio()),
						bloque.getMateriaInscritaId(),
						bloque.getNombreMateria()))
				.toList();
		int minutosTotales = bloques.stream().mapToInt(BloqueHorarioEstudioResponse::minutos).sum();
		return new HorarioEstudioResponse(
				horario.getId(),
				horario.getSemanaInicio(),
				horario.getSemanaFin(),
				horario.getConfirmadoEn(),
				minutosTotales,
				bloques);
	}
}
