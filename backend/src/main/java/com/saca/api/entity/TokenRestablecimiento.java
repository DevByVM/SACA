package com.saca.api.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "tokens_restablecimiento", indexes = @Index(name = "idx_restablecimiento_estudiante", columnList = "estudiante_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_restablecimiento_hash", columnNames = "hash_token"))
public class TokenRestablecimiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false, foreignKey = @ForeignKey(name = "fk_restablecimiento_estudiante"))
    private Estudiante estudiante;
    @Column(name = "hash_token", nullable = false, length = 64)
    private String hashToken;
    @Column(nullable = false)
    private LocalDateTime expiraEn;
    private LocalDateTime usadoEn;
    private LocalDateTime invalidadoEn;

    protected TokenRestablecimiento() { }
    public TokenRestablecimiento(Estudiante estudiante, String hashToken, LocalDateTime expiraEn) {
        this.estudiante = estudiante;
        this.hashToken = hashToken;
        this.expiraEn = expiraEn;
    }
    public Long getId() { return id; }
    public Estudiante getEstudiante() { return estudiante; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public LocalDateTime getUsadoEn() { return usadoEn; }
    public LocalDateTime getInvalidadoEn() { return invalidadoEn; }
    public void usar(LocalDateTime ahora) { usadoEn = ahora; }
}
