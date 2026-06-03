package com.saca.api.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(
			RecursoNoEncontradoException exception,
			HttpServletRequest request) {
		return construirRespuesta(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
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

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> manejarErrorGeneral(Exception exception, HttpServletRequest request) {
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
