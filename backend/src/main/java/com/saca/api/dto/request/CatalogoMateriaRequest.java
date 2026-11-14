package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CatalogoMateriaRequest(
        @NotBlank(message = "El código de la materia es obligatorio")
        @Size(max = 30, message = "El código no debe superar 30 caracteres")
        String codigo,

        @NotBlank(message = "El nombre de la materia es obligatorio")
        @Size(max = 120, message = "El nombre no debe superar 120 caracteres")
        String nombre) {
}
