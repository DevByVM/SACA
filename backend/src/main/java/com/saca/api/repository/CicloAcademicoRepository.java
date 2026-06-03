package com.saca.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.CicloAcademico;
import com.saca.api.entity.EstadoCiclo;

public interface CicloAcademicoRepository extends JpaRepository<CicloAcademico, Long> {

	List<CicloAcademico> findByEstudianteIdOrderByAnioDescFechaInicioDesc(Long estudianteId);

	List<CicloAcademico> findByEstudianteIdAndEstadoOrderByAnioDescFechaInicioDesc(
			Long estudianteId,
			EstadoCiclo estado);

	Optional<CicloAcademico> findByIdAndEstudianteId(Long id, Long estudianteId);

	boolean existsByIdAndEstudianteId(Long id, Long estudianteId);
}
