package com.saca.api.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.saca.api.entity.Administrador;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {

    Optional<Administrador> findByCorreoIgnoreCase(String correo);

    @Query("select a.id from Administrador a where a.habilitado = false")
    List<Long> findIdsInhabilitados();
}
