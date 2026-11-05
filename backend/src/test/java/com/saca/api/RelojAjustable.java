package com.saca.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

// Reloj de pruebas que se puede fijar y adelantar, para comprobar vencimientos sin esperar
public final class RelojAjustable extends Clock {

    private final ZoneId zona;
    private volatile Instant instante;

    public RelojAjustable(Instant instante, ZoneId zona) {
        this.instante = instante;
        this.zona = zona;
    }

    public void fijar(Instant nuevoInstante) {
        this.instante = nuevoInstante;
    }

    public void avanzar(Duration duracion) {
        this.instante = instante.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId otraZona) {
        return new RelojAjustable(instante, otraZona);
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
