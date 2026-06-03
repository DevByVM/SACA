package com.saca.api.mapper;

import org.springframework.stereotype.Component;

import com.saca.api.dto.request.ActualizarHorarioClaseRequest;
import com.saca.api.dto.request.CrearHorarioClaseRequest;
import com.saca.api.dto.response.HorarioClaseResponse;
import com.saca.api.entity.HorarioClase;
import com.saca.api.entity.MateriaInscrita;

@Component
public class HorarioClaseMapper {

	public HorarioClase toEntity(CrearHorarioClaseRequest request, MateriaInscrita materiaInscrita) {
		HorarioClase horarioClase = new HorarioClase();
		horarioClase.setMateriaInscrita(materiaInscrita);
		horarioClase.setDiaSemana(request.diaSemana());
		horarioClase.setHoraInicio(request.horaInicio());
		horarioClase.setHoraFin(request.horaFin());
		horarioClase.setModalidad(request.modalidad());
		horarioClase.setDocenteTutor(request.docenteTutor());
		return horarioClase;
	}

	public void updateEntity(HorarioClase horarioClase, ActualizarHorarioClaseRequest request) {
		horarioClase.setDiaSemana(request.diaSemana());
		horarioClase.setHoraInicio(request.horaInicio());
		horarioClase.setHoraFin(request.horaFin());
		horarioClase.setModalidad(request.modalidad());
		horarioClase.setDocenteTutor(request.docenteTutor());
	}

	public HorarioClaseResponse toResponse(HorarioClase horarioClase) {
		MateriaInscrita materiaInscrita = horarioClase.getMateriaInscrita();

		return new HorarioClaseResponse(
				horarioClase.getId(),
				materiaInscrita.getId(),
				materiaInscrita.getNombre(),
				materiaInscrita.getCicloAcademico().getId(),
				horarioClase.getDiaSemana(),
				horarioClase.getHoraInicio(),
				horarioClase.getHoraFin(),
				horarioClase.getModalidad(),
				horarioClase.getDocenteTutor());
	}
}
