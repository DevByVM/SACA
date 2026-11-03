package com.saca.api.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.response.RendimientoCicloResponse;
import com.saca.api.service.RendimientoAcademicoService;

@RestController
@RequestMapping("/api/rendimiento")
public class RendimientoAcademicoController {

	private final RendimientoAcademicoService rendimientoAcademicoService;

	public RendimientoAcademicoController(RendimientoAcademicoService rendimientoAcademicoService) {
		this.rendimientoAcademicoService = rendimientoAcademicoService;
	}

	// Sin cicloId se usa el ciclo activo; un ciclo de otro estudiante responde 404
	@GetMapping
	public RendimientoCicloResponse obtener(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) Long cicloId) {
		return rendimientoAcademicoService.obtenerPorCiclo(cicloId, EstudianteAutenticado.id(jwt));
	}
}
