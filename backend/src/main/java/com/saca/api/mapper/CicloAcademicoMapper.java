package com.saca.api.mapper;

import org.springframework.stereotype.Component;

import com.saca.api.dto.request.ActualizarCicloAcademicoRequest;
import com.saca.api.dto.request.CrearCicloAcademicoRequest;
import com.saca.api.dto.response.CicloAcademicoResponse;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.Estudiante;

@Component
public class CicloAcademicoMapper {

	public CicloAcademico toEntity(CrearCicloAcademicoRequest request, Estudiante estudiante) {
		CicloAcademico cicloAcademico = new CicloAcademico();
		cicloAcademico.setEstudiante(estudiante);
		cicloAcademico.setNombre(request.nombre());
		cicloAcademico.setAnio(request.anio());
		cicloAcademico.setFechaInicio(request.fechaInicio());
		cicloAcademico.setFechaFin(request.fechaFin());
		cicloAcademico.setEstado(request.estado());
		return cicloAcademico;
	}

	public void updateEntity(CicloAcademico cicloAcademico, ActualizarCicloAcademicoRequest request) {
		cicloAcademico.setNombre(request.nombre());
		cicloAcademico.setAnio(request.anio());
		cicloAcademico.setFechaInicio(request.fechaInicio());
		cicloAcademico.setFechaFin(request.fechaFin());
		cicloAcademico.setEstado(request.estado());
	}

	public CicloAcademicoResponse toResponse(CicloAcademico cicloAcademico) {
		return new CicloAcademicoResponse(
				cicloAcademico.getId(),
				cicloAcademico.getEstudiante().getId(),
				cicloAcademico.getNombre(),
				cicloAcademico.getAnio(),
				cicloAcademico.getFechaInicio(),
				cicloAcademico.getFechaFin(),
				cicloAcademico.getEstado());
	}
}
