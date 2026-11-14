package com.saca.api.repository;

import com.saca.api.entity.TipoActividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoActividadRepository extends JpaRepository<TipoActividad, Integer> {

    List<TipoActividad> findByActivoTrueOrderByNombreAsc();

    List<TipoActividad> findAllByOrderByNombreAsc();

    Optional<TipoActividad> findByIdTipoActividadAndActivoTrue(Integer idTipoActividad);

    Optional<TipoActividad> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdTipoActividadNot(String nombre, Integer id);
}
