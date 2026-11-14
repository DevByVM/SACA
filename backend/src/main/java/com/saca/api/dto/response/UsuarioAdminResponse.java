package com.saca.api.dto.response;

public record UsuarioAdminResponse(
        Long id,
        String nombre,
        String carnet,
        String correoInstitucional,
        boolean habilitada,
        String estadoCorreo) {
}
