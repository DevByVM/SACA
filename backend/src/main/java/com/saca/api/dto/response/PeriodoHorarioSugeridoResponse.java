package com.saca.api.dto.response;

import java.time.LocalDate;

public record PeriodoHorarioSugeridoResponse(
		LocalDate desde,
		LocalDate hasta) {
}
