import { apiRequest } from './client'

export function listarHorarios(estudianteId, filtros = {}) {
  const params = new URLSearchParams({ estudianteId })
  if (filtros.materiaInscritaId) {
    params.set('materiaInscritaId', filtros.materiaInscritaId)
  }
  if (filtros.cicloAcademicoId) {
    params.set('cicloAcademicoId', filtros.cicloAcademicoId)
  }

  return apiRequest(`/horarios?${params.toString()}`)
}

export function crearHorario(estudianteId, payload) {
  return apiRequest(`/horarios?estudianteId=${estudianteId}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function actualizarHorario(horarioId, estudianteId, payload) {
  return apiRequest(`/horarios/${horarioId}?estudianteId=${estudianteId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function eliminarHorario(horarioId, estudianteId) {
  return apiRequest(`/horarios/${horarioId}?estudianteId=${estudianteId}`, {
    method: 'DELETE',
  })
}
