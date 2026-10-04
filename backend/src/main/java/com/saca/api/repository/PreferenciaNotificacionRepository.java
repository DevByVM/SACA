package com.saca.api.repository;

import com.saca.api.entity.PreferenciaNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenciaNotificacionRepository extends JpaRepository<PreferenciaNotificacion, Long> {

    Optional<PreferenciaNotificacion> findByEstudianteId(Long estudianteId);
}
