package com.saca.api.controllers;


import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.service.DisponibilidadSemanalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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


    // Obtener todas las disponibilidades registradas en el sistema
    @GetMapping
    public ResponseEntity<List<DisponibilidadSemanal>> listarTodo() {
        List<DisponibilidadSemanal> lista = service.listarTodo();

        return ResponseEntity.ok(lista);
    }


    // Obtener un único bloque específico por su ID
    @GetMapping("/{id}")
    public ResponseEntity<DisponibilidadSemanal> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerPorId(id));
    }


    // Obtener el horario de un estudiante en un ciclo específico
    @GetMapping("/estudiante/{estudianteId}/ciclo/{cicloId}")
    public ResponseEntity<List<DisponibilidadSemanal>> obtenerHorario(
            @PathVariable Long estudianteId,
            @PathVariable Long cicloId) {
        return ResponseEntity.ok(service.obtenerHorarioEstudiante(estudianteId, cicloId));
    }


    //  Agregar un nuevo bloque de disponibilidad
    @PostMapping
    public ResponseEntity<DisponibilidadSemanal> agregarBloque(@RequestBody DisponibilidadSemanal bloque) {
        DisponibilidadSemanal nuevoBloque = service.guardarBloque(bloque);

        return ResponseEntity.status(HttpStatus.CREATED).body(nuevoBloque);
    }


    // Actualizar un bloque de disponibilidad existente por su ID
    @PutMapping("/{id}")
    public ResponseEntity<DisponibilidadSemanal> actualizarBloque(
            @PathVariable Long id,
            @RequestBody DisponibilidadSemanal bloqueDetalles) {
        DisponibilidadSemanal bloqueActualizado = service.actualizarBloque(id, bloqueDetalles);
        return ResponseEntity.ok(bloqueActualizado);
    }


    //  Eliminar un bloque por su ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarBloque(@PathVariable Long id) {
        service.eliminarBloque(id);
        return ResponseEntity.noContent().build();
    }
}