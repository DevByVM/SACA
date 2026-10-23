package com.saca.api.controller;


import com.saca.api.config.EstudianteAutenticado;
import com.saca.api.entity.JornadaLaboral;
import com.saca.api.service.jornadaLaboralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jornadas")
public class JornadaLaboralController {

    @Autowired
    private jornadaLaboralService service;

    // El propietario siempre es el estudiante del token, nunca el indicado por el cliente
    @PostMapping
    public ResponseEntity<JornadaLaboral> crear(@AuthenticationPrincipal Jwt jwt, @RequestBody JornadaLaboral jornada) {
        JornadaLaboral nuevaJornada = service.guardarJornada(jornada, EstudianteAutenticado.id(jwt));
        return new ResponseEntity<>(nuevaJornada, HttpStatus.CREATED);
    }

    @GetMapping("/estudiante/{estudianteId}")
    public ResponseEntity<List<JornadaLaboral>> listarPorEstudiante(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long estudianteId) {
        List<JornadaLaboral> lista = service.obtenerJornadasEstudiante(EstudianteAutenticado.idPropio(jwt, estudianteId));
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JornadaLaboral> obtenerPorId(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id, EstudianteAutenticado.id(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JornadaLaboral> actualizar(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody JornadaLaboral detalles) {
        JornadaLaboral actualizada = service.actualizarJornada(id, detalles, EstudianteAutenticado.id(jwt));
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.eliminarJornada(id, EstudianteAutenticado.id(jwt));
        return ResponseEntity.noContent().build();
    }
}