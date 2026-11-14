package com.saca.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "tipo_actividad", uniqueConstraints = @UniqueConstraint(
        name = "uk_tipo_actividad_nombre",
        columnNames = "nombre"))
public class TipoActividad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_actividad")
    private int idTipoActividad;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private boolean activo;

    public TipoActividad() {
    }

    public TipoActividad(int idTipoActividad, String nombre, boolean activo) {
        this.idTipoActividad = idTipoActividad;
        this.nombre = nombre;
        this.activo = activo;
    }

}
