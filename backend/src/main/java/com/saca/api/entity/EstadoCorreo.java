package com.saca.api.entity;

// Estado de la confirmación del correo institucional. Las cuentas anteriores a la confirmación
// no tienen estado (NULL) y conservan su acceso sin confirmación retroactiva.
public enum EstadoCorreo {
	PENDIENTE,
	CONFIRMADO
}
