package com.saca.api.repository;

import com.saca.api.entity.DisponibilidadSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DisponibilidadSemanalRepository extends JpaRepository<DisponibilidadSemanal, Long> {

    // Método para poder buscar el horario de un estudiante en específico
    List<DisponibilidadSemanal> findByEstudianteIdAndCicloId(Long estudianteId, Long cicloId);
}
