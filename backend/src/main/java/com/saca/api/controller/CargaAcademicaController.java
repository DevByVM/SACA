package com.saca.api.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.response.CargaPorSemanasResponse;
import com.saca.api.dto.response.CargaSemanalResponse;
import com.saca.api.dto.response.TiempoDisponibleResponse;
import com.saca.api.service.CargaAcademicaService;
import com.saca.api.service.CargaSemanasService;
import com.saca.api.service.TiempoDisponibleConsultaService;

@RestController
@RequestMapping("/api/carga")
public class CargaAcademicaController {

	private final CargaAcademicaService cargaAcademicaService;
	private final CargaSemanasService cargaSemanasService;
	private final TiempoDisponibleConsultaService tiempoDisponibleConsultaService;

	public CargaAcademicaController(
			CargaAcademicaService cargaAcademicaService,
			CargaSemanasService cargaSemanasService,
			TiempoDisponibleConsultaService tiempoDisponibleConsultaService) {
		this.cargaAcademicaService = cargaAcademicaService;
		this.cargaSemanasService = cargaSemanasService;
		this.tiempoDisponibleConsultaService = tiempoDisponibleConsultaService;
	}

	// Siempre la carga del estudiante del token, con las actividades de sus ciclos activos
	@GetMapping("/semanal")
	public CargaSemanalResponse obtenerCargaSemanal(@AuthenticationPrincipal Jwt jwt) {
		return cargaAcademicaService.obtenerCargaSemanal(EstudianteAutenticado.id(jwt));
	}

	// Semanas desde la actual hasta el fin del ciclo activo; el rango lo fija el ciclo, no el cliente
	@GetMapping("/semanas")
	public CargaPorSemanasResponse obtenerCargaPorSemanas(@AuthenticationPrincipal Jwt jwt) {
		return cargaSemanasService.obtenerCargaPorSemanas(EstudianteAutenticado.id(jwt));
	}

	// Tiempo de estudio disponible (bloques ESTUDIO menos clases, jornadas y NO_DISPONIBLE) del
	// estudiante del token; informativo, no cambia la carga. Mismo rango máximo que el calendario
	@GetMapping("/tiempo-disponible")
	public TiempoDisponibleResponse obtenerTiempoDisponible(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
		return tiempoDisponibleConsultaService.obtener(EstudianteAutenticado.id(jwt), desde, hasta);
	}
}
