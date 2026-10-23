package com.saca.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

// Solo las rutas sin controlador pasan a 404; los errores inesperados no se disfrazan de "no encontrado"
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/perfil/correo/x@ues.edu.sv");

    @Test
    void unaRutaSinControladorResponde404() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarRutaInexistente(
                new NoResourceFoundException(HttpMethod.GET, "api/auth/perfil/correo/x@ues.edu.sv"), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody().status()).isEqualTo(404);
        assertThat(respuesta.getBody().message()).isEqualTo("Recurso no encontrado");
        assertThat(respuesta.getBody().path()).isEqualTo("/api/auth/perfil/correo/x@ues.edu.sv");
    }

    @Test
    void unRecursoDeNegocioNoEncontradoConservaSu404YSuMensaje() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarRecursoNoEncontrado(
                new RecursoNoEncontradoException("Estudiante no encontrado"), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(404);
        assertThat(respuesta.getBody().message()).isEqualTo("Estudiante no encontrado");
    }

    @Test
    void unErrorInesperadoSigueSiendo500YNoRevelaElDetalle() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarErrorGeneral(
                new IllegalStateException("detalle interno que no debe salir"), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
        assertThat(respuesta.getBody().message()).doesNotContain("detalle interno");
    }

    @Test
    void unaExcepcionQueSoloMencionaNotFoundNoSeConvierteEn404() {
        ResponseEntity<ErrorResponse> respuesta = handler.manejarErrorGeneral(
                new RuntimeException("Not found en un servicio externo"), request);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(500);
    }
}
