package com.saca.api.service;

import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.ActualizarHorarioClaseRequest;
import com.saca.api.dto.request.CrearHorarioClaseRequest;
import com.saca.api.dto.response.HorarioClaseResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.HorarioClaseMapper;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.HorarioClaseRepository;
import com.saca.api.repository.MateriaInscritaRepository;

@Service
public class HorarioClaseService {

	private final HorarioClaseRepository horarioClaseRepository;
	private final MateriaInscritaRepository materiaInscritaRepository;
	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final ConflictoHorarioService conflictoHorarioService;
	private final HorarioClaseMapper horarioClaseMapper;
	private final ActividadAcademicaRepository actividadAcademicaRepository;

	public HorarioClaseService(
			HorarioClaseRepository horarioClaseRepository,
			MateriaInscritaRepository materiaInscritaRepository,
			CicloAcademicoRepository cicloAcademicoRepository,
			ConflictoHorarioService conflictoHorarioService,
			HorarioClaseMapper horarioClaseMapper,
			ActividadAcademicaRepository actividadAcademicaRepository) {
		this.horarioClaseRepository = horarioClaseRepository;
		this.materiaInscritaRepository = materiaInscritaRepository;
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.conflictoHorarioService = conflictoHorarioService;
		this.horarioClaseMapper = horarioClaseMapper;
		this.actividadAcademicaRepository = actividadAcademicaRepository;
	}

	@Transactional(readOnly = true)
	public List<HorarioClaseResponse> listarPorMateria(Long materiaInscritaId, Long estudianteId) {
		validarMateriaPerteneceAEstudiante(materiaInscritaId, estudianteId);

		return horarioClaseRepository.findByMateriaInscritaIdOrderByDiaSemanaAscHoraInicioAsc(materiaInscritaId)
				.stream()
				.map(horarioClaseMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<HorarioClaseResponse> listarPorCiclo(Long cicloAcademicoId, Long estudianteId) {
		validarCicloPerteneceAEstudiante(cicloAcademicoId, estudianteId);

		return horarioClaseRepository.findByMateriaInscritaCicloAcademicoIdOrderByDiaSemanaAscHoraInicioAsc(
						cicloAcademicoId)
				.stream()
				.map(horarioClaseMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<HorarioClaseResponse> listarPorEstudiante(Long estudianteId) {
		return horarioClaseRepository
				.findByMateriaInscritaCicloAcademicoEstudianteIdOrderByDiaSemanaAscHoraInicioAsc(estudianteId)
				.stream()
				.map(horarioClaseMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public HorarioClaseResponse obtenerPorId(Long horarioId, Long estudianteId) {
		HorarioClase horarioClase = buscarHorarioDelEstudiante(horarioId, estudianteId);
		return horarioClaseMapper.toResponse(horarioClase);
	}

	@Transactional
	public HorarioClaseResponse crear(CrearHorarioClaseRequest request, Long estudianteId) {
		validarRangoHoras(request.horaInicio(), request.horaFin());
		MateriaInscrita materiaInscrita = buscarMateriaDelEstudiante(request.materiaInscritaId(), estudianteId);

		conflictoHorarioService.validarSinConflicto(
				materiaInscrita,
				request.diaSemana(),
				request.horaInicio(),
				request.horaFin(),
				null);

		ActividadAcademica actividadAcademica =
				buscarActividadDeLaMateria(request.actividadAcademicaId(), materiaInscrita, estudianteId);
		HorarioClase horarioClase = horarioClaseMapper.toEntity(request, materiaInscrita, actividadAcademica);
		HorarioClase horarioGuardado = horarioClaseRepository.save(horarioClase);
		return horarioClaseMapper.toResponse(horarioGuardado);
	}

	@Transactional
	public HorarioClaseResponse actualizar(
			Long horarioId,
			Long estudianteId,
			ActualizarHorarioClaseRequest request) {
		validarRangoHoras(request.horaInicio(), request.horaFin());
		HorarioClase horarioClase = buscarHorarioDelEstudiante(horarioId, estudianteId);
		MateriaInscrita materiaInscrita = horarioClase.getMateriaInscrita();

		conflictoHorarioService.validarSinConflicto(
				materiaInscrita,
				request.diaSemana(),
				request.horaInicio(),
				request.horaFin(),
				horarioId);

		ActividadAcademica actividadAcademica =
				buscarActividadDeLaMateria(request.actividadAcademicaId(), materiaInscrita, estudianteId);
		horarioClaseMapper.updateEntity(horarioClase, request, actividadAcademica);
		return horarioClaseMapper.toResponse(horarioClase);
	}

	@Transactional
	public void eliminar(Long horarioId, Long estudianteId) {
		HorarioClase horarioClase = buscarHorarioDelEstudiante(horarioId, estudianteId);
		horarioClaseRepository.delete(horarioClase);
	}

	private HorarioClase buscarHorarioDelEstudiante(Long horarioId, Long estudianteId) {
		HorarioClase horarioClase = horarioClaseRepository.findById(horarioId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el horario de clase solicitado"));

		Long propietarioId = horarioClase.getMateriaInscrita().getCicloAcademico().getEstudiante().getId();
		if (!propietarioId.equals(estudianteId)) {
			throw new RecursoNoEncontradoException("No se encontro el horario de clase solicitado");
		}

		return horarioClase;
	}

	private MateriaInscrita buscarMateriaDelEstudiante(Long materiaInscritaId, Long estudianteId) {
		return materiaInscritaRepository.findByIdAndCicloAcademicoEstudianteId(materiaInscritaId, estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la materia inscrita solicitada"));
	}

	// Una actividad ajena o inexistente responde 404, como el resto de recursos de otro estudiante
	private ActividadAcademica buscarActividadDeLaMateria(
			Long actividadAcademicaId,
			MateriaInscrita materiaInscrita,
			Long estudianteId) {
		if (actividadAcademicaId == null) {
			return null;
		}
		ActividadAcademica actividadAcademica = actividadAcademicaRepository
				.findByIdActividadAndMateriaInscritaCicloAcademicoEstudianteId(actividadAcademicaId, estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la actividad académica solicitada"));
		if (!actividadAcademica.getMateriaInscrita().getId().equals(materiaInscrita.getId())) {
			throw new ValidacionNegocioException("La actividad debe pertenecer a la misma materia del horario");
		}
		return actividadAcademica;
	}

	private void validarMateriaPerteneceAEstudiante(Long materiaInscritaId, Long estudianteId) {
		buscarMateriaDelEstudiante(materiaInscritaId, estudianteId);
	}

	private void validarCicloPerteneceAEstudiante(Long cicloAcademicoId, Long estudianteId) {
		if (!cicloAcademicoRepository.existsByIdAndEstudianteId(cicloAcademicoId, estudianteId)) {
			throw new RecursoNoEncontradoException("No se encontro el ciclo academico solicitado");
		}
	}

	private void validarRangoHoras(LocalTime horaInicio, LocalTime horaFin) {
		if (!horaInicio.isBefore(horaFin)) {
			throw new ValidacionNegocioException("La hora de inicio debe ser menor que la hora de fin");
		}
	}
}
