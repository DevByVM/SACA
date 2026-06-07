package com.saca.api.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saca.api.dto.CalendarioDTO;
import com.saca.api.entity.HorarioClase;
import com.saca.api.repository.HorarioClaseRepository;

@Service
public class CalendarioService {

    @Autowired
    private HorarioClaseRepository repository;

    public List<CalendarioDTO> obtenerCalendarioSemanal(Long estudianteId) {

        List<HorarioClase> horarios =
                repository.findByMateriaInscritaCicloAcademicoEstudianteIdOrderByDiaSemanaAscHoraInicioAsc(
                        estudianteId);

        return horarios.stream()
                .map(h -> new CalendarioDTO(
                        h.getMateriaInscrita().getNombre(),
                        h.getMateriaInscrita().getCodigo(),
                        h.getDiaSemana().name(),
                        h.getHoraInicio(),
                        h.getHoraFin(),
                        h.getModalidad().name(),
                        h.getDocenteTutor()
                ))
                .toList();
    }
}