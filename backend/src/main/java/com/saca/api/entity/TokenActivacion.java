package com.saca.api.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Token de activación de una cuenta nueva. Solo se guarda el hash SHA-256 del token; el valor
// en claro únicamente existe en el enlace enviado por correo. Las filas usadas, vencidas o invalidadas se
// conservan para auditoría y para contar los envíos de cada cuenta.
//
// Cada token es un correo. Su envío pasa por: en cola (sin marcas), intentado (envio_intentado_en, guardado
// antes de llamar al SMTP), y enviado (enviado_en, el SMTP lo aceptó) o descartado (envio_descartado_en: no
// salió). Un intento sin resultado guardado (fallo de MySQL o reinicio tras llamar al SMTP) se trata como enviado.
@Entity
@Table(name = "tokens_activacion",
		uniqueConstraints = @UniqueConstraint(name = "uk_token_activacion_hash", columnNames = "hash_token"),
		indexes = @Index(name = "idx_token_activacion_estudiante_creado", columnList = "estudiante_id, creado_en"))
public class TokenActivacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "estudiante_id", nullable = false,
			foreignKey = @ForeignKey(name = "fk_token_activacion_estudiante"))
	private Estudiante estudiante;

	// SHA-256 en hexadecimal (64 caracteres)
	@Column(name = "hash_token", nullable = false, length = 64)
	private String hashToken;

	@Column(name = "creado_en", nullable = false)
	private LocalDateTime creadoEn;

	@Column(name = "expira_en", nullable = false)
	private LocalDateTime expiraEn;

	@Column(name = "usado_en")
	private LocalDateTime usadoEn;

	// Fecha en que una emisión posterior lo sustituyó
	@Column(name = "invalidado_en")
	private LocalDateTime invalidadoEn;

	@Column(name = "envio_intentado_en")
	private LocalDateTime envioIntentadoEn;

	@Column(name = "enviado_en")
	private LocalDateTime enviadoEn;

	@Column(name = "envio_descartado_en")
	private LocalDateTime envioDescartadoEn;

	protected TokenActivacion() {
	}

	public TokenActivacion(Estudiante estudiante, String hashToken, LocalDateTime creadoEn, LocalDateTime expiraEn) {
		this.estudiante = estudiante;
		this.hashToken = hashToken;
		this.creadoEn = creadoEn;
		this.expiraEn = expiraEn;
	}

	public Long getId() {
		return id;
	}

	public Estudiante getEstudiante() {
		return estudiante;
	}

	public String getHashToken() {
		return hashToken;
	}

	public LocalDateTime getCreadoEn() {
		return creadoEn;
	}

	public LocalDateTime getExpiraEn() {
		return expiraEn;
	}

	public LocalDateTime getUsadoEn() {
		return usadoEn;
	}

	public LocalDateTime getInvalidadoEn() {
		return invalidadoEn;
	}

	public LocalDateTime getEnvioIntentadoEn() {
		return envioIntentadoEn;
	}

	public LocalDateTime getEnviadoEn() {
		return enviadoEn;
	}

	public LocalDateTime getEnvioDescartadoEn() {
		return envioDescartadoEn;
	}
}
