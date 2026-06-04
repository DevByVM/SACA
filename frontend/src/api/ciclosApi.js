import { apiRequest } from './client'

export function listarCiclos(estudianteId) {
  return apiRequest(`/ciclos?estudianteId=${estudianteId}`)
}

export function crearCiclo(payload) {
  return apiRequest('/ciclos', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarCiclo(cicloId, estudianteId, payload) {
  return apiRequest(`/ciclos/${cicloId}?estudianteId=${estudianteId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarCiclo(cicloId, estudianteId) {
  return apiRequest(`/ciclos/${cicloId}?estudianteId=${estudianteId}`, {
    method: 'DELETE',
  })
}
