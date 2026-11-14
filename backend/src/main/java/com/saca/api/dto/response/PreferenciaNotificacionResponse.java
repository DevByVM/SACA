package com.saca.api.dto.response;

import java.util.List;

public record PreferenciaNotificacionResponse(
        Long estudianteId,
        List<TipoActividadResponse> tiposDisponibles,
        List<Integer> tiposActividadHabilitados
) {
}
