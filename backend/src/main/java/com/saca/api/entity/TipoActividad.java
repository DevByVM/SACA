package com.saca.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name="tipo_actividad")
public class TipoActividad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idTipoActividad;

    private String nombre;
    private boolean activo;

    public TipoActividad() {
    }

    public TipoActividad(int idTipoActividad, String nombre, boolean activo) {
        this.idTipoActividad = idTipoActividad;
        this.nombre = nombre;
        this.activo = activo;
    }

}
