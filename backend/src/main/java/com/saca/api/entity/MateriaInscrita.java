package com.saca.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "materias_inscritas")
public class MateriaInscrita {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ciclo_academico_id", nullable = false)
	private CicloAcademico cicloAcademico;

	@Column(name = "nombre", nullable = false, length = 120)
	private String nombre;

	@Column(name = "codigo", nullable = false, length = 30)
	private String codigo;

	@Column(name = "grupo_teorico", nullable = false, length = 30)
	private String grupoTeorico;

	protected MateriaInscrita() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public CicloAcademico getCicloAcademico() {
		return cicloAcademico;
	}

	public void setCicloAcademico(CicloAcademico cicloAcademico) {
		this.cicloAcademico = cicloAcademico;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public String getGrupoTeorico() {
		return grupoTeorico;
	}

	public void setGrupoTeorico(String grupoTeorico) {
		this.grupoTeorico = grupoTeorico;
	}
}
