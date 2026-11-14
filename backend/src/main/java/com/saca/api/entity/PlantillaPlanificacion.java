package com.saca.api.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "plantillas_planificacion")
public class PlantillaPlanificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PerfilPlantillaPlanificacion perfil;

    @Column(nullable = false)
    private boolean activa;

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("diaSemana ASC, horaInicio ASC")
    private List<FranjaPlantillaPlanificacion> franjas = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public PerfilPlantillaPlanificacion getPerfil() {
        return perfil;
    }

    public void setPerfil(PerfilPlantillaPlanificacion perfil) {
        this.perfil = perfil;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public List<FranjaPlantillaPlanificacion> getFranjas() {
        return franjas;
    }

    public void reemplazarFranjas(List<FranjaPlantillaPlanificacion> nuevasFranjas) {
        franjas.clear();
        nuevasFranjas.forEach(franja -> {
            franja.setPlantilla(this);
            franjas.add(franja);
        });
    }
}
