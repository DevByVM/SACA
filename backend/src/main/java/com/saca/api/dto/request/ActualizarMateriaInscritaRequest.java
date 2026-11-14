package com.saca.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarMateriaInscritaRequest(
		@NotNull(message = "La materia del catálogo es obligatoria")
		Long catalogoMateriaId,

		@NotBlank(message = "El grupo teorico es obligatorio")
		@Size(max = 30, message = "El grupo teorico no debe superar 30 caracteres")
		String grupoTeorico) {
}
