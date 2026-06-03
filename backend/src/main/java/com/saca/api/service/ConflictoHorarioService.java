package com.saca.api.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.exception.ConflictoHorarioException;
import com.saca.api.repository.HorarioClaseRepository;

@Service
public class ConflictoHorarioService {

	private final HorarioClaseRepository horarioClaseRepository;

	public ConflictoHorarioService(HorarioClaseRepository horarioClaseRepository) {
		this.horarioClaseRepository = horarioClaseRepository;
	}

	@Transactional(readOnly = true)
	public void validarSinConflicto(
			MateriaInscrita materiaInscrita,
			DiaSemana diaSemana,
			LocalTime horaInicio,
			LocalTime horaFin,
			Long horarioIdExcluir) {
		Long estudianteId = materiaInscrita.getCicloAcademico().getEstudiante().getId();
		Long cicloAcademicoId = materiaInscrita.getCicloAcademico().getId();

		horarioClaseRepository.findHorariosParaValidarConflicto(
						estudianteId,
						cicloAcademicoId,
						diaSemana,
						horarioIdExcluir)
				.stream()
				.filter(horarioExistente -> hayTraslape(
						horaInicio,
						horaFin,
						horarioExistente.getHoraInicio(),
						horarioExistente.getHoraFin()))
				.findFirst()
				.ifPresent(this::lanzarConflicto);
	}

	private boolean hayTraslape(
			LocalTime nuevoInicio,
			LocalTime nuevoFin,
			LocalTime existenteInicio,
			LocalTime existenteFin) {
		return nuevoInicio.isBefore(existenteFin) && nuevoFin.isAfter(existenteInicio);
	}

	private void lanzarConflicto(HorarioClase horarioExistente) {
		throw new ConflictoHorarioException(
				"Existe conflicto con otro horario registrado el "
						+ horarioExistente.getDiaSemana()
						+ " de "
						+ horarioExistente.getHoraInicio()
						+ " a "
						+ horarioExistente.getHoraFin());
	}
}
