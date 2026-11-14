package com.saca.api.dto.request;

import jakarta.validation.constraints.*;

public record ActualizarUsuarioAdminRequest(
        @Size(max = 120) String nombre,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}[0-9]{5}$", message = "El carnet debe tener dos letras y cinco números") String carnet,
        @NotBlank @Email @Size(max = 120)
        @Pattern(regexp = "(?i)^[^\\s@]+@ues\\.edu\\.sv$", message = "Usa un correo institucional @ues.edu.sv") String correoInstitucional,
        @NotNull Boolean habilitada) { }
