package com.saca.api.service;


import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.exception.ConflictoHorarioException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

 @Service
 public class DisponibilidadSemanalService {
    @Autowired
    private DisponibilidadSemanalRepository repository;


    // / 1. Agregar/Guardar bloque con validación para evitar duplicados
    public DisponibilidadSemanal guardarBloque(DisponibilidadSemanal bloque) {
        validarRangoHoras(bloque.getHoraInicio(), bloque.getHoraFin());
        validarSinConflicto(bloque, null);

        // Si no hay duplicados ni cruces, el horario está limpio y se puede guardar
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
        validarRangoHoras(detalles.getHoraInicio(), detalles.getHoraFin());

        // Buscamos el registro actual en la base de datos
        DisponibilidadSemanal existente = obtenerPorId(id);
        validarSinConflicto(detalles, id);

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

    // Misma regla que los horarios de clase: no se admiten bloques vacíos ni que crucen la medianoche
    private void validarRangoHoras(LocalTime horaInicio, LocalTime horaFin) {
        if (!horaInicio.isBefore(horaFin)) {
            throw new ValidacionNegocioException("La hora de inicio debe ser menor que la hora de fin");
        }
    }

    // Compara contra los bloques del mismo estudiante, ciclo y día. Al editar se excluye
    // el propio bloque (idExcluir) para que no choque consigo mismo. Igual que en los
    // horarios de clase, la comparación es estricta: 08:00-09:00 y 09:00-10:00 no se cruzan.
    private void validarSinConflicto(DisponibilidadSemanal bloque, Long idExcluir) {
        List<DisponibilidadSemanal> horarioExistente = obtenerHorarioEstudiante(
                bloque.getEstudianteId(),
                bloque.getCicloId()
        );

        for (DisponibilidadSemanal existente : horarioExistente) {
            if (Objects.equals(existente.getId(), idExcluir)
                    || !existente.getDiaSemana().equalsIgnoreCase(bloque.getDiaSemana())) {
                continue;
            }

            if (existente.getHoraInicio().equals(bloque.getHoraInicio())
                    && existente.getHoraFin().equals(bloque.getHoraFin())) {
                throw new ConflictoHorarioException(
                        "Ya existe un bloque de disponibilidad registrado el " + describirBloque(existente));
            }

            boolean seCruzan = bloque.getHoraInicio().isBefore(existente.getHoraFin())
                    && bloque.getHoraFin().isAfter(existente.getHoraInicio());

            if (seCruzan) {
                throw new ConflictoHorarioException(
                        "El bloque se cruza con otro registrado el " + describirBloque(existente));
            }
        }
    }

    private String describirBloque(DisponibilidadSemanal bloque) {
        return bloque.getDiaSemana() + " de " + bloque.getHoraInicio() + " a " + bloque.getHoraFin();
    }
}
