package com.saca.api.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "estudiantes")
public class Estudiante {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "carnet", length = 20, unique = true)
	private String carnet;

	@Column(name = "nombre", length = 120)
	private String nombre;

	@Column(name = "correo_institucional", length = 120, unique = true)
	private String correoInstitucional;

	@Column(name = "contrasenia", length = 255, nullable = false)
	private String contrasenia;

	@Column(name = "cuenta_activa")
	private Boolean cuentaActiva;

	@Column(name = "intentos_fallidos")
	private Integer intentosFallidos;

	@Column(name = "bloqueado_hasta")
	private Timestamp bloqueadoHasta;

	public Estudiante() {
	}

	public Estudiante(Long id) {
		this.id = id;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCarnet() {
		return carnet;
	}

	public void setCarnet(String carnet) {
		this.carnet = carnet;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getCorreoInstitucional() {
		return correoInstitucional;
	}

	public void setCorreoInstitucional(String correoInstitucional) {
		this.correoInstitucional = correoInstitucional;
	}

	public String getContrasenia() {
		return contrasenia;
	}

	public void setContrasenia(String contrasenia) {
		this.contrasenia = contrasenia;
	}

	public Boolean getCuentaActiva() {
		return cuentaActiva;
	}

	public void setCuentaActiva(Boolean cuentaActiva) {
		this.cuentaActiva = cuentaActiva;
	}

	public Integer getIntentosFallidos() {
		return intentosFallidos;
	}

	public void setIntentosFallidos(Integer intentosFallidos) {
		this.intentosFallidos = intentosFallidos;
	}

	public Timestamp getBloqueadoHasta() {
		return bloqueadoHasta;
	}

	public void setBloqueadoHasta(Timestamp bloqueadoHasta) {
		this.bloqueadoHasta = bloqueadoHasta;
	}
}
