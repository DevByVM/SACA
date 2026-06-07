package com.saca.api.repository;

import com.saca.api.entity.ActividadAcademica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActividadAcademicaRepository extends JpaRepository<ActividadAcademica, Long> {

    //es necesario especificar un query ya que hay muchas tablas involucradas
    @Query("SELECT aa FROM ActividadAcademica aa " +
            "JOIN aa.materiaInscrita materia " +
            "JOIN materia.cicloAcademico ciclo " +
            "JOIN ciclo.estudiante estudiante " +
            "WHERE estudiante.id = :idEstudiante " +
            "AND ciclo.estado = ACTIVO")
    List<ActividadAcademica> findActividadesCicloActivoPorEstudiante(@Param("idEstudiante") Long idEstudiante);
}
