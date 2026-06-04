package com.saca.api.dto.response;

import java.time.LocalTime;

import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.ModalidadClase;

public record HorarioClaseResponse(
		Long id,
		Long materiaInscritaId,
		String materiaNombre,
		Long cicloAcademicoId,
		DiaSemana diaSemana,
		LocalTime horaInicio,
		LocalTime horaFin,
		ModalidadClase modalidad,
		String docenteTutor) {
}
