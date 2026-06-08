package com.saca.api.service;

import com.saca.api.dto.request.ActividadAcademicaRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.entity.*;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActividadAcademicaService {

    private final ActividadAcademicaRepository actividadRepository;
    private final ActividadAcademicaMapper mapper;
    private final NotaRepository notaRepository;
    private final MateriaInscritaRepository materiaInscritaRepository;
    private final EstudianteRepository estudianteRepository;

    public ActividadAcademicaService(ActividadAcademicaRepository actividadRepository, ActividadAcademicaMapper mapper,
                                     NotaRepository notaRepository, MateriaInscritaRepository materiaInscritaRepository, EstudianteRepository estudianteRepository) {
        this.actividadRepository = actividadRepository;
        this.mapper = mapper;
        this.notaRepository = notaRepository;
        this.materiaInscritaRepository = materiaInscritaRepository;
        this.estudianteRepository = estudianteRepository;
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

        MateriaInscrita materia = materiaInscritaRepository.getReferenceById(request.materiaInscritaId());
        ActividadAcademica actividad = mapper.toEntity(request, materia);
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
        actividadActualizada.setTipoActividad(request.tipoActividad());

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

    @Transactional(readOnly = true)
    public List<ActividadAcademicaResponse> obtenerPorCicloActivoYEstdiante(Long estudianteId){
        if(estudianteRepository.existsById(estudianteId)){
            return actividadRepository.findActividadesCicloActivoPorEstudiante(estudianteId)
                    .stream()
                    .map(mapper::toResponse)
                    .toList();
        }else{
            throw new RecursoNoEncontradoException("No se encontró el estudiante solicitado");
        }
    }

    @Transactional(readOnly = true)
    public List<ActividadAcademicaResponse> obtenerPorCicloYEstdiante(Long cicloId, Long estudianteId){
        if(estudianteRepository.existsById(estudianteId)){
            return actividadRepository.findActividadesPorCicloYEstudiante(cicloId,estudianteId)
                    .stream()
                    .map(mapper::toResponse)
                    .toList();
        }else{
            throw new RecursoNoEncontradoException("No se encontró el estudiante solicitado");
        }
    }

    private void validarFechas(LocalDateTime fechaInicio, LocalDateTime fechaEntrega) {
        if (fechaInicio.isAfter(fechaEntrega)) {
            throw new ValidacionNegocioException("La fecha de inicio no puede ser posterior a la fecha de entrega");
        }
    }
}
