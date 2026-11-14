package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CrearUsuarioAdminRequest(
        @NotBlank(message = "El carnet es obligatorio")
        @Pattern(regexp = "^[A-Za-z]{2}[0-9]{5}$", message = "El carnet debe tener dos letras y cinco números")
        String carnet) {
}
