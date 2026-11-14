package com.saca.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ParametrosCargaRequest(
        @NotNull(message = "El primer umbral es obligatorio")
        @Positive(message = "El primer umbral debe ser positivo") Double umbralElevada,
        @NotNull(message = "El segundo umbral es obligatorio")
        @Positive(message = "El segundo umbral debe ser positivo") Double umbralCritica) {
}
