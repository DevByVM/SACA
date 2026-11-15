package com.saca.api.dto.request;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmarHorarioEstudioRequest(
        @NotNull(message = "La semana del horario es obligatoria") LocalDate desde,
        @NotNull(message = "Los bloques del horario son obligatorios")
        @Size(min = 1, message = "El horario debe tener al menos un bloque de estudio")
        @Size(max = 200, message = "El horario no debe superar 200 bloques")
        List<@Valid @NotNull(message = "El bloque no puede estar vacío") BloqueHorarioEstudioRequest> bloques) {
}
