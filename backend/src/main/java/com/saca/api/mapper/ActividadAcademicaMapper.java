package com.saca.api.mapper;

import com.saca.api.dto.request.CrearActividadAcademicaRequest;
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
                actividadAcademica.getFechaEntrega(),
                actividadAcademica.getPorcentajeEvaluacion(),
                actividadAcademica.getTiempoEstimadoHoras(),
                actividadAcademica.getEstado(),
                actividadAcademica.getFechaCompletada(),
                actividadAcademica.getTipoActividad().getIdTipoActividad(),
                actividadAcademica.getMateriaInscrita().getId(),
                Optional.ofNullable(actividadAcademica.getNota()).map(Nota::getId).orElse(null)
        );
    }

    public ActividadAcademica toEntity(CrearActividadAcademicaRequest crearActividad, TipoActividad tipo, MateriaInscrita materiaInscrita){
        ActividadAcademica actividad = new ActividadAcademica();
        actividad.setNombre(crearActividad.nombre());
        actividad.setFechaEntrega(crearActividad.fechaEntrega());
        actividad.setPorcentajeEvaluacion(crearActividad.porcentajeEvaluacion());
        actividad.setTiempoEstimadoHoras(crearActividad.tiempoEstimadoHoras());
        actividad.setEstado(crearActividad.estado());
        actividad.setFechaCompletada(crearActividad.fechaCompletada());
        actividad.setTipoActividad(tipo);
        actividad.setMateriaInscrita(materiaInscrita);
        return actividad;
    }
}
