package com.saca.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.saca.api.entity.Estudiante;
import java.util.Optional;
public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    boolean existsByCarnet(String carnet);

    boolean existsByCorreoInstitucional(String correoInstitucional);

    Optional<Estudiante> findByCorreoInstitucional(
            String correoInstitucional);
}