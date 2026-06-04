package com.saca.api.controllers;


import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.service.DisponibilidadSemanalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/disponibilidades")
@CrossOrigin(origins = "http://localhost:5173") // Permite que tu React aqua se conecte
public class DisponibilidadSemanalController {
//
    @Autowired
    private DisponibilidadSemanalService service;

    // GET: http://localhost:8080/api/disponibilidades/estudiante/1/ciclo/1
    @GetMapping("/estudiante/{estudianteId}/ciclo/{cicloId}")
    public ResponseEntity<List<DisponibilidadSemanal>> obtenerHorario(
            @PathVariable Long estudianteId,
            @PathVariable Long cicloId) {
        return ResponseEntity.ok(service.obtenerHorarioEstudiante(estudianteId, cicloId));
    }

    // POST: http://localhost:8080/api/disponibilidades
    @PostMapping
    public ResponseEntity<DisponibilidadSemanal> agregarBloque(@RequestBody DisponibilidadSemanal bloque) {
        return ResponseEntity.ok(service.guardarBloque(bloque));
    }

    // DELETE: http://localhost:8080/api/disponibilidades/1
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarBloque(@PathVariable Long id) {
        service.eliminarBloque(id);
        return ResponseEntity.noContent().build();
    }
}