package com.saca.api.service;

import com.saca.api.dto.request.CrearActividadAcademicaRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.entity.*;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.MateriaInscritaRepository;
import com.saca.api.repository.NotaRepository;
import com.saca.api.repository.TipoActividadRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActividadAcademicaService {

    private final ActividadAcademicaRepository actividadRepository;
    private final ActividadAcademicaMapper mapper;
    private final NotaRepository notaRepository;
    private final TipoActividadRepository tipoActividadRepository;
    private final MateriaInscritaRepository materiaInscritaRepository;

    public ActividadAcademicaService(ActividadAcademicaRepository actividadRepository, ActividadAcademicaMapper mapper,
                                     NotaRepository notaRepository, TipoActividadRepository tipoActividadRepository, MateriaInscritaRepository materiaInscritaRepository) {
        this.actividadRepository = actividadRepository;
        this.mapper = mapper;
        this.notaRepository = notaRepository;
        this.tipoActividadRepository = tipoActividadRepository;
        this.materiaInscritaRepository = materiaInscritaRepository;
    }

    @Transactional(readOnly = true)
    public ActividadAcademicaResponse getById(Long id){

        return actividadRepository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Actividad académica no encontrada"
                ));
    }

    @Transactional
    public ActividadAcademicaResponse crear(CrearActividadAcademicaRequest request) {
        //validando fechas de entrga y terminada
        if(request.fechaCompletada() != null){
            validarFechas(request.fechaEntrega(), request.fechaCompletada());
        }


        TipoActividad tipo = tipoActividadRepository.getReferenceById(request.tipoActividadId());
        MateriaInscrita materia = materiaInscritaRepository.getReferenceById(request.materiaInscritaId());
        ActividadAcademica actividad = mapper.toEntity(request, tipo, materia);
        if(request.notaId() != null){
            Nota nota = notaRepository.getReferenceById(request.notaId());
            actividad.setNota(nota);
        }
        ActividadAcademica itemGuardado = actividadRepository.save(actividad);
        return mapper.toResponse(itemGuardado);
    }

    @Transactional
    public void eliminar(Long actividadId){
        if(actividadRepository.existsById(actividadId)){
            actividadRepository.deleteById(actividadId);
        }else{
            throw new RecursoNoEncontradoException("Actividad académica no encontrada");
        }
    }

    private void validarFechas(java.time.LocalDate fechaEntrega, java.time.LocalDate fechaCompletada) {
        if (fechaEntrega.isAfter(fechaCompletada)) {
            throw new ValidacionNegocioException("La fecha de Entrega no puede ser posterior a la fecha de Completada");
        }
    }
}
