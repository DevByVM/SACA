package com.saca.api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.request.ConfirmarHorarioEstudioRequest;
import com.saca.api.dto.response.HorarioEstudioResponse;
import com.saca.api.service.HorarioEstudioService;

import jakarta.validation.Valid;

// Horario de estudio confirmado del estudiante del token. Sin horario para la semana responde 204.
@RestController
@RequestMapping("/api/estudiante/horario-estudio")
public class HorarioEstudioController {

	private final HorarioEstudioService horarioEstudioService;

	public HorarioEstudioController(HorarioEstudioService horarioEstudioService) {
		this.horarioEstudioService = horarioEstudioService;
	}

	@GetMapping
	public ResponseEntity<HorarioEstudioResponse> obtener(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
		return horarioEstudioService.obtener(EstudianteAutenticado.id(jwt), desde)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	// PUT porque confirmar otra vez la misma semana reemplaza el horario en lugar de crear otro
	@PutMapping
	public HorarioEstudioResponse confirmar(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody ConfirmarHorarioEstudioRequest solicitud) {
		return horarioEstudioService.confirmar(EstudianteAutenticado.id(jwt), solicitud);
	}
}
