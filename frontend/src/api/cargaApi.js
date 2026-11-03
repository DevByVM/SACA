import { apiRequest } from './client'

// Carga de los próximos 7 días del estudiante del token (horas, exigencia evaluativa y clasificación)
export function obtenerCargaSemanal() {
  return apiRequest('/carga/semanal')
}

// SDACAS-26: semanas desde la actual hasta el fin del ciclo activo, cada una con su carga
export function obtenerCargaPorSemanas() {
  return apiRequest('/carga/semanas')
}

// SDACAS-32: tiempo de estudio disponible del periodo AAAA-MM-DD (máximo 42 días); informativo
export function obtenerTiempoDisponible(desde, hasta) {
  const parametros = new URLSearchParams({ desde, hasta })
  return apiRequest(`/carga/tiempo-disponible?${parametros}`)
}
