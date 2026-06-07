package com.saca.api.service;

import com.saca.api.dto.request.ActividadAcademicaRequest;
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

import java.time.LocalDateTime;
import java.util.Objects;

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
    public ActividadAcademicaResponse crear(ActividadAcademicaRequest request) {
        //validando fechas de inicio y entrega
        validarFechas(request.fechaInicio(), request.fechaEntrega());
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
    public ActividadAcademicaResponse actualizar(ActividadAcademicaRequest request, Long actividadId) {

        //se valida la existencia de la actividad
        ActividadAcademica actividadExistente = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Actividad Academica no encontrada"
                ));

        //validando fechas de inicio y entrega
        validarFechas(request.fechaInicio(), request.fechaEntrega());

        //se mapea el objeto
        ActividadAcademica actividadActualizada = mapper.updateEntity(actividadExistente, request);

        //se verifica que hubo cambio de tipo
        if (actividadExistente.getTipoActividad().getIdTipoActividad() != request.tipoActividadId()){
            TipoActividad tipo = tipoActividadRepository.getReferenceById(request.tipoActividadId());
            actividadActualizada.setTipoActividad(tipo);
        }
        //se verifica si hubo cambio de materia
        if(actividadExistente.getMateriaInscrita().getId() != request.materiaInscritaId()){
            MateriaInscrita materia = materiaInscritaRepository.getReferenceById(request.materiaInscritaId());
            actividadActualizada.setMateriaInscrita(materia);

        }
        //se verifica si hay nota
        if(actividadExistente.getNota()!=null && actividadExistente.getNota().getId()!=request.notaId()){
            Nota nota = notaRepository.getReferenceById(request.notaId());
            actividadActualizada.setNota(nota);
        }

        ActividadAcademica itemActualizado = actividadRepository.save(actividadActualizada);
        return mapper.toResponse(itemActualizado);
    }

    @Transactional
    public void eliminar(Long actividadId){
        if(actividadRepository.existsById(actividadId)){
            actividadRepository.deleteById(actividadId);
        }else{
            throw new RecursoNoEncontradoException("Actividad académica no encontrada");
        }
    }

    private void validarFechas(LocalDateTime fechaInicio, LocalDateTime fechaEntrega) {
        if (fechaInicio.isAfter(fechaEntrega)) {
            throw new ValidacionNegocioException("La fecha de inicio no puede ser posterior a la fecha de entrega");
        }
    }
}
