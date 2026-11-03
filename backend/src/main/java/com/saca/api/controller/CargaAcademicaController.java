package com.saca.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.response.CargaPorSemanasResponse;
import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.service.CargaAcademicaService;
import com.saca.api.service.CargaSemanasService;

@RestController
@RequestMapping("/api/carga")
public class CargaAcademicaController {

	private final CargaAcademicaService cargaAcademicaService;
	private final CargaSemanasService cargaSemanasService;

	public CargaAcademicaController(CargaAcademicaService cargaAcademicaService, CargaSemanasService cargaSemanasService) {
		this.cargaAcademicaService = cargaAcademicaService;
		this.cargaSemanasService = cargaSemanasService;
	}

	// Siempre la carga del estudiante del token, con las actividades de sus ciclos activos
	@GetMapping("/semanal")
	public CargaSemanalResponse obtenerCargaSemanal(@AuthenticationPrincipal Jwt jwt) {
		return cargaAcademicaService.obtenerCargaSemanal(EstudianteAutenticado.id(jwt));
	}

	// SDACAS-26: semanas desde la actual hasta el fin del ciclo activo; el rango lo fija el ciclo, no el cliente
	@GetMapping("/semanas")
	public CargaPorSemanasResponse obtenerCargaPorSemanas(@AuthenticationPrincipal Jwt jwt) {
		return cargaSemanasService.obtenerCargaPorSemanas(EstudianteAutenticado.id(jwt));
	}
}
