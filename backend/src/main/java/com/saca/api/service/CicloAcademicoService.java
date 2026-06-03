package com.saca.api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.ActualizarCicloAcademicoRequest;
import com.saca.api.dto.request.CrearCicloAcademicoRequest;
import com.saca.api.dto.response.CicloAcademicoResponse;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.entity.Estudiante;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.CicloAcademicoMapper;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;

@Service
public class CicloAcademicoService {

	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final EstudianteRepository estudianteRepository;
	private final CicloAcademicoMapper cicloAcademicoMapper;

	public CicloAcademicoService(
			CicloAcademicoRepository cicloAcademicoRepository,
			EstudianteRepository estudianteRepository,
			CicloAcademicoMapper cicloAcademicoMapper) {
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.estudianteRepository = estudianteRepository;
		this.cicloAcademicoMapper = cicloAcademicoMapper;
	}

	@Transactional(readOnly = true)
	public List<CicloAcademicoResponse> listarPorEstudiante(Long estudianteId) {
		validarEstudianteExiste(estudianteId);

		return cicloAcademicoRepository.findByEstudianteIdOrderByAnioDescFechaInicioDesc(estudianteId)
				.stream()
				.map(cicloAcademicoMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<CicloAcademicoResponse> listarPorEstudianteYEstado(Long estudianteId, EstadoCiclo estado) {
		validarEstudianteExiste(estudianteId);

		return cicloAcademicoRepository.findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(estudianteId, estado)
				.stream()
				.map(cicloAcademicoMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public CicloAcademicoResponse obtenerPorId(Long cicloId, Long estudianteId) {
		CicloAcademico cicloAcademico = buscarCicloDelEstudiante(cicloId, estudianteId);
		return cicloAcademicoMapper.toResponse(cicloAcademico);
	}

	@Transactional
	public CicloAcademicoResponse crear(CrearCicloAcademicoRequest request) {
		validarRangoFechas(request.fechaInicio(), request.fechaFin());
		Estudiante estudiante = buscarEstudiante(request.estudianteId());

		CicloAcademico cicloAcademico = cicloAcademicoMapper.toEntity(request, estudiante);
		CicloAcademico cicloGuardado = cicloAcademicoRepository.save(cicloAcademico);
		return cicloAcademicoMapper.toResponse(cicloGuardado);
	}

	@Transactional
	public CicloAcademicoResponse actualizar(
			Long cicloId,
			Long estudianteId,
			ActualizarCicloAcademicoRequest request) {
		validarRangoFechas(request.fechaInicio(), request.fechaFin());

		CicloAcademico cicloAcademico = buscarCicloDelEstudiante(cicloId, estudianteId);
		cicloAcademicoMapper.updateEntity(cicloAcademico, request);

		return cicloAcademicoMapper.toResponse(cicloAcademico);
	}

	@Transactional
	public void eliminar(Long cicloId, Long estudianteId) {
		CicloAcademico cicloAcademico = buscarCicloDelEstudiante(cicloId, estudianteId);
		cicloAcademicoRepository.delete(cicloAcademico);
	}

	private CicloAcademico buscarCicloDelEstudiante(Long cicloId, Long estudianteId) {
		return cicloAcademicoRepository.findByIdAndEstudianteId(cicloId, estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el ciclo academico solicitado"));
	}

	private Estudiante buscarEstudiante(Long estudianteId) {
		return estudianteRepository.findById(estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el estudiante solicitado"));
	}

	private void validarEstudianteExiste(Long estudianteId) {
		if (!estudianteRepository.existsById(estudianteId)) {
			throw new RecursoNoEncontradoException("No se encontro el estudiante solicitado");
		}
	}

	private void validarRangoFechas(java.time.LocalDate fechaInicio, java.time.LocalDate fechaFin) {
		if (fechaInicio.isAfter(fechaFin)) {
			throw new ValidacionNegocioException("La fecha de inicio no puede ser posterior a la fecha de fin");
		}
	}
}
