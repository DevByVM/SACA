package com.saca.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.service.CargaAcademicaService;

@RestController
@RequestMapping("/api/carga")
public class CargaAcademicaController {

	private final CargaAcademicaService cargaAcademicaService;

	public CargaAcademicaController(CargaAcademicaService cargaAcademicaService) {
		this.cargaAcademicaService = cargaAcademicaService;
	}

	// Siempre la carga del estudiante del token, con las actividades de sus ciclos activos
	@GetMapping("/semanal")
	public CargaSemanalResponse obtenerCargaSemanal(@AuthenticationPrincipal Jwt jwt) {
		return cargaAcademicaService.obtenerCargaSemanal(EstudianteAutenticado.id(jwt));
	}
}
