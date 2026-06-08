package com.saca.api.mapper;

import com.saca.api.dto.request.ActividadAcademicaRequest;
import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.MateriaInscrita;
import com.saca.api.entity.Nota;
import com.saca.api.entity.TipoActividad;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ActividadAcademicaMapper {
    public ActividadAcademicaResponse toResponse(ActividadAcademica actividadAcademica) {
        return new ActividadAcademicaResponse(
                actividadAcademica.getIdActividad(),
                actividadAcademica.getNombre(),
                actividadAcademica.getFechaInicio(),
                actividadAcademica.getFechaEntrega(),
                actividadAcademica.getPorcentajeEvaluacion(),
                actividadAcademica.getTiempoEstimadoHoras(),
                actividadAcademica.getEstado(),
                actividadAcademica.getFechaCompletada(),
                actividadAcademica.getTipoActividad().getIdTipoActividad(),
                actividadAcademica.getMateriaInscrita().getId(),
                Optional.ofNullable(actividadAcademica.getNota()).map(Nota::getId).orElse(null),
                actividadAcademica.getTipoActividad().getNombre(),
                actividadAcademica.getMateriaInscrita().getCodigo()
        );
    }

    public ActividadAcademica toEntity(ActividadAcademicaRequest crearActividad, TipoActividad tipo, MateriaInscrita materiaInscrita){
        ActividadAcademica actividad = new ActividadAcademica();
        setCampos(crearActividad, actividad);
        actividad.setTipoActividad(tipo);
        actividad.setMateriaInscrita(materiaInscrita);
        return actividad;
    }

    public ActividadAcademica updateEntity(ActividadAcademica actividad, ActividadAcademicaRequest updateActividad){
        setCampos(updateActividad, actividad);
        return actividad;
    }

    private void setCampos(ActividadAcademicaRequest crearActividad, ActividadAcademica actividad) {
        actividad.setNombre(crearActividad.nombre());
        actividad.setFechaInicio(crearActividad.fechaInicio());
        actividad.setFechaEntrega(crearActividad.fechaEntrega());
        actividad.setPorcentajeEvaluacion(crearActividad.porcentajeEvaluacion());
        actividad.setTiempoEstimadoHoras(crearActividad.tiempoEstimadoHoras());
        actividad.setEstado(crearActividad.estado());
        actividad.setFechaCompletada(crearActividad.fechaCompletada());
    }
}
