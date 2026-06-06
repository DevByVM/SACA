package com.saca.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "actividades_academicas")
public class ActividadAcademica {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long idActividad;
    private String nombre;
    private LocalDate fechaEntrega;
    private double porcentajeEvaluacion;
    private double tiempoEstimadoHoras;
    private String estado;
    private LocalDate fechaCompletada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_actividad_id", nullable = false)
    private TipoActividad tipoActividad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_inscrita_id", nullable = false)
    private MateriaInscrita materiaInscrita;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_id")
    private Nota nota;

    public ActividadAcademica() {}

}
