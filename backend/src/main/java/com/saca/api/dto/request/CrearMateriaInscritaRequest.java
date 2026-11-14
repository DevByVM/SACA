package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearMateriaInscritaRequest(
		@NotNull(message = "El ciclo academico es obligatorio")
		Long cicloAcademicoId,

		@NotNull(message = "La materia del catálogo es obligatoria")
		Long catalogoMateriaId,

		@NotBlank(message = "El grupo teorico es obligatorio")
		@jakarta.validation.constraints.Size(max = 30, message = "El grupo teorico no debe superar 30 caracteres")
		String grupoTeorico) {
}
