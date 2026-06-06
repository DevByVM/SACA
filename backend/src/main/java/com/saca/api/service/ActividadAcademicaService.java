package com.saca.api.service;

import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.ActividadAcademicaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActividadAcademicaService {

    private final ActividadAcademicaRepository actividadRepository;
    private final ActividadAcademicaMapper mapper;

    public ActividadAcademicaService(ActividadAcademicaRepository actividadRepository, ActividadAcademicaMapper mapper) {
        this.actividadRepository = actividadRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ActividadAcademicaResponse getById(Long id){

        return actividadRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Actividad académica no encontrada"
                ));
    }
}
