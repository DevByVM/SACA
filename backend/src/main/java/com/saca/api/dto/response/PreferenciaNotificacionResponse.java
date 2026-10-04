package com.saca.api.dto.response;

import com.saca.api.entity.TipoActividadAcademica;

import java.util.List;

public record PreferenciaNotificacionResponse(
        Long estudianteId,
        List<TipoActividadAcademica> tiposDisponibles,
        List<TipoActividadAcademica> tiposActividadHabilitados
) {
}
