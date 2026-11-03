import { apiRequest } from './client'

// Sin cicloId el backend usa el ciclo activo del estudiante del token
export function obtenerRendimiento(cicloId) {
  const consulta = cicloId ? `?cicloId=${encodeURIComponent(cicloId)}` : ''
  return apiRequest(`/rendimiento${consulta}`)
}
