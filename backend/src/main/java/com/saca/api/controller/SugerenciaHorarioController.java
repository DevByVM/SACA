package com.saca.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.response.HorarioSugeridoResponse;
import com.saca.api.service.SugerenciaHorarioConsultaService;

@RestController
@RequestMapping("/api/estudiante")
public class SugerenciaHorarioController {

	private final SugerenciaHorarioConsultaService sugerenciaHorarioConsultaService;

	public SugerenciaHorarioController(SugerenciaHorarioConsultaService sugerenciaHorarioConsultaService) {
		this.sugerenciaHorarioConsultaService = sugerenciaHorarioConsultaService;
	}

	@GetMapping("/horario-sugerido")
	public HorarioSugeridoResponse obtenerHorarioSugerido(@AuthenticationPrincipal Jwt jwt) {
		return sugerenciaHorarioConsultaService.obtener(EstudianteAutenticado.id(jwt));
	}
}
