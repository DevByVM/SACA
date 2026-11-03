package com.saca.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

// Actualización parcial: Jackson solo llama al setter de los campos presentes en el JSON. Un campo
// omitido conserva su valor guardado y uno enviado como null lo borra.
public class ActualizarPerfilAcademicoRequest {

    private String carrera;
    private String facultad;
    private LocalDate fechaIngreso;
    private String planEstudio;

    // Depende del programa académico: la configura el estudiante y no tiene valor por defecto
    @DecimalMin(value = "0.0", message = "La nota mínima de aprobación no puede ser menor a 0")
    @DecimalMax(value = "10.0", message = "La nota mínima de aprobación no puede ser mayor a 10")
    @Digits(integer = 2, fraction = 2, message = "La nota mínima de aprobación admite como máximo dos decimales")
    private BigDecimal notaMinimaAprobacion;

    @JsonIgnore
    private boolean carreraIndicada;
    @JsonIgnore
    private boolean facultadIndicada;
    @JsonIgnore
    private boolean fechaIngresoIndicada;
    @JsonIgnore
    private boolean planEstudioIndicado;
    @JsonIgnore
    private boolean notaMinimaAprobacionIndicada;

    public String getCarrera() {
        return carrera;
    }

    public void setCarrera(String carrera) {
        this.carrera = carrera;
        this.carreraIndicada = true;
    }

    public String getFacultad() {
        return facultad;
    }

    public void setFacultad(String facultad) {
        this.facultad = facultad;
        this.facultadIndicada = true;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
        this.fechaIngresoIndicada = true;
    }

    public String getPlanEstudio() {
        return planEstudio;
    }

    public void setPlanEstudio(String planEstudio) {
        this.planEstudio = planEstudio;
        this.planEstudioIndicado = true;
    }

    public BigDecimal getNotaMinimaAprobacion() {
        return notaMinimaAprobacion;
    }

    public void setNotaMinimaAprobacion(BigDecimal notaMinimaAprobacion) {
        this.notaMinimaAprobacion = notaMinimaAprobacion;
        this.notaMinimaAprobacionIndicada = true;
    }

    @JsonIgnore
    public boolean isCarreraIndicada() {
        return carreraIndicada;
    }

    @JsonIgnore
    public boolean isFacultadIndicada() {
        return facultadIndicada;
    }

    @JsonIgnore
    public boolean isFechaIngresoIndicada() {
        return fechaIngresoIndicada;
    }

    @JsonIgnore
    public boolean isPlanEstudioIndicado() {
        return planEstudioIndicado;
    }

    @JsonIgnore
    public boolean isNotaMinimaAprobacionIndicada() {
        return notaMinimaAprobacionIndicada;
    }
}
