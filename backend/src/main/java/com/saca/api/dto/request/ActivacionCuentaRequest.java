package com.saca.api.dto.request;

public record ActivacionCuentaRequest(String token, String nombre, String contrasenia) {

    // La contraseña no debe aparecer si la petición llega a registrarse
    @Override
    public String toString() {
        return "ActivacionCuentaRequest[nombre=" + nombre + "]";
    }
}
