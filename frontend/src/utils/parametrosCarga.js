export function validarUmbrales(primero, segundo) {
  const elevada = Number(primero);
  const critica = Number(segundo);
  if (!Number.isFinite(elevada) || !Number.isFinite(critica) || elevada <= 0 || critica <= 0) {
    return "Ambos umbrales deben ser números positivos.";
  }
  return elevada < critica ? null : "El primer umbral debe ser menor que el segundo.";
}

// Conserva espacio por encima del umbral crítico; con los valores iniciales la escala es 40 h.
export function obtenerEscalaHoras(indicador) {
  const critica = Number(indicador?.umbralCritica);
  if (!Number.isFinite(critica) || critica <= 0) return null;
  const escala = critica + critica / 7;
  return Number.isFinite(escala) ? escala : critica;
}

export function porcentajeHoras(horas, indicador) {
  const escala = obtenerEscalaHoras(indicador);
  if (!escala) return 0;
  return Math.min(Math.max(Number(horas) / escala, 0) * 100, 100);
}

export function describirUmbrales(indicador) {
  if (!indicador) return "";
  return `Normal hasta ${indicador.umbralElevada} hrs, elevada con más de ${indicador.umbralElevada} y hasta ${indicador.umbralCritica} hrs, y crítica con más de ${indicador.umbralCritica} hrs.`;
}
