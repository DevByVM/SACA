package com.saca.api.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "preferencias_notificacion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_preferencia_notificacion_estudiante",
                columnNames = "estudiante_id"
        )
)
public class PreferenciaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia_notificacion")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false, unique = true)
    private Estudiante estudiante;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "preferencias_notificacion_tipo_actividad",
            joinColumns = @JoinColumn(name = "preferencia_notificacion_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_preferencia_notificacion_tipo",
                    columnNames = {"preferencia_notificacion_id", "tipo_actividad"}
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_actividad", nullable = false, length = 20)
    private Set<TipoActividadAcademica> tiposActividadHabilitados = new HashSet<>();

    public PreferenciaNotificacion() {
    }

    public Long getId() {
        return id;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public Set<TipoActividadAcademica> getTiposActividadHabilitados() {
        return tiposActividadHabilitados;
    }

    public void setTiposActividadHabilitados(Set<TipoActividadAcademica> tiposActividadHabilitados) {
        this.tiposActividadHabilitados = tiposActividadHabilitados == null
                ? new HashSet<>()
                : new HashSet<>(tiposActividadHabilitados);
    }
}
