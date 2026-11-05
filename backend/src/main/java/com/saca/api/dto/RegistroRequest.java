package com.saca.api.dto;

// El registro solo recibe el carnet. El correo se deriva de él; si se envía, debe coincidir. Nombre y
// contraseña se eligen en la activación (si un cliente antiguo los envía, se ignoran)
public class RegistroRequest {

    private String carnet;
    private String correoInstitucional;

    public String getCarnet() {
        return carnet;
    }

    public void setCarnet(String carnet) {
        this.carnet = carnet;
    }

    public String getCorreoInstitucional() {
        return correoInstitucional;
    }

    public void setCorreoInstitucional(String correoInstitucional) {
        this.correoInstitucional = correoInstitucional;
    }
}
