package com.saca.api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.ActualizarMateriaInscritaRequest;
import com.saca.api.dto.request.CrearMateriaInscritaRequest;
import com.saca.api.dto.response.MateriaInscritaResponse;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.MateriaInscritaMapper;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.MateriaInscritaRepository;

@Service
public class MateriaInscritaService {

	private final MateriaInscritaRepository materiaInscritaRepository;
	private final CicloAcademicoRepository cicloAcademicoRepository;
	private final EstudianteRepository estudianteRepository;
	private final MateriaInscritaMapper materiaInscritaMapper;

	public MateriaInscritaService(
			MateriaInscritaRepository materiaInscritaRepository,
			CicloAcademicoRepository cicloAcademicoRepository,
			EstudianteRepository estudianteRepository,
			MateriaInscritaMapper materiaInscritaMapper) {
		this.materiaInscritaRepository = materiaInscritaRepository;
		this.cicloAcademicoRepository = cicloAcademicoRepository;
		this.estudianteRepository = estudianteRepository;
		this.materiaInscritaMapper = materiaInscritaMapper;
	}

	@Transactional(readOnly = true)
	public List<MateriaInscritaResponse> listarPorEstudiante(Long estudianteId) {
		validarEstudianteExiste(estudianteId);

		return materiaInscritaRepository.findByCicloAcademicoEstudianteIdOrderByNombreAsc(estudianteId)
				.stream()
				.map(materiaInscritaMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<MateriaInscritaResponse> listarPorCiclo(Long cicloAcademicoId, Long estudianteId) {
		validarCicloPerteneceAEstudiante(cicloAcademicoId, estudianteId);

		return materiaInscritaRepository.findByCicloAcademicoIdOrderByNombreAsc(cicloAcademicoId)
				.stream()
				.map(materiaInscritaMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public MateriaInscritaResponse obtenerPorId(Long materiaId, Long estudianteId) {
		MateriaInscrita materiaInscrita = buscarMateriaDelEstudiante(materiaId, estudianteId);
		return materiaInscritaMapper.toResponse(materiaInscrita);
	}

	@Transactional
	public MateriaInscritaResponse crear(CrearMateriaInscritaRequest request, Long estudianteId) {
		CicloAcademico cicloAcademico = buscarCicloDelEstudiante(request.cicloAcademicoId(), estudianteId);
		validarCodigoDisponibleAlCrear(request.cicloAcademicoId(), request.codigo());

		MateriaInscrita materiaInscrita = materiaInscritaMapper.toEntity(request, cicloAcademico);
		MateriaInscrita materiaGuardada = materiaInscritaRepository.save(materiaInscrita);
		return materiaInscritaMapper.toResponse(materiaGuardada);
	}

	@Transactional
	public MateriaInscritaResponse actualizar(
			Long materiaId,
			Long estudianteId,
			ActualizarMateriaInscritaRequest request) {
		MateriaInscrita materiaInscrita = buscarMateriaDelEstudiante(materiaId, estudianteId);
		Long cicloAcademicoId = materiaInscrita.getCicloAcademico().getId();

		validarCodigoDisponibleAlActualizar(cicloAcademicoId, request.codigo(), materiaId);
		materiaInscritaMapper.updateEntity(materiaInscrita, request);

		return materiaInscritaMapper.toResponse(materiaInscrita);
	}

	@Transactional
	public void eliminar(Long materiaId, Long estudianteId) {
		MateriaInscrita materiaInscrita = buscarMateriaDelEstudiante(materiaId, estudianteId);
		materiaInscritaRepository.delete(materiaInscrita);
	}

	private MateriaInscrita buscarMateriaDelEstudiante(Long materiaId, Long estudianteId) {
		return materiaInscritaRepository.findByIdAndCicloAcademicoEstudianteId(materiaId, estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la materia inscrita solicitada"));
	}

	private CicloAcademico buscarCicloDelEstudiante(Long cicloAcademicoId, Long estudianteId) {
		return cicloAcademicoRepository.findByIdAndEstudianteId(cicloAcademicoId, estudianteId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el ciclo academico solicitado"));
	}

	private void validarCicloPerteneceAEstudiante(Long cicloAcademicoId, Long estudianteId) {
		if (!cicloAcademicoRepository.existsByIdAndEstudianteId(cicloAcademicoId, estudianteId)) {
			throw new RecursoNoEncontradoException("No se encontro el ciclo academico solicitado");
		}
	}

	private void validarEstudianteExiste(Long estudianteId) {
		if (!estudianteRepository.existsById(estudianteId)) {
			throw new RecursoNoEncontradoException("No se encontro el estudiante solicitado");
		}
	}

	private void validarCodigoDisponibleAlCrear(Long cicloAcademicoId, String codigo) {
		if (materiaInscritaRepository.existsByCicloAcademicoIdAndCodigoIgnoreCase(cicloAcademicoId, codigo)) {
			throw new ValidacionNegocioException("Ya existe una materia con ese codigo en el ciclo academico");
		}
	}

	private void validarCodigoDisponibleAlActualizar(Long cicloAcademicoId, String codigo, Long materiaId) {
		boolean codigoDuplicado = materiaInscritaRepository.existsByCicloAcademicoIdAndCodigoIgnoreCaseAndIdNot(
				cicloAcademicoId,
				codigo,
				materiaId);

		if (codigoDuplicado) {
			throw new ValidacionNegocioException("Ya existe una materia con ese codigo en el ciclo academico");
		}
	}
}
