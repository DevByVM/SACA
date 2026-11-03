import { apiRequest } from './client'

// Carga de los próximos 7 días del estudiante del token (horas, exigencia evaluativa y clasificación)
export function obtenerCargaSemanal() {
  return apiRequest('/carga/semanal')
}
