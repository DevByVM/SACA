package com.saca.api.entity;

public enum SituacionRendimiento {
	SIN_ACTIVIDADES,
	SIN_CALIFICACIONES,
	NOTA_MINIMA_NO_CONFIGURADA,
	APROBADA,
	RECUPERABLE,
	// Con lo registrado no alcanza, pero el peso que falta por registrar todavía podría permitirlo
	DEPENDE_DE_EVALUACIONES_SIN_REGISTRAR,
	IMPOSIBLE_DE_APROBAR,
	REPROBADA
}
