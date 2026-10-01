package com.saca.api.repository;

import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.TipoActividadAcademica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface ActividadAcademicaRepository extends JpaRepository<ActividadAcademica, Long> {

    //es necesario especificar un query ya que hay muchas tablas involucradas
    @Query("SELECT aa FROM ActividadAcademica aa " +
            "JOIN aa.materiaInscrita materia " +
            "JOIN materia.cicloAcademico ciclo " +
            "JOIN ciclo.estudiante estudiante " +
            "WHERE estudiante.id = :idEstudiante " +
            "AND ciclo.estado = ACTIVO")
    List<ActividadAcademica> findActividadesCicloActivoPorEstudiante(@Param("idEstudiante") Long idEstudiante);

    @Query("SELECT aa FROM ActividadAcademica aa " +
            "JOIN aa.materiaInscrita materia " +
            "JOIN materia.cicloAcademico ciclo " +
            "JOIN ciclo.estudiante estudiante " +
            "WHERE estudiante.id = :idEstudiante " +
            "AND ciclo.id = :idCiclo ")

    List<ActividadAcademica> findActividadesPorCicloYEstudiante(@Param("idEstudiante") Long idEstudiante,@Param("idCiclo") Long idCiclo);

    @Query("SELECT aa FROM ActividadAcademica aa " +
            "JOIN FETCH aa.materiaInscrita materia " +
            "JOIN materia.cicloAcademico ciclo " +
            "JOIN ciclo.estudiante estudiante " +
            "WHERE estudiante.id = :idEstudiante " +
            "AND ciclo.estado = ACTIVO " +
            "AND aa.fechaEntrega >= :inicio " +
            "AND aa.fechaEntrega < :fin " +
            "AND (aa.estado IS NULL OR aa.estado <> 'COMPLETADA') " +
            "AND aa.tipoActividad IN :tiposActividad " +
            "ORDER BY aa.fechaEntrega ASC, aa.idActividad ASC")
    List<ActividadAcademica> findNotificacionesProximasPorEstudiante(
            @Param("idEstudiante") Long idEstudiante,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("tiposActividad") Set<TipoActividadAcademica> tiposActividad
    );
}
