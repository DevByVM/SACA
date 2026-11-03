package com.saca.api.dto.response;

import java.math.BigDecimal;

import com.saca.api.entity.NivelCarga;

public record IndicadorHorasResponse(
		BigDecimal horasPendientes,
		NivelCarga nivel,
		BigDecimal umbralElevada,
		BigDecimal umbralCritica) {
}
