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
        dto.setNotaMinimaAprobacion(p.getNotaMinimaAprobacion());

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
        // Solo se actualiza lo que viene en la petición: un campo omitido conserva su valor guardado
        if (request.isCarreraIndicada()) {
            perfil.setCarrera(request.getCarrera());
        }
        if (request.isFacultadIndicada()) {
            perfil.setFacultad(request.getFacultad());
        }
        if (request.isFechaIngresoIndicada()) {
            perfil.setFechaIngreso(request.getFechaIngreso());
        }
        if (request.isPlanEstudioIndicado()) {
            perfil.setPlanEstudio(request.getPlanEstudio());
        }
        if (request.isNotaMinimaAprobacionIndicada()) {
            perfil.setNotaMinimaAprobacion(request.getNotaMinimaAprobacion());
        }

        repository.save(perfil);

        return "Perfil académico actualizado correctamente";
    }
}