package com.saca.api.dto.request;

import java.util.List;

import com.saca.api.entity.PerfilPlantillaPlanificacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlantillaPlanificacionRequest(
        @NotBlank(message = "El nombre de la plantilla es obligatorio")
        @Size(max = 120, message = "El nombre no debe superar 120 caracteres")
        String nombre,
        @NotNull(message = "El perfil de la plantilla es obligatorio") PerfilPlantillaPlanificacion perfil,
        boolean activa,
        @NotNull(message = "Las franjas de la plantilla son obligatorias")
        @Size(min = 1, message = "La plantilla debe tener al menos una franja")
        List<@Valid FranjaPlantillaRequest> franjas) {
}
