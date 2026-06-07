package com.saca.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@Entity
@Table(name = "notas")
public class Nota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private double valorObtenido;

    private LocalDate fechaRegistro;

    public Nota() {}

    public Nota(int id, double valorObtenido, LocalDate fechaRegistro) {
        this.id = id;
        this.valorObtenido = valorObtenido;
        this.fechaRegistro = fechaRegistro;
    }

}
