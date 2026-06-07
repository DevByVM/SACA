package com.saca.api.dto;

import java.time.LocalTime;

public class CalendarioDTO {

    private String materia;
    private String codigo;
    private String diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String modalidad;
    private String docenteTutor;

    public CalendarioDTO() {}

    public CalendarioDTO(
            String materia,
            String codigo,
            String diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            String modalidad,
            String docenteTutor) {

        this.materia = materia;
        this.codigo = codigo;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.modalidad = modalidad;
        this.docenteTutor = docenteTutor;
    }

    public String getMateria() {
        return materia;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public String getModalidad() {
        return modalidad;
    }

    public String getDocenteTutor() {
        return docenteTutor;
    }
}