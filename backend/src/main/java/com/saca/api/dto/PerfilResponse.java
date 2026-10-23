package com.saca.api.dto;

import com.saca.api.entity.Estudiante;

// Solo los datos que muestra la pantalla de perfil; nunca contraseña ni estado de bloqueo
public record PerfilResponse(Long id, String nombre, String carnet, String correoInstitucional) {

    public static PerfilResponse de(Estudiante estudiante) {
        return new PerfilResponse(estudiante.getId(), estudiante.getNombre(), estudiante.getCarnet(),
                estudiante.getCorreoInstitucional());
    }
}
