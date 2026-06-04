package com.saca.api.service;


import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

 @Service
 public class DisponibilidadSemanalService {
    @Autowired
    private DisponibilidadSemanalRepository repository;

    // 1. Agregar/Guardar bloque
    public DisponibilidadSemanal guardarBloque(DisponibilidadSemanal bloque) {
        return repository.save(bloque);
    }

    // 2. Obtener horario por estudiante y ciclo
    public List<DisponibilidadSemanal> obtenerHorarioEstudiante(Long estudianteId, Long cicloId) {
        return repository.findByEstudianteIdAndCicloId(estudianteId, cicloId);
    }

    // 3. Eliminar bloque por ID
    public void eliminarBloque(Long id) {
        repository.deleteById(id);
    }

    // 4. Obtener TODOS los bloques del sistema
    public List<DisponibilidadSemanal> listarTodo() {
        return repository.findAll();
    }

    // 5. Obtener un solo bloque por su ID (Lanza excepción si no existe)
    public DisponibilidadSemanal obtenerPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bloque de disponibilidad no encontrado con ID: " + id));
    }

    // 6. Actualizar un bloque existente (Mapeado con tus getters y setters reales)
    public DisponibilidadSemanal actualizarBloque(Long id, DisponibilidadSemanal detalles) {
        // Buscamos el registro actual en la base de datos
        DisponibilidadSemanal existente = obtenerPorId(id);

        // Sincronizamos los datos con lo que viene del frontend/Request
        existente.setDiaSemana(detalles.getDiaSemana());
        existente.setHoraInicio(detalles.getHoraInicio());
        existente.setHoraFin(detalles.getHoraFin());
        existente.setTipoBloque(detalles.getTipoBloque());
        existente.setCicloId(detalles.getCicloId());
        existente.setEstudianteId(detalles.getEstudianteId());

        // Guardamos los cambios en la base de datos
        return repository.save(existente);
    }
}
