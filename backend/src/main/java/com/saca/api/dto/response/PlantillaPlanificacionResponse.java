package com.saca.api.dto.response;

import java.util.List;

import com.saca.api.entity.PerfilPlantillaPlanificacion;

public record PlantillaPlanificacionResponse(
        Long id, String nombre, PerfilPlantillaPlanificacion perfil, boolean activa,
        List<FranjaPlantillaResponse> franjas) {
}
