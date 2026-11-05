package com.saca.api.dto.request;

public record ConsultaActivacionRequest(String token) {

    // El token no debe aparecer si la petición llega a registrarse
    @Override
    public String toString() {
        return "ConsultaActivacionRequest[]";
    }
}
