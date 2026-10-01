package com.saca.api.service;

import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.entity.Estudiante;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.mapper.ActividadAcademicaMapper;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.EstudianteRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionService {

    private final ActividadAcademicaRepository actividadRepository;
    private final PreferenciaNotificacionRepository preferenciaRepository;
    private final EstudianteRepository estudianteRepository;
    private final ActividadAcademicaMapper actividadMapper;
    private final Clock relojAplicacion;

    public NotificacionService(
            ActividadAcademicaRepository actividadRepository,
            PreferenciaNotificacionRepository preferenciaRepository,
            EstudianteRepository estudianteRepository,
            ActividadAcademicaMapper actividadMapper,
            Clock relojAplicacion
    ) {
        this.actividadRepository = actividadRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.estudianteRepository = estudianteRepository;
        this.actividadMapper = actividadMapper;
        this.relojAplicacion = relojAplicacion;
    }

    @Transactional(readOnly = true)
    public List<ActividadAcademicaResponse> obtenerProximasPorEstudiante(Long estudianteId) {
        validarEstudiante(estudianteId);

        return preferenciaRepository.findByEstudianteId(estudianteId)
                .map(preferencia -> buscarActividadesProximas(estudianteId, preferencia))
                .orElseGet(List::of);
    }

    private List<ActividadAcademicaResponse> buscarActividadesProximas(
            Long estudianteId,
            com.saca.api.entity.PreferenciaNotificacion preferencia
    ) {
        if (preferencia.getTiposActividadHabilitados().isEmpty()) {
            return List.of();
        }

        LocalDate hoy = LocalDate.now(relojAplicacion);
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.plusDays(8).atStartOfDay();

        return actividadRepository.findNotificacionesProximasPorEstudiante(
                        estudianteId,
                        inicio,
                        fin,
                        preferencia.getTiposActividadHabilitados()
                )
                .stream()
                .map(actividadMapper::toResponse)
                .toList();
    }

    private void validarEstudiante(Long estudianteId) {
        if (estudianteId == null || estudianteId <= 0) {
            throw new ValidacionNegocioException("El estudiante indicado no es válido");
        }

        if (!estudianteRepository.existsById(estudianteId)) {
            throw new RecursoNoEncontradoException("Estudiante no encontrado");
        }
    }
}
