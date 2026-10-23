package com.saca.api.service;

import com.saca.api.dto.request.ActividadAcademicaRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.entity.*;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.time.LocalDate;

@Service
public class ActividadAcademicaService {

    private static final String NOTA_NO_ENCONTRADA = "No se encontró la nota solicitada";

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

    // Todas las operaciones reciben el id del estudiante autenticado (sub del JWT); una actividad,
    // materia o nota ajena responde igual que una inexistente
    @Transactional(readOnly = true)
    public ActividadAcademicaResponse getById(Long id, Long estudianteId){

        return mapper.toResponse(buscarActividadDelEstudiante(id, estudianteId));
    }

    @Transactional
    public ActividadAcademicaResponse crear(ActividadAcademicaRequest request, Long estudianteId) {
        //validando fechas de inicio y entrega
        validarFechas(request.fechaInicio(), request.fechaEntrega());

        MateriaInscrita materia = buscarMateriaDelEstudiante(request.materiaInscritaId(), estudianteId);
        ActividadAcademica actividad = mapper.toEntity(request, materia);
        // Una actividad nueva aún no tiene nota propia: un notaId solo podría apuntar a la nota de otra
        // actividad o a una nota sin actividad, cuyo propietario no se puede comprobar
        if(request.notaId() != null){
            throw new RecursoNoEncontradoException(NOTA_NO_ENCONTRADA);
        }
        if (request.notaObtenida() != null) {
            Nota nota = new Nota();
            nota.setValorObtenido(request.notaObtenida());
            nota.setFechaRegistro(LocalDate.now());
            Nota notaGuardada = notaRepository.save(nota);
            actividad.setNota(notaGuardada);
        }
        ActividadAcademica itemGuardado = actividadRepository.save(actividad);
        return mapper.toResponse(itemGuardado);
    }

    @Transactional
    public ActividadAcademicaResponse actualizar(ActividadAcademicaRequest request, Long actividadId, Long estudianteId) {

        //se valida la existencia de la actividad y que sea del estudiante
        ActividadAcademica actividadExistente = buscarActividadDelEstudiante(actividadId, estudianteId);

        //validando fechas de inicio y entrega
        validarFechas(request.fechaInicio(), request.fechaEntrega());

        // La materia de destino también debe ser del estudiante: así la actividad no puede pasar a otro
        MateriaInscrita materia = buscarMateriaDelEstudiante(request.materiaInscritaId(), estudianteId);
        // Solo se admite la nota que esta misma actividad ya tiene enlazada
        if (request.notaObtenida() == null && request.notaId() != null
                && (actividadExistente.getNota() == null || actividadExistente.getNota().getId() != request.notaId())) {
            throw new RecursoNoEncontradoException(NOTA_NO_ENCONTRADA);
        }

        //se mapea el objeto
        ActividadAcademica actividadActualizada = mapper.updateEntity(actividadExistente, request);
        actividadActualizada.setTipoActividad(request.tipoActividad());
        actividadActualizada.setMateriaInscrita(materia);

        // se verifica si se registró, actualizó o eliminó una nota obtenida
        if (request.notaObtenida() != null) {
            Nota nota = actividadExistente.getNota();

            if (nota == null) {
                nota = new Nota();
            }

            nota.setValorObtenido(request.notaObtenida());
            nota.setFechaRegistro(LocalDate.now());

            Nota notaGuardada = notaRepository.save(nota);
            actividadActualizada.setNota(notaGuardada);

        } else if (request.notaId() != null) {
            // Ya comprobado arriba: es la nota actual de la actividad, que se conserva
            actividadActualizada.setNota(actividadExistente.getNota());

        } else {
            actividadActualizada.setNota(null);
        }

        ActividadAcademica itemActualizado = actividadRepository.save(actividadActualizada);
        return mapper.toResponse(itemActualizado);
    }

    @Transactional
    public void eliminar(Long actividadId, Long estudianteId){
        actividadRepository.delete(buscarActividadDelEstudiante(actividadId, estudianteId));
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
            // La consulta declara (idEstudiante, idCiclo); antes se pasaban en orden inverso
            return actividadRepository.findActividadesPorCicloYEstudiante(estudianteId, cicloId)
                    .stream()
                    .map(mapper::toResponse)
                    .toList();
        }else{
            throw new RecursoNoEncontradoException("No se encontró el estudiante solicitado");
        }
    }

    private ActividadAcademica buscarActividadDelEstudiante(Long actividadId, Long estudianteId) {
        return actividadRepository.findByIdActividadAndMateriaInscritaCicloAcademicoEstudianteId(actividadId, estudianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la actividad académica solicitada"));
    }

    private MateriaInscrita buscarMateriaDelEstudiante(Long materiaInscritaId, Long estudianteId) {
        if (materiaInscritaId == null) {
            throw new ValidacionNegocioException("La materia es obligatoria");
        }
        return materiaInscritaRepository.findByIdAndCicloAcademicoEstudianteId(materiaInscritaId, estudianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro la materia inscrita solicitada"));
    }

    private void validarFechas(LocalDateTime fechaInicio, LocalDateTime fechaEntrega) {
        if (fechaInicio.isAfter(fechaEntrega)) {
            throw new ValidacionNegocioException("La fecha de inicio no puede ser posterior a la fecha de entrega");
        }
    }
}
