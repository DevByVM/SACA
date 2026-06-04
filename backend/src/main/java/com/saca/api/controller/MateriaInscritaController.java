package com.saca.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
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

import com.saca.api.dto.request.ActualizarMateriaInscritaRequest;
import com.saca.api.dto.request.CrearMateriaInscritaRequest;
import com.saca.api.dto.response.MateriaInscritaResponse;
import com.saca.api.service.MateriaInscritaService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/materias")
public class MateriaInscritaController {

	private final MateriaInscritaService materiaInscritaService;

	public MateriaInscritaController(MateriaInscritaService materiaInscritaService) {
		this.materiaInscritaService = materiaInscritaService;
	}

	@GetMapping
	public List<MateriaInscritaResponse> listar(
			@RequestParam Long estudianteId,
			@RequestParam(required = false) Long cicloAcademicoId) {
		if (cicloAcademicoId != null) {
			return materiaInscritaService.listarPorCiclo(cicloAcademicoId, estudianteId);
		}

		return materiaInscritaService.listarPorEstudiante(estudianteId);
	}

	@GetMapping("/{materiaId}")
	public MateriaInscritaResponse obtenerPorId(
			@PathVariable Long materiaId,
			@RequestParam Long estudianteId) {
		return materiaInscritaService.obtenerPorId(materiaId, estudianteId);
	}

	@PostMapping
	public ResponseEntity<MateriaInscritaResponse> crear(
			@RequestParam Long estudianteId,
			@Valid @RequestBody CrearMateriaInscritaRequest request) {
		MateriaInscritaResponse response = materiaInscritaService.crear(request, estudianteId);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PutMapping("/{materiaId}")
	public MateriaInscritaResponse actualizar(
			@PathVariable Long materiaId,
			@RequestParam Long estudianteId,
			@Valid @RequestBody ActualizarMateriaInscritaRequest request) {
		return materiaInscritaService.actualizar(materiaId, estudianteId, request);
	}

	@DeleteMapping("/{materiaId}")
	public ResponseEntity<Void> eliminar(
			@PathVariable Long materiaId,
			@RequestParam Long estudianteId) {
		materiaInscritaService.eliminar(materiaId, estudianteId);
		return ResponseEntity.noContent().build();
	}
}
