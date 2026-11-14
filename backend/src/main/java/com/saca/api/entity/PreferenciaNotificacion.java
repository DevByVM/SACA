package com.saca.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "preferencias_notificacion_tipos_catalogo",
            joinColumns = @JoinColumn(name = "preferencia_notificacion_id"),
            inverseJoinColumns = @JoinColumn(name = "tipo_actividad_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_preferencia_notificacion_tipo_catalogo",
                    columnNames = {"preferencia_notificacion_id", "tipo_actividad_id"}
            )
    )
    private Set<TipoActividad> tiposActividadHabilitados = new java.util.HashSet<>();

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

    public Set<TipoActividad> getTiposActividadHabilitados() {
        return tiposActividadHabilitados;
    }

    public void setTiposActividadHabilitados(Set<TipoActividad> tiposActividadHabilitados) {
        this.tiposActividadHabilitados = tiposActividadHabilitados == null
                ? new java.util.HashSet<>()
                : new java.util.HashSet<>(tiposActividadHabilitados);
    }
}
