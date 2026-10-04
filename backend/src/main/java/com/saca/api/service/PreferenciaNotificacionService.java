package com.saca.api.service;

import com.saca.api.dto.request.PreferenciaNotificacionRequest;
import com.saca.api.dto.response.PreferenciaNotificacionResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
public class PreferenciaNotificacionService {

    private final PreferenciaNotificacionRepository preferenciaRepository;
    private final EstudianteRepository estudianteRepository;

    public PreferenciaNotificacionService(
            PreferenciaNotificacionRepository preferenciaRepository,
            EstudianteRepository estudianteRepository
    ) {
        this.preferenciaRepository = preferenciaRepository;
        this.estudianteRepository = estudianteRepository;
    }

    @Transactional(readOnly = true)
    public PreferenciaNotificacionResponse obtenerPorEstudiante(Long estudianteId) {
        Estudiante estudiante = obtenerEstudiante(estudianteId);

        return preferenciaRepository.findByEstudianteId(estudiante.getId())
                .map(preferencia -> construirRespuesta(estudiante.getId(), preferencia.getTiposActividadHabilitados()))
                .orElseGet(() -> construirRespuesta(estudiante.getId(), List.of()));
    }

    @Transactional
    public PreferenciaNotificacionResponse guardarOActualizar(
            Long estudianteId,
            PreferenciaNotificacionRequest request
    ) {
        Estudiante estudiante = obtenerEstudiante(estudianteId);

        PreferenciaNotificacion preferencia = preferenciaRepository
                .findByEstudianteId(estudiante.getId())
                .orElseGet(PreferenciaNotificacion::new);

        preferencia.setEstudiante(estudiante);
        preferencia.setTiposActividadHabilitados(request.tiposActividadHabilitados());

        PreferenciaNotificacion guardada = preferenciaRepository.save(preferencia);
        return construirRespuesta(estudiante.getId(), guardada.getTiposActividadHabilitados());
    }

    private Estudiante obtenerEstudiante(Long estudianteId) {
        if (estudianteId == null || estudianteId <= 0) {
            throw new ValidacionNegocioException("El estudiante indicado no es válido");
        }

        return estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante no encontrado"));
    }

    private PreferenciaNotificacionResponse construirRespuesta(
            Long estudianteId,
            Collection<TipoActividadAcademica> tiposHabilitados
    ) {
        return new PreferenciaNotificacionResponse(
                estudianteId,
                Arrays.asList(TipoActividadAcademica.values()),
                ordenarTipos(tiposHabilitados)
        );
    }

    private List<TipoActividadAcademica> ordenarTipos(Collection<TipoActividadAcademica> tiposActividad) {
        return tiposActividad.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }
}
