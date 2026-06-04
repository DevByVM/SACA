package com.saca.api.dto;

public record LoginResponse(
        boolean autenticado,
        String mensaje,
        Long estudianteId,
        String nombre,
        String carnet,
        String correoInstitucional) {

    public static LoginResponse error(String mensaje) {
        return new LoginResponse(false, mensaje, null, null, null, null);
    }
}
