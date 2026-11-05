// Nombres legibles de los tipos que ya existen en el modelo (TipoActividadAcademica)
export const TIPOS_ACTIVIDAD = {
  PARCIAL: "Parcial",
  LABORATORIO: "Laboratorio",
  PROYECTO: "Proyecto",
  INVESTIGACION: "Investigación",
};

export const ESTILOS_EVENTO = {
  CLASE: "border-brand-700 bg-brand-50",
  ACTIVIDAD: "border-info-700 bg-info-50",
};

export const FILTROS_INICIALES = {
  tipos: { CLASE: true, PARCIAL: true, LABORATORIO: true, PROYECTO: true, INVESTIGACION: true },
  estados: { PENDIENTE: true, COMPLETADA: true, CALIFICADA: true },
};

// El estado solo filtra actividades: una completada y calificada se muestra si cualquiera de sus dos
// estados está seleccionado
export function eventoVisible(evento, filtros) {
  const tipo = evento.tipo === "CLASE" ? "CLASE" : evento.tipoActividad;
  if (!filtros.tipos[tipo]) return false;
  if (evento.tipo === "CLASE") return true;
  return (filtros.estados.PENDIENTE && evento.pendiente)
    || (filtros.estados.COMPLETADA && evento.completada)
    || (filtros.estados.CALIFICADA && evento.calificada);
}

// Franjas de tiempo de la vista semanal. La jornada laboral y los bloques «No disponible» son
// tiempo restringido; «Tiempo de estudio» es tiempo reservado. Borde discontinuo y fondo propio para no
// confundirlas con clases (borde izquierdo de marca) ni con actividades (borde izquierdo azul).
export const TIPOS_FRANJA = {
  JORNADA_LABORAL: { etiqueta: "Jornada laboral", icono: "fa-solid fa-briefcase", estilo: "border-warning-700 bg-warning-50 text-warning-700" },
  NO_DISPONIBLE: { etiqueta: "No disponible", icono: "fa-solid fa-ban", estilo: "border-ink-500 bg-surface-muted text-ink-600" },
  ESTUDIO: { etiqueta: "Tiempo de estudio", icono: "fa-solid fa-book-open-reader", estilo: "border-success-700 bg-success-50 text-success-700" },
};

export const FRANJAS_INICIALES = { JORNADA_LABORAL: true, NO_DISPONIBLE: true, ESTUDIO: true };
