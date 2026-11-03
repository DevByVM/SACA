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
