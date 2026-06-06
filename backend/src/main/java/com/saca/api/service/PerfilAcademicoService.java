package com.saca.api.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saca.api.dto.ActualizarPerfilAcademicoRequest;
import com.saca.api.dto.PerfilAcademicoResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PerfilAcademico;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PerfilAcademicoRepository;

@Service
public class PerfilAcademicoService {

    @Autowired
    private PerfilAcademicoRepository repository;

    @Autowired
    private EstudianteRepository estudianteRepository;

    
    public PerfilAcademicoResponse obtenerPorEstudiante(Long estudianteId) {

        PerfilAcademico p = repository.findByEstudianteId(estudianteId)
                .orElse(null);

        if (p == null) return null;

        PerfilAcademicoResponse dto = new PerfilAcademicoResponse();
        dto.setCarrera(p.getCarrera());
        dto.setFacultad(p.getFacultad());
        dto.setFechaIngreso(p.getFechaIngreso());
        dto.setPlanEstudio(p.getPlanEstudio());

        return dto;
    }

    
    public String guardarOActualizar(Long estudianteId, ActualizarPerfilAcademicoRequest request) {

        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElse(null);

        if (estudiante == null) {
            return "Estudiante no encontrado";
        }

        Optional<PerfilAcademico> existente =
                repository.findByEstudianteId(estudianteId);

        PerfilAcademico perfil = existente.orElse(new PerfilAcademico());

        perfil.setEstudiante(estudiante);
        perfil.setCarrera(request.getCarrera());
        perfil.setFacultad(request.getFacultad());
        perfil.setFechaIngreso(request.getFechaIngreso());
        perfil.setPlanEstudio(request.getPlanEstudio());

        repository.save(perfil);

        return "Perfil académico actualizado correctamente";
    }
}