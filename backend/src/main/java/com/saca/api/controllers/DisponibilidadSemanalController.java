package com.saca.api.controllers;


import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.service.DisponibilidadSemanalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/disponibilidades")
public class DisponibilidadSemanalController {
//
@Autowired
private DisponibilidadSemanalService service;


    // Obtener las disponibilidades del estudiante autenticado (antes devolvía las de todos)
    @GetMapping
    public ResponseEntity<List<DisponibilidadSemanal>> listarDelEstudiante(@AuthenticationPrincipal Jwt jwt) {
        List<DisponibilidadSemanal> lista = service.listarDelEstudiante(EstudianteAutenticado.id(jwt));

        return ResponseEntity.ok(lista);
    }


    // Obtener un único bloque específico por su ID
    @GetMapping("/{id}")
    public ResponseEntity<DisponibilidadSemanal> obtenerPorId(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id, EstudianteAutenticado.id(jwt)));
    }


    // Obtener el horario de un estudiante en un ciclo específico (solo el del propio token)
    @GetMapping("/estudiante/{estudianteId}/ciclo/{cicloId}")
    public ResponseEntity<List<DisponibilidadSemanal>> obtenerHorario(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long estudianteId,
            @PathVariable Long cicloId) {
        return ResponseEntity.ok(service.obtenerHorarioEstudiante(EstudianteAutenticado.idPropio(jwt, estudianteId), cicloId));
    }


    //  Agregar un nuevo bloque de disponibilidad
    @PostMapping
    public ResponseEntity<DisponibilidadSemanal> agregarBloque(
            @AuthenticationPrincipal Jwt jwt, @RequestBody DisponibilidadSemanal bloque) {
        DisponibilidadSemanal nuevoBloque = service.guardarBloque(bloque, EstudianteAutenticado.id(jwt));

        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoBloque);
    }


    // Actualizar un bloque de disponibilidad existente por su ID
    @PutMapping("/{id}")
    public ResponseEntity<DisponibilidadSemanal> actualizarBloque(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody DisponibilidadSemanal bloqueDetalles) {
        DisponibilidadSemanal bloqueActualizado = service.actualizarBloque(id, bloqueDetalles, EstudianteAutenticado.id(jwt));
        return ResponseEntity.ok(bloqueActualizado);
    }


    //  Eliminar un bloque por su ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarBloque(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.eliminarBloque(id, EstudianteAutenticado.id(jwt));
        return ResponseEntity.noContent().build();
    }
}