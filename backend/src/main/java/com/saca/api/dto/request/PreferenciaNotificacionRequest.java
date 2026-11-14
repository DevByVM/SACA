package com.saca.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record PreferenciaNotificacionRequest(
        @NotNull(message = "Debe indicar los tipos de actividad para las notificaciones")
        Set<Integer> tiposActividadHabilitados
) {
}
