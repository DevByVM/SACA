package com.saca.api.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import java.time.LocalTime;

@Entity
@Table(name = "disponibilidades_semanales")
public class DisponibilidadSemanal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dia_semana", length = 15, nullable = false)
    private String diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(name = "tipo_bloque", length = 30, nullable = false)
    private String tipoBloque;

    @Column(name = "ciclo_id", nullable = false)
    private Long cicloId;

    @Column(name = "estudiante_id", nullable = false)
    private Long estudianteId;

    // Constructores
    public DisponibilidadSemanal() {}

    public DisponibilidadSemanal(String diaSemana, LocalTime horaInicio, LocalTime horaFin, String tipoBloque, Long cicloId, Long estudianteId) {
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.tipoBloque = tipoBloque;
        this.cicloId = cicloId;
        this.estudianteId = estudianteId;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDiaSemana() { return diaSemana; }
    public void setDiaSemana(String diaSemana) { this.diaSemana = diaSemana; }

    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }

    public LocalTime getHoraFin() { return horaFin; }
    public void setHoraFin(LocalTime horaFin) { this.horaFin = horaFin; }

    public String getTipoBloque() { return tipoBloque; }
    public void setTipoBloque(String tipoBloque) { this.tipoBloque = tipoBloque; }

    public Long getCicloId() { return cicloId; }
    public void setCicloId(Long cicloId) { this.cicloId = cicloId; }

    public Long getEstudianteId() { return estudianteId; }
    public void setEstudianteId(Long estudianteId) { this.estudianteId = estudianteId; }
}