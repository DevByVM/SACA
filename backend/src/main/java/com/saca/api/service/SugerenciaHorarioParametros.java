package com.saca.api.service;

import java.time.LocalTime;

public final class SugerenciaHorarioParametros {

	public static final int VERSION = 1;
	public static final int DURACION_MAXIMA_SESION_MINUTOS = 120;
	public static final int DESCANSO_ENTRE_SESIONES_MINUTOS = 15;
	public static final int DURACION_MINIMA_BLOQUE_MINUTOS = 30;
	public static final int TOPE_DIARIO_MINUTOS = 360;
	public static final int GRANULARIDAD_MINUTOS = 30;
	public static final int DIAS_SEMANA = 7;
	public static final String ZONA_HORARIA = "America/El_Salvador";
	public static final LocalTime INICIO_VENTANA_PROVISIONAL = LocalTime.of(6, 0);
	public static final LocalTime FIN_VENTANA_PROVISIONAL = LocalTime.of(22, 0);

	private SugerenciaHorarioParametros() {
	}
}
