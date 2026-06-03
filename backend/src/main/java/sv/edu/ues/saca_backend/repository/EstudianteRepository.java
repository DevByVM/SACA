package sv.edu.ues.saca_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sv.edu.ues.saca_backend.entity.Estudiante;
import java.util.Optional;
public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    boolean existsByCarnet(String carnet);

    boolean existsByCorreoInstitucional(String correoInstitucional);

    Optional<Estudiante> findByCorreoInstitucional(
            String correoInstitucional);
}