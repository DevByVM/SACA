package com.saca.api.dto.request;

import java.time.LocalTime;

import com.saca.api.entity.DiaSemana;
import com.saca.api.entity.ModalidadClase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarHorarioClaseRequest(
		@NotNull(message = "El dia de la semana es obligatorio")
		DiaSemana diaSemana,

		@NotNull(message = "La hora de inicio es obligatoria")
		LocalTime horaInicio,

		@NotNull(message = "La hora de fin es obligatoria")
		LocalTime horaFin,

		@NotNull(message = "La modalidad es obligatoria")
		ModalidadClase modalidad,

		@Size(max = 120, message = "El docente o tutor no debe superar 120 caracteres")
		String docenteTutor) {
}
