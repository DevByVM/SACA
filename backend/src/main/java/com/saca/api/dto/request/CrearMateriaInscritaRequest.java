package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearMateriaInscritaRequest(
		@NotNull(message = "El ciclo academico es obligatorio")
		Long cicloAcademicoId,

		@NotBlank(message = "El nombre de la materia es obligatorio")
		@Size(max = 120, message = "El nombre de la materia no debe superar 120 caracteres")
		String nombre,

		@NotBlank(message = "El codigo de la materia es obligatorio")
		@Size(max = 30, message = "El codigo de la materia no debe superar 30 caracteres")
		String codigo,

		@NotBlank(message = "El grupo teorico es obligatorio")
		@Size(max = 30, message = "El grupo teorico no debe superar 30 caracteres")
		String grupoTeorico) {
}
