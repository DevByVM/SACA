package com.saca.api.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

public record BloqueHorarioEstudioRequest(
        @NotNull(message = "La fecha del bloque es obligatoria") LocalDate fecha,
        @NotNull(message = "La hora de inicio es obligatoria") LocalTime inicio,
        @NotNull(message = "La hora de fin es obligatoria") LocalTime fin,
        @NotNull(message = "La materia del bloque es obligatoria") Long materiaInscritaId) {
}
