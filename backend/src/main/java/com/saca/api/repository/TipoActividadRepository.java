package com.saca.api.repository;

import com.saca.api.entity.TipoActividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoActividadRepository extends JpaRepository<TipoActividad, Integer> {

}
