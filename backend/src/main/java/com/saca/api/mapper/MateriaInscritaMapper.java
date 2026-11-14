package com.saca.api.mapper;

import org.springframework.stereotype.Component;

import com.saca.api.dto.request.ActualizarMateriaInscritaRequest;
import com.saca.api.dto.request.CrearMateriaInscritaRequest;
import com.saca.api.dto.response.MateriaInscritaResponse;
import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.MateriaCatalogo;

@Component
public class MateriaInscritaMapper {

	public MateriaInscrita toEntity(CrearMateriaInscritaRequest request, CicloAcademico cicloAcademico,
			MateriaCatalogo catalogoMateria) {
		MateriaInscrita materiaInscrita = new MateriaInscrita();
		materiaInscrita.setCicloAcademico(cicloAcademico);
		materiaInscrita.setCatalogoMateria(catalogoMateria);
		materiaInscrita.setNombre(catalogoMateria.getNombre());
		materiaInscrita.setCodigo(catalogoMateria.getCodigo());
		materiaInscrita.setGrupoTeorico(request.grupoTeorico());
		return materiaInscrita;
	}

	public void updateEntity(MateriaInscrita materiaInscrita, ActualizarMateriaInscritaRequest request,
			MateriaCatalogo catalogoMateria) {
		materiaInscrita.setCatalogoMateria(catalogoMateria);
		materiaInscrita.setNombre(catalogoMateria.getNombre());
		materiaInscrita.setCodigo(catalogoMateria.getCodigo());
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
				materiaInscrita.getGrupoTeorico(),
				materiaInscrita.getCatalogoMateria() == null ? null : materiaInscrita.getCatalogoMateria().getId());
	}
}
