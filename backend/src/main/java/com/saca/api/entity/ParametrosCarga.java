package com.saca.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parametros_carga")
public class ParametrosCarga {

    // Clave de dominio: existe una configuración para todo SACA, sin estudiante ni semana.
    public static final String CLAVE_GLOBAL = "GLOBAL";

    @Id
    @Column(length = 20)
    private String clave = CLAVE_GLOBAL;

    @Column(name = "umbral_elevada", nullable = false)
    private double umbralElevada = 20;

    @Column(name = "umbral_critica", nullable = false)
    private double umbralCritica = 35;

    public ParametrosCarga() { }

    public double getUmbralElevada() { return umbralElevada; }
    public double getUmbralCritica() { return umbralCritica; }

    public void actualizar(double umbralElevada, double umbralCritica) {
        this.umbralElevada = umbralElevada;
        this.umbralCritica = umbralCritica;
    }
}
