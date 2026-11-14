package com.saca.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.PerfilPlantillaPlanificacion;
import com.saca.api.entity.PlantillaPlanificacion;

public interface PlantillaPlanificacionRepository extends JpaRepository<PlantillaPlanificacion, Long> {
    List<PlantillaPlanificacion> findAllByOrderByPerfilAscNombreAsc();

    List<PlantillaPlanificacion> findByPerfilAndActivaTrue(PerfilPlantillaPlanificacion perfil);
}
