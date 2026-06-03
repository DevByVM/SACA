package com.saca.api.mapper;

import org.springframework.stereotype.Component;

import com.saca.api.dto.request.ActualizarMateriaInscritaRequest;
import com.saca.api.dto.request.CrearMateriaInscritaRequest;
import com.saca.api.dto.response.MateriaInscritaResponse;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.MateriaInscrita;

@Component
public class MateriaInscritaMapper {

	public MateriaInscrita toEntity(CrearMateriaInscritaRequest request, CicloAcademico cicloAcademico) {
		MateriaInscrita materiaInscrita = new MateriaInscrita();
		materiaInscrita.setCicloAcademico(cicloAcademico);
		materiaInscrita.setNombre(request.nombre());
		materiaInscrita.setCodigo(request.codigo());
		materiaInscrita.setGrupoTeorico(request.grupoTeorico());
		return materiaInscrita;
	}

	public void updateEntity(MateriaInscrita materiaInscrita, ActualizarMateriaInscritaRequest request) {
		materiaInscrita.setNombre(request.nombre());
		materiaInscrita.setCodigo(request.codigo());
		materiaInscrita.setGrupoTeorico(request.grupoTeorico());
	}

	public MateriaInscritaResponse toResponse(MateriaInscrita materiaInscrita) {
		CicloAcademico cicloAcademico = materiaInscrita.getCicloAcademico();

		return new MateriaInscritaResponse(
				materiaInscrita.getId(),
				cicloAcademico.getId(),
				cicloAcademico.getNombre(),
				materiaInscrita.getNombre(),
				materiaInscrita.getCodigo(),
				materiaInscrita.getGrupoTeorico());
	}
}
