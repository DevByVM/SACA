package com.saca.api.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(
			RecursoNoEncontradoException exception,
			HttpServletRequest request) {
		return construirRespuesta(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
	}

	// Ruta sin controlador (por ejemplo, las antiguas rutas de perfil por correo o id): es un 404,
	// no un error interno; el resto de excepciones inesperadas siguen respondiendo 500
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> manejarRutaInexistente(
			NoResourceFoundException exception,
			HttpServletRequest request) {
		return construirRespuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado", request.getRequestURI());
	}

	@ExceptionHandler({ ValidacionNegocioException.class, ConflictoHorarioException.class })
	public ResponseEntity<ErrorResponse> manejarValidacionNegocio(
			RuntimeException exception,
			HttpServletRequest request) {
		return construirRespuesta(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> manejarValidacionRequest(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		String message = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(this::formatearErrorCampo)
				.collect(Collectors.joining("; "));

		return construirRespuesta(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
	}

	// Errores de la petición que Spring detecta antes de llegar al controlador. Sin estos manejadores
	// caían en el genérico de Exception y respondían 500. Los mensajes son genéricos a propósito:
	// no repiten el detalle de la excepción ni los valores recibidos.
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return construirRespuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido", request.getRequestURI());
	}

	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<ErrorResponse> manejarParametroInvalido(Exception exception, HttpServletRequest request) {
		return construirRespuesta(HttpStatus.BAD_REQUEST, "Los parámetros de la solicitud no son válidos", request.getRequestURI());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> manejarMetodoNoPermitido(
			HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {
		ResponseEntity<ErrorResponse> respuesta = construirRespuesta(
				HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido para esta ruta", request.getRequestURI());
		return ResponseEntity.status(respuesta.getStatusCode())
				.allow(exception.getSupportedHttpMethods() == null ? new HttpMethod[0]
						: exception.getSupportedHttpMethods().toArray(HttpMethod[]::new))
				.body(respuesta.getBody());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> manejarTipoDeContenido(
			HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request) {
		return construirRespuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no admitido", request.getRequestURI());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> manejarErrorGeneral(Exception exception, HttpServletRequest request) {
		// El detalle solo va al log del servidor, nunca a la respuesta
		log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), exception);
		return construirRespuesta(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Ocurrio un error inesperado al procesar la solicitud",
				request.getRequestURI());
	}

	private String formatearErrorCampo(FieldError fieldError) {
		return fieldError.getField() + ": " + fieldError.getDefaultMessage();
	}

	private ResponseEntity<ErrorResponse> construirRespuesta(HttpStatus status, String message, String path) {
		ErrorResponse response = new ErrorResponse(
				LocalDateTime.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				path);

		return ResponseEntity.status(status).body(response);
	}
}
