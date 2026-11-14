package com.saca.api.dto.request;

import java.time.LocalTime;

import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.TipoBloquePlantilla;

import jakarta.validation.constraints.NotNull;

public record FranjaPlantillaRequest(
        @NotNull(message = "El día de la franja es obligatorio") DiaSemana diaSemana,
        @NotNull(message = "La hora de inicio es obligatoria") LocalTime horaInicio,
        @NotNull(message = "La hora de fin es obligatoria") LocalTime horaFin,
        @NotNull(message = "El tipo de bloque es obligatorio") TipoBloquePlantilla tipoBloque) {
}
