import { apiRequest } from './client'

export function listarMaterias(estudianteId, cicloAcademicoId) {
  const params = new URLSearchParams({ estudianteId })
  if (cicloAcademicoId) {
    params.set('cicloAcademicoId', cicloAcademicoId)
  }

  return apiRequest(`/materias?${params.toString()}`)
}

export function crearMateria(estudianteId, payload) {
  return apiRequest(`/materias?estudianteId=${estudianteId}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarMateria(materiaId, estudianteId, payload) {
  return apiRequest(`/materias/${materiaId}?estudianteId=${estudianteId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarMateria(materiaId, estudianteId) {
  return apiRequest(`/materias/${materiaId}?estudianteId=${estudianteId}`, {
    method: 'DELETE',
  })
}
