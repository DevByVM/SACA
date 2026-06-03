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

import com.saca.api.dto.request.ActualizarHorarioClaseRequest;
import com.saca.api.dto.request.CrearHorarioClaseRequest;
import com.saca.api.dto.response.HorarioClaseResponse;
import com.saca.api.service.HorarioClaseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/horarios")
public class HorarioClaseController {

	private final HorarioClaseService horarioClaseService;

	public HorarioClaseController(HorarioClaseService horarioClaseService) {
		this.horarioClaseService = horarioClaseService;
	}

	@GetMapping
	public List<HorarioClaseResponse> listar(
			@RequestParam Long estudianteId,
			@RequestParam(required = false) Long materiaInscritaId,
			@RequestParam(required = false) Long cicloAcademicoId) {
		if (materiaInscritaId != null) {
			return horarioClaseService.listarPorMateria(materiaInscritaId, estudianteId);
		}

		if (cicloAcademicoId != null) {
			return horarioClaseService.listarPorCiclo(cicloAcademicoId, estudianteId);
		}

		return horarioClaseService.listarPorEstudiante(estudianteId);
	}

	@GetMapping("/{horarioId}")
	public HorarioClaseResponse obtenerPorId(
			@PathVariable Long horarioId,
			@RequestParam Long estudianteId) {
		return horarioClaseService.obtenerPorId(horarioId, estudianteId);
	}

	@PostMapping
	public ResponseEntity<HorarioClaseResponse> crear(
			@RequestParam Long estudianteId,
			@Valid @RequestBody CrearHorarioClaseRequest request) {
		HorarioClaseResponse response = horarioClaseService.crear(request, estudianteId);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PutMapping("/{horarioId}")
	public HorarioClaseResponse actualizar(
			@PathVariable Long horarioId,
			@RequestParam Long estudianteId,
			@Valid @RequestBody ActualizarHorarioClaseRequest request) {
		return horarioClaseService.actualizar(horarioId, estudianteId, request);
	}

	@DeleteMapping("/{horarioId}")
	public ResponseEntity<Void> eliminar(
			@PathVariable Long horarioId,
			@RequestParam Long estudianteId) {
		horarioClaseService.eliminar(horarioId, estudianteId);
		return ResponseEntity.noContent().build();
	}
}
