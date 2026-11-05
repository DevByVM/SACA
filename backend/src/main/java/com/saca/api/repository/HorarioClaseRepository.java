package com.saca.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.HorarioClase;

public interface HorarioClaseRepository extends JpaRepository<HorarioClase, Long> {

	List<HorarioClase> findByMateriaInscritaIdOrderByDiaSemanaAscHoraInicioAsc(Long materiaInscritaId);

	List<HorarioClase> findByMateriaInscrita_CicloAcademico_Estudiante_Id(Long estudianteId);

	
	List<HorarioClase> findByMateriaInscritaCicloAcademicoIdOrderByDiaSemanaAscHoraInicioAsc(Long cicloAcademicoId);

	List<HorarioClase> findByMateriaInscritaCicloAcademicoEstudianteIdOrderByDiaSemanaAscHoraInicioAsc(
			Long estudianteId);

	// Calendario: carga en una sola consulta la materia, el ciclo y la actividad vinculada
	@Query("""
			select horario
			from HorarioClase horario
			join fetch horario.materiaInscrita materia
			join fetch materia.cicloAcademico ciclo
			left join fetch horario.actividadAcademica
			where ciclo.estudiante.id = :estudianteId
			""")
	List<HorarioClase> findParaCalendario(@Param("estudianteId") Long estudianteId);

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

	long countByActividadAcademica(ActividadAcademica actividadAcademica);

	// Sin clearAutomatically: quien llama sigue usando la actividad ya cargada (y su nota perezosa)
	@Modifying(flushAutomatically = true)
	@Query("""
			update HorarioClase horario
			set horario.actividadAcademica = null
			where horario.actividadAcademica = :actividad
			""")
	int desvincularActividad(@Param("actividad") ActividadAcademica actividad);
}
