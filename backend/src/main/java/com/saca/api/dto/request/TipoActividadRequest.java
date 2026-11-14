package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoActividadRequest(
        @NotBlank(message = "El nombre del tipo de actividad es obligatorio")
        @Size(max = 120, message = "El nombre no debe superar 120 caracteres")
        String nombre) {
}
