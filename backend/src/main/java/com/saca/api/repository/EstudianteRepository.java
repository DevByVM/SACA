package com.saca.api.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.Estudiante;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

	boolean existsByCarnet(String carnet);

	boolean existsByCorreoInstitucional(String correoInstitucional);

	Optional<Estudiante> findByCarnet(String carnet);

	Optional<Estudiante> findByCorreoInstitucional(String correoInstitucional);
}
