package com.saca.api.repository;

import com.saca.api.entity.DisponibilidadSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DisponibilidadSemanalRepository extends JpaRepository<DisponibilidadSemanal, Long> {

    // Método para poder buscar el horario de un estudiante en específico
    List<DisponibilidadSemanal> findByEstudianteIdAndCicloId(Long estudianteId, Long cicloId);

    List<DisponibilidadSemanal> findByEstudianteId(Long estudianteId);

    // El bloque solo se encuentra si pertenece al estudiante indicado
    Optional<DisponibilidadSemanal> findByIdAndEstudianteId(Long id, Long estudianteId);
}
