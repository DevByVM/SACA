package com.saca.api.dto.response;

import java.time.LocalTime;

import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.TipoBloquePlantilla;

public record FranjaPlantillaResponse(
        Long id, DiaSemana diaSemana, LocalTime horaInicio, LocalTime horaFin, TipoBloquePlantilla tipoBloque) {
}
