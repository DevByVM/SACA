package com.saca.api.service;

import com.saca.api.dto.request.PreferenciaNotificacionRequest;
import com.saca.api.dto.response.PreferenciaNotificacionResponse;
import com.saca.api.dto.response.TipoActividadResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.entity.PreferenciaNotificacion;
import com.saca.api.entity.TipoActividad;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import com.saca.api.repository.TipoActividadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PreferenciaNotificacionService {

    private final PreferenciaNotificacionRepository preferenciaRepository;
    private final EstudianteRepository estudianteRepository;
    private final TipoActividadRepository tipoActividadRepository;

    public PreferenciaNotificacionService(
            PreferenciaNotificacionRepository preferenciaRepository,
            EstudianteRepository estudianteRepository,
            TipoActividadRepository tipoActividadRepository
    ) {
        this.preferenciaRepository = preferenciaRepository;
        this.estudianteRepository = estudianteRepository;
        this.tipoActividadRepository = tipoActividadRepository;
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
        preferencia.setTiposActividadHabilitados(resolverTipos(request.tiposActividadHabilitados()));

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
            Collection<TipoActividad> tiposHabilitados
    ) {
        return new PreferenciaNotificacionResponse(
                estudianteId,
                tipoActividadRepository.findByActivoTrueOrderByNombreAsc().stream()
                        .map(this::toResponse)
                        .toList(),
                tiposHabilitados.stream()
                        .map(TipoActividad::getIdTipoActividad)
                        .sorted()
                        .toList()
        );
    }

    private Set<TipoActividad> resolverTipos(Set<Integer> ids) {
        if (ids == null || ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ValidacionNegocioException("Los tipos de actividad indicados no son válidos");
        }
        List<TipoActividad> encontrados = tipoActividadRepository.findAllById(ids);
        if (encontrados.size() != ids.size() || encontrados.stream().anyMatch(tipo -> !tipo.isActivo())) {
            throw new RecursoNoEncontradoException("Uno o más tipos de actividad no están disponibles");
        }
        return new LinkedHashSet<>(encontrados);
    }

    private TipoActividadResponse toResponse(TipoActividad tipo) {
        return new TipoActividadResponse(tipo.getIdTipoActividad(), tipo.getNombre(), tipo.isActivo());
    }
}
