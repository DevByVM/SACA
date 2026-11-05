package com.saca.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.saca.api.entity.ModalidadClase;
import com.saca.api.entity.TipoActividadAcademica;
import com.saca.api.entity.TipoEventoCalendario;

// Un evento es una clase (horario recurrente ubicado en su fecha) o una actividad académica.
// Los campos que no aplican al tipo del evento van en null.
public record EventoCalendarioResponse(
		String id,
		TipoEventoCalendario tipo,
		LocalDate fecha,
		LocalTime horaInicio,
		LocalTime horaFin,
		String titulo,
		Long materiaInscritaId,
		String materiaCodigo,
		String materiaNombre,
		// Solo clases
		Long horarioId,
		ModalidadClase modalidad,
		String docenteTutor,
		Long actividadVinculadaId,
		String actividadVinculadaNombre,
		// Solo actividades
		Long actividadId,
		TipoActividadAcademica tipoActividad,
		BigDecimal porcentajeEvaluacion,
		String estado,
		Double notaObtenida,
		boolean completada,
		boolean calificada,
		// La actividad no tiene fecha de entrega y se ubica por su fecha de inicio
		boolean ubicadaPorFechaInicio,
		BigDecimal tiempoEstimadoHoras,
		// Misma definición que la carga semanal: ni completada ni calificada
		boolean pendiente) {
}
