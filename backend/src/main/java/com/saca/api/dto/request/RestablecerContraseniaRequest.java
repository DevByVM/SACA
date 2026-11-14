package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RestablecerContraseniaRequest(
        @NotBlank @Size(max = 43) String token,
        @NotBlank @Size(max = 72) String contrasenia) {
    @Override public String toString() { return "RestablecerContraseniaRequest[datos protegidos]"; }
}
