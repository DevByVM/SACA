package com.saca.api.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "horarios_clase")
public class HorarioClase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "materia_inscrita_id", nullable = false)
	private MateriaInscrita materiaInscrita;

	@Enumerated(EnumType.STRING)
	@Column(name = "dia_semana", nullable = false, length = 20)
	private DiaSemana diaSemana;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	@Enumerated(EnumType.STRING)
	@Column(name = "modalidad", nullable = false, length = 20)
	private ModalidadClase modalidad;

	@Column(name = "docente_tutor", length = 120)
	private String docenteTutor;

	protected HorarioClase() {
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public MateriaInscrita getMateriaInscrita() {
		return materiaInscrita;
	}

	public void setMateriaInscrita(MateriaInscrita materiaInscrita) {
		this.materiaInscrita = materiaInscrita;
	}

	public DiaSemana getDiaSemana() {
		return diaSemana;
	}

	public void setDiaSemana(DiaSemana diaSemana) {
		this.diaSemana = diaSemana;
	}

	public LocalTime getHoraInicio() {
		return horaInicio;
	}

	public void setHoraInicio(LocalTime horaInicio) {
		this.horaInicio = horaInicio;
	}

	public LocalTime getHoraFin() {
		return horaFin;
	}

	public void setHoraFin(LocalTime horaFin) {
		this.horaFin = horaFin;
	}

	public ModalidadClase getModalidad() {
		return modalidad;
	}

	public void setModalidad(ModalidadClase modalidad) {
		this.modalidad = modalidad;
	}

	public String getDocenteTutor() {
		return docenteTutor;
	}

	public void setDocenteTutor(String docenteTutor) {
		this.docenteTutor = docenteTutor;
	}
}
