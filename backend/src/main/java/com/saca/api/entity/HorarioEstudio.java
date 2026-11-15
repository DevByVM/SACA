package com.saca.api.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

// Horario de estudio confirmado por el estudiante para una semana (lunes a domingo).
// La restricción única impide dos horarios del mismo estudiante para la misma semana.
@Entity
@Table(name = "horarios_estudio",
        uniqueConstraints = @UniqueConstraint(name = "uk_horario_estudio_semana", columnNames = { "estudiante_id", "semana_inicio" }))
public class HorarioEstudio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false, foreignKey = @ForeignKey(name = "fk_horario_estudio_estudiante"))
    private Estudiante estudiante;
    @Column(name = "semana_inicio", nullable = false)
    private LocalDate semanaInicio;
    @Column(name = "semana_fin", nullable = false)
    private LocalDate semanaFin;
    @Column(name = "confirmado_en", nullable = false)
    private LocalDateTime confirmadoEn;
    @OneToMany(mappedBy = "horario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC, horaInicio ASC")
    private List<BloqueHorarioEstudio> bloques = new ArrayList<>();

    protected HorarioEstudio() { }
    public HorarioEstudio(Estudiante estudiante, LocalDate semanaInicio, LocalDate semanaFin) {
        this.estudiante = estudiante;
        this.semanaInicio = semanaInicio;
        this.semanaFin = semanaFin;
    }
    public Long getId() { return id; }
    public LocalDate getSemanaInicio() { return semanaInicio; }
    public LocalDate getSemanaFin() { return semanaFin; }
    public LocalDateTime getConfirmadoEn() { return confirmadoEn; }
    public List<BloqueHorarioEstudio> getBloques() { return bloques; }

    // Sustituye todos los bloques: confirmar otra vez la misma semana reemplaza la versión anterior
    public void confirmar(List<BloqueHorarioEstudio> nuevosBloques, LocalDateTime ahora) {
        bloques.clear();
        nuevosBloques.forEach(bloque -> {
            bloque.asignarHorario(this);
            bloques.add(bloque);
        });
        confirmadoEn = ahora;
    }
}
