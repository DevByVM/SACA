package com.saca.api.service;


import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DisponibilidadSemanalService {
    @Autowired
    private DisponibilidadSemanalRepository repository;

    public DisponibilidadSemanal guardarBloque(DisponibilidadSemanal bloque) {
        return repository.save(bloque);
    }

    public List<DisponibilidadSemanal> obtenerHorarioEstudiante(Long estudianteId, Long cicloId) {
        return repository.findByEstudianteIdAndCicloId(estudianteId, cicloId);
    }

    public void eliminarBloque(Long id) {
        repository.deleteById(id);
    }
}
