package com.saca.api.dto.response;

public record ResumenHorarioSugeridoResponse(
		int minutosSugeridosSemanales,
		int minutosNoProgramadosSemanales,
		int materiasConsideradas) {
}
