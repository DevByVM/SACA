package com.saca.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PerfilAcademicoResponse {

    private String carrera;
    private String facultad;
    private LocalDate fechaIngreso;
    private String planEstudio;
    private BigDecimal notaMinimaAprobacion;

    public PerfilAcademicoResponse() {}

    public String getCarrera() {
        return carrera;
    }

    public void setCarrera(String carrera) {
        this.carrera = carrera;
    }

    public String getFacultad() {
        return facultad;
    }

    public void setFacultad(String facultad) {
        this.facultad = facultad;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(LocalDate fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public String getPlanEstudio() {
        return planEstudio;
    }

    public void setPlanEstudio(String planEstudio) {
        this.planEstudio = planEstudio;
    }

    public BigDecimal getNotaMinimaAprobacion() {
        return notaMinimaAprobacion;
    }

    public void setNotaMinimaAprobacion(BigDecimal notaMinimaAprobacion) {
        this.notaMinimaAprobacion = notaMinimaAprobacion;
    }
}