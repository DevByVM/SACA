package com.saca.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record EstadoCuentaRequest(@NotNull Boolean habilitada) {
}
