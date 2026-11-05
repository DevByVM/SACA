package com.saca.api.dto.response;

// Estado de un enlace de activación. Solo un enlace válido devuelve el carnet y el correo de la cuenta
public record ConsultaActivacionResponse(String estado, String carnet, String correoInstitucional) {

    public static ConsultaActivacionResponse sinDatos(String estado) {
        return new ConsultaActivacionResponse(estado, null, null);
    }
}
