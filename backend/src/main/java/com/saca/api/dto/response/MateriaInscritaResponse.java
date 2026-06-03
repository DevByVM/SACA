package com.saca.api.dto.response;

public record MateriaInscritaResponse(
		Long id,
		Long cicloAcademicoId,
		String cicloAcademicoNombre,
		String nombre,
		String codigo,
		String grupoTeorico) {
}
