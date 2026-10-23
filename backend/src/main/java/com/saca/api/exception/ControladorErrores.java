package com.saca.api.exception;

import java.time.LocalDateTime;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

// Sustituye a la página /error de Spring Boot (que llamada directamente respondía 500 con status 999)
// por el mismo ErrorResponse JSON del resto de la API. Nunca incluye el mensaje de la excepción ni trazas.
@RestController
public class ControladorErrores implements ErrorController {

	@RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
		// Sin un error real detrás (petición directa a /error) no hay nada que mostrar: se trata como inexistente
		Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		HttpStatus status = codigo instanceof Integer valor && HttpStatus.resolve(valor) != null
				? HttpStatus.resolve(valor)
				: HttpStatus.NOT_FOUND;

		Object rutaOriginal = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		String ruta = rutaOriginal != null ? rutaOriginal.toString() : request.getRequestURI();

		return ResponseEntity.status(status).body(new ErrorResponse(
				LocalDateTime.now(), status.value(), status.getReasonPhrase(), mensajePara(status), ruta));
	}

	private static String mensajePara(HttpStatus status) {
		if (status == HttpStatus.NOT_FOUND) {
			return "Recurso no encontrado";
		}
		if (status.is5xxServerError()) {
			return "Ocurrio un error inesperado al procesar la solicitud";
		}
		return "No se pudo procesar la solicitud";
	}
}
