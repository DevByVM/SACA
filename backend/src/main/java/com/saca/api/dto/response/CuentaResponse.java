package com.saca.api.dto.response;

// Respuesta del registro y de la activación. correoInstitucional permite volver al inicio de sesión
// con el correo ya escrito; en el registro es el derivado del carnet, que no revela si la cuenta existe
public record CuentaResponse(String mensaje, String correoInstitucional) {
}
