package com.saca.api.proteccion;

import java.time.Duration;

// Se responde con HTTP 429 y Retry-After. El mensaje no depende de ninguna cuenta
public class LimiteSolicitudesExcedidoException extends RuntimeException {

    private final transient Duration reintentarEn;

    public LimiteSolicitudesExcedidoException(String mensaje, Duration reintentarEn) {
        super(mensaje);
        this.reintentarEn = reintentarEn;
    }

    // Segundos enteros, redondeados hacia arriba y al menos uno
    public long segundosParaReintentar() {
        long segundos = (reintentarEn.toMillis() + 999) / 1000;
        return Math.max(1, segundos);
    }
}
