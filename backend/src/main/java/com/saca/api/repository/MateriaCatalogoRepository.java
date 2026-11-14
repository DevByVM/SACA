package com.saca.api.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.MateriaCatalogo;

public interface MateriaCatalogoRepository extends JpaRepository<MateriaCatalogo, Long> {

    List<MateriaCatalogo> findByActivaTrueOrderByNombreAsc();

    List<MateriaCatalogo> findAllByOrderByNombreAsc();

    Optional<MateriaCatalogo> findByIdAndActivaTrue(Long id);

    Optional<MateriaCatalogo> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
}
