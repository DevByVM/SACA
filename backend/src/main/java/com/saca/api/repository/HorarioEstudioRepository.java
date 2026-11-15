package com.saca.api.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.saca.api.entity.HorarioEstudio;

public interface HorarioEstudioRepository extends JpaRepository<HorarioEstudio, Long> {

    Optional<HorarioEstudio> findByEstudianteIdAndSemanaInicio(Long estudianteId, LocalDate semanaInicio);

    long countByEstudianteId(Long estudianteId);
}
