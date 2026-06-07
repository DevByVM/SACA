package com.saca.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.HorarioClase;

public interface HorarioClaseRepository extends JpaRepository<HorarioClase, Long> {

	List<HorarioClase> findByMateriaInscritaIdOrderByDiaSemanaAscHoraInicioAsc(Long materiaInscritaId);

	List<HorarioClase> findByMateriaInscrita_CicloAcademico_Estudiante_Id(Long estudianteId);

	
	List<HorarioClase> findByMateriaInscritaCicloAcademicoIdOrderByDiaSemanaAscHoraInicioAsc(Long cicloAcademicoId);

	List<HorarioClase> findByMateriaInscritaCicloAcademicoEstudianteIdOrderByDiaSemanaAscHoraInicioAsc(
			Long estudianteId);

	@Query("""
			select horario
			from HorarioClase horario
			where horario.materiaInscrita.cicloAcademico.estudiante.id = :estudianteId
			  and horario.materiaInscrita.cicloAcademico.id = :cicloAcademicoId
			  and horario.diaSemana = :diaSemana
			  and (:horarioIdExcluir is null or horario.id <> :horarioIdExcluir)
			order by horario.horaInicio asc
			""")
	List<HorarioClase> findHorariosParaValidarConflicto(
			@Param("estudianteId") Long estudianteId,
			@Param("cicloAcademicoId") Long cicloAcademicoId,
			@Param("diaSemana") DiaSemana diaSemana,
			@Param("horarioIdExcluir") Long horarioIdExcluir);
}
