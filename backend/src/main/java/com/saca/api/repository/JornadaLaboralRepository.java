package com.saca.api.repository;
import com.saca.api.entity.JornadaLaboral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface JornadaLaboralRepository extends JpaRepository<JornadaLaboral, Long> {
    List<JornadaLaboral> findByEstudianteId(Long estudianteId);

    // La jornada solo se encuentra si pertenece al estudiante indicado
    Optional<JornadaLaboral> findByIdAndEstudianteId(Long id, Long estudianteId);

}
