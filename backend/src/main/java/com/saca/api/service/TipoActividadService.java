package com.saca.api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saca.api.dto.request.TipoActividadRequest;
import com.saca.api.dto.response.TipoActividadResponse;
import com.saca.api.entity.TipoActividad;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.ActividadAcademicaRepository;
import com.saca.api.repository.PreferenciaNotificacionRepository;
import com.saca.api.repository.TipoActividadRepository;

@Service
public class TipoActividadService {

    private final TipoActividadRepository tipoActividadRepository;
    private final ActividadAcademicaRepository actividadRepository;
    private final PreferenciaNotificacionRepository preferenciaRepository;

    public TipoActividadService(
            TipoActividadRepository tipoActividadRepository,
            ActividadAcademicaRepository actividadRepository,
            PreferenciaNotificacionRepository preferenciaRepository) {
        this.tipoActividadRepository = tipoActividadRepository;
        this.actividadRepository = actividadRepository;
        this.preferenciaRepository = preferenciaRepository;
    }

    @Transactional(readOnly = true)
    public List<TipoActividadResponse> listarActivos() {
        return tipoActividadRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TipoActividadResponse> listarTodos() {
        return tipoActividadRepository.findAllByOrderByNombreAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TipoActividadResponse crear(TipoActividadRequest request) {
        String nombre = normalizarNombre(request.nombre());
        validarNombreDisponible(nombre, null);

        TipoActividad tipo = new TipoActividad();
        tipo.setNombre(nombre);
        tipo.setActivo(true);
        return toResponse(tipoActividadRepository.save(tipo));
    }

    @Transactional
    public TipoActividadResponse actualizar(Integer id, TipoActividadRequest request) {
        TipoActividad tipo = buscar(id);
        String nombre = normalizarNombre(request.nombre());
        validarNombreDisponible(nombre, id);
        tipo.setNombre(nombre);
        return toResponse(tipo);
    }

    @Transactional
    public void eliminar(Integer id) {
        TipoActividad tipo = buscar(id);
        long actividades = actividadRepository.countByTipoActividadIdTipoActividad(id);
        long preferencias = preferenciaRepository.countByTiposActividadHabilitados_IdTipoActividad(id);
        if (actividades > 0 || preferencias > 0) {
            throw new ValidacionNegocioException(
                    "No se puede eliminar el tipo porque está asociado a actividades o preferencias de notificación");
        }
        tipoActividadRepository.delete(tipo);
    }

    private TipoActividad buscar(Integer id) {
        return tipoActividadRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el tipo de actividad"));
    }

    private void validarNombreDisponible(String nombre, Integer id) {
        boolean existe = id == null
                ? tipoActividadRepository.existsByNombreIgnoreCase(nombre)
                : tipoActividadRepository.existsByNombreIgnoreCaseAndIdTipoActividadNot(nombre, id);
        if (existe) {
            throw new ValidacionNegocioException("Ya existe un tipo de actividad con ese nombre");
        }
    }

    private static String normalizarNombre(String nombre) {
        return nombre.trim().replaceAll("\\s+", " ");
    }

    private TipoActividadResponse toResponse(TipoActividad tipo) {
        return new TipoActividadResponse(tipo.getIdTipoActividad(), tipo.getNombre(), tipo.isActivo());
    }
}
