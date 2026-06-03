package com.saca.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.MateriaInscrita;

public interface MateriaInscritaRepository extends JpaRepository<MateriaInscrita, Long> {

	List<MateriaInscrita> findByCicloAcademicoIdOrderByNombreAsc(Long cicloAcademicoId);

	List<MateriaInscrita> findByCicloAcademicoEstudianteIdOrderByNombreAsc(Long estudianteId);

	Optional<MateriaInscrita> findByIdAndCicloAcademicoEstudianteId(Long id, Long estudianteId);

	boolean existsByCicloAcademicoIdAndCodigoIgnoreCase(Long cicloAcademicoId, String codigo);
}
