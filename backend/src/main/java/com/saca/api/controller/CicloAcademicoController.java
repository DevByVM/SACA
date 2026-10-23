package com.saca.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.dto.request.ActualizarCicloAcademicoRequest;
import com.saca.api.dto.request.CrearCicloAcademicoRequest;
import com.saca.api.dto.response.CicloAcademicoResponse;
import com.saca.api.entity.EstadoCiclo;
import com.saca.api.service.CicloAcademicoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ciclos")
public class CicloAcademicoController {

	private final CicloAcademicoService cicloAcademicoService;

	public CicloAcademicoController(CicloAcademicoService cicloAcademicoService) {
		this.cicloAcademicoService = cicloAcademicoService;
	}

	@GetMapping
	public List<CicloAcademicoResponse> listar(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam Long estudianteId,
			@RequestParam(required = false) EstadoCiclo estado) {
		if (estado != null) {
			return cicloAcademicoService.listarPorEstudianteYEstado(EstudianteAutenticado.idPropio(jwt, estudianteId), estado);
		}

		return cicloAcademicoService.listarPorEstudiante(EstudianteAutenticado.idPropio(jwt, estudianteId));
	}

	@GetMapping("/{cicloId}")
	public CicloAcademicoResponse obtenerPorId(
			@PathVariable Long cicloId,
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam Long estudianteId) {
		return cicloAcademicoService.obtenerPorId(cicloId, EstudianteAutenticado.idPropio(jwt, estudianteId));
	}

	// El propietario es siempre el estudiante del token; un estudianteId distinto en el cuerpo responde 404
	@PostMapping
	public ResponseEntity<CicloAcademicoResponse> crear(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CrearCicloAcademicoRequest request) {
		CicloAcademicoResponse response = cicloAcademicoService.crear(
				request, EstudianteAutenticado.idPropio(jwt, request.estudianteId()));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PutMapping("/{cicloId}")
	public CicloAcademicoResponse actualizar(
			@PathVariable Long cicloId,
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam Long estudianteId,
			@Valid @RequestBody ActualizarCicloAcademicoRequest request) {
		return cicloAcademicoService.actualizar(cicloId, EstudianteAutenticado.idPropio(jwt, estudianteId), request);
	}

	@DeleteMapping("/{cicloId}")
	public ResponseEntity<Void> eliminar(
			@PathVariable Long cicloId,
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam Long estudianteId) {
		cicloAcademicoService.eliminar(cicloId, EstudianteAutenticado.idPropio(jwt, estudianteId));
		return ResponseEntity.noContent().build();
	}
}
