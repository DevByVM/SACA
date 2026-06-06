package com.saca.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.PerfilAcademico;

public interface PerfilAcademicoRepository extends JpaRepository<PerfilAcademico, Long> {

    Optional<PerfilAcademico> findByEstudianteId(Long estudianteId);
}