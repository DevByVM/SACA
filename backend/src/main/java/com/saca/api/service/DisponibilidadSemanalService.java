package com.saca.api.service;


import com.saca.api.entity.DisponibilidadSemanal;
import com.saca.api.exception.ConflictoHorarioException;
import com.saca.api.exception.RecursoNoEncontradoException;
import com.saca.api.exception.ValidacionNegocioException;
import com.saca.api.repository.CicloAcademicoRepository;
import com.saca.api.repository.DisponibilidadSemanalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

// Todas las operaciones reciben el id del estudiante autenticado (sub del JWT) y solo
// actúan sobre sus propios bloques; un bloque o ciclo ajeno responde igual que uno inexistente.
 @Service
 public class DisponibilidadSemanalService {
    private static final int DURACION_MINIMA_MINUTOS = 30;

    @Autowired
    private DisponibilidadSemanalRepository repository;

    @Autowired
    private CicloAcademicoRepository cicloAcademicoRepository;


    // / 1. Agregar/Guardar bloque con validación para evitar duplicados
    public DisponibilidadSemanal guardarBloque(DisponibilidadSemanal bloque, Long estudianteId) {
        validarRangoHoras(bloque.getHoraInicio(), bloque.getHoraFin());
        validarEstudianteDelCuerpo(bloque.getEstudianteId(), estudianteId);
        validarCicloDelEstudiante(bloque.getCicloId(), estudianteId);

        // Un id en el cuerpo haría que save() sobrescribiera un bloque existente
        bloque.setId(null);
        bloque.setEstudianteId(estudianteId);
        validarSinConflicto(bloque, null);

        // Si no hay duplicados ni cruces, el horario está limpio y se puede guardar
        return repository.save(bloque);
    }

    // 2. Obtener horario por estudiante y ciclo
    public List<DisponibilidadSemanal> obtenerHorarioEstudiante(Long estudianteId, Long cicloId) {
        return repository.findByEstudianteIdAndCicloId(estudianteId, cicloId);
    }

    // 3. Eliminar bloque por ID
    public void eliminarBloque(Long id, Long estudianteId) {
        repository.delete(obtenerPorId(id, estudianteId));
    }

    // 4. Obtener los bloques del estudiante en todos sus ciclos
    public List<DisponibilidadSemanal> listarDelEstudiante(Long estudianteId) {
        return repository.findByEstudianteId(estudianteId);
    }

    // 5. Obtener un solo bloque por su ID (Lanza excepción si no existe o es de otro estudiante)
    public DisponibilidadSemanal obtenerPorId(Long id, Long estudianteId) {
        return repository.findByIdAndEstudianteId(id, estudianteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el bloque de disponibilidad solicitado"));
    }

    // 6. Actualizar un bloque existente (Mapeado con tus getters y setters reales)
    public DisponibilidadSemanal actualizarBloque(Long id, DisponibilidadSemanal detalles, Long estudianteId) {
        validarRangoHoras(detalles.getHoraInicio(), detalles.getHoraFin());

        // Buscamos el registro actual en la base de datos
        DisponibilidadSemanal existente = obtenerPorId(id, estudianteId);
        validarEstudianteDelCuerpo(detalles.getEstudianteId(), estudianteId);
        validarCicloDelEstudiante(detalles.getCicloId(), estudianteId);

        // El propietario no cambia: los conflictos se buscan siempre entre sus bloques
        detalles.setEstudianteId(estudianteId);
        validarSinConflicto(detalles, id);

        // Sincronizamos los datos con lo que viene del frontend/Request
        existente.setDiaSemana(detalles.getDiaSemana());
        existente.setHoraInicio(detalles.getHoraInicio());
        existente.setHoraFin(detalles.getHoraFin());
        existente.setTipoBloque(detalles.getTipoBloque());
        existente.setCicloId(detalles.getCicloId());

        // Guardamos los cambios en la base de datos
        return repository.save(existente);
    }

    // Misma regla que los horarios de clase: no se admiten bloques vacíos ni que crucen la medianoche
    private void validarRangoHoras(LocalTime horaInicio, LocalTime horaFin) {
        if (!horaInicio.isBefore(horaFin)) {
            throw new ValidacionNegocioException("La hora de inicio debe ser menor que la hora de fin");
        }
        if (Duration.between(horaInicio, horaFin).toMinutes() < DURACION_MINIMA_MINUTOS) {
            throw new ValidacionNegocioException("El bloque debe durar al menos " + DURACION_MINIMA_MINUTOS + " minutos");
        }
    }

    // El frontend envía su propio estudianteId; uno distinto se rechaza en lugar de ignorarse en silencio
    private void validarEstudianteDelCuerpo(Long estudianteIdDelCuerpo, Long estudianteId) {
        if (estudianteIdDelCuerpo != null && !estudianteIdDelCuerpo.equals(estudianteId)) {
            throw new ValidacionNegocioException("El estudiante indicado no corresponde a la sesión iniciada");
        }
    }

    private void validarCicloDelEstudiante(Long cicloId, Long estudianteId) {
        if (cicloId == null) {
            throw new ValidacionNegocioException("El ciclo académico es obligatorio");
        }
        if (!cicloAcademicoRepository.existsByIdAndEstudianteId(cicloId, estudianteId)) {
            throw new RecursoNoEncontradoException("No se encontró el ciclo académico solicitado");
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
