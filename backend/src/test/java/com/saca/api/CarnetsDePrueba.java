package com.saca.api;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

// SDACAS-3: carnets con el formato oficial (dos letras y cinco números) para las pruebas que crean estudiantes
// con un carnet válido. El contador evita repetidos dentro de una misma ejecución de la suite.
public final class CarnetsDePrueba {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(ThreadLocalRandom.current().nextInt(100_000));

    private CarnetsDePrueba() {
    }

    public static String nuevo(String prefijo) {
        return prefijo + "%05d".formatted(Math.floorMod(SECUENCIA.getAndIncrement(), 100_000));
    }
}
