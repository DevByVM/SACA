package com.saca.api.dto;

import java.time.Instant;

// token y expiraEn solo se informan cuando el inicio de sesión es correcto
public record LoginResponse(
        boolean autenticado,
        String mensaje,
        Long estudianteId,
        String nombre,
        String carnet,
        String correoInstitucional,
        String token,
        Instant expiraEn,
        String rol) {

    public LoginResponse(boolean autenticado, String mensaje, Long estudianteId, String nombre,
            String carnet, String correoInstitucional, String token, Instant expiraEn) {
        this(autenticado, mensaje, estudianteId, nombre, carnet, correoInstitucional, token, expiraEn,
                autenticado ? "ESTUDIANTE" : null);
    }

    public static LoginResponse error(String mensaje) {
        return new LoginResponse(false, mensaje, null, null, null, null, null, null, null);
    }
}
