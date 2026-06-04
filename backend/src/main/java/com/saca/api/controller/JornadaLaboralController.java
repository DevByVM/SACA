package com.saca.api.controller;


import com.saca.api.entity.JornadaLaboral;
import com.saca.api.service.jornadaLaboralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jornadas")
@CrossOrigin(origins = "*")
public class JornadaLaboralController {

    @Autowired
    private jornadaLaboralService service;

    @PostMapping
    public ResponseEntity<JornadaLaboral> crear(@RequestBody JornadaLaboral jornada) {
        JornadaLaboral nuevaJornada = service.guardarJornada(jornada);
        return new ResponseEntity<>(nuevaJornada, HttpStatus.CREATED);
    }

    @GetMapping("/estudiante/{estudianteId}")
    public ResponseEntity<List<JornadaLaboral>> listarPorEstudiante(@PathVariable Long estudianteId) {
        List<JornadaLaboral> lista = service.obtenerJornadasEstudiante(estudianteId);
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JornadaLaboral> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JornadaLaboral> actualizar(@PathVariable Long id, @RequestBody JornadaLaboral detalles) {
        JornadaLaboral actualizada = service.actualizarJornada(id, detalles);
        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminarJornada(id);
        return ResponseEntity.noContent().build();
    }
}