package com.saca.api.mapper;

import com.saca.api.dto.response.ActividadAcademicaResponse;
import com.saca.api.entity.ActividadAcademica;
import com.saca.api.entity.Nota;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
}
