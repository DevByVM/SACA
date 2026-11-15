package com.saca.api.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.*;

// La materia se guarda por id y con su nombre copiado, sin clave foránea: así eliminar una materia
// inscrita no queda bloqueado y el horario ya confirmado se sigue pudiendo consultar e imprimir.
@Entity
@Table(name = "bloques_horario_estudio", indexes = @Index(name = "idx_bloque_horario_estudio_horario", columnList = "horario_id"))
public class BloqueHorarioEstudio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horario_id", nullable = false, foreignKey = @ForeignKey(name = "fk_bloque_horario_estudio_horario"))
    private HorarioEstudio horario;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;
    @Column(name = "materia_inscrita_id", nullable = false)
    private Long materiaInscritaId;
    @Column(name = "nombre_materia", nullable = false, length = 120)
    private String nombreMateria;

    protected BloqueHorarioEstudio() { }
    public BloqueHorarioEstudio(LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Long materiaInscritaId, String nombreMateria) {
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.materiaInscritaId = materiaInscritaId;
        this.nombreMateria = nombreMateria;
    }
    public Long getId() { return id; }
    public LocalDate getFecha() { return fecha; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFin() { return horaFin; }
    public Long getMateriaInscritaId() { return materiaInscritaId; }
    public String getNombreMateria() { return nombreMateria; }

    void asignarHorario(HorarioEstudio horario) { this.horario = horario; }
}
