import { apiRequest } from './client'

export function getActividadesByEstudianteIdAndCicloId(estudianteId, cicloId) {
  return apiRequest(`/actividades?estudianteId=${estudianteId}&cicloId=${cicloId}`)
}

export function getActividadesByEstudianteIdAndCicloActivo(estudianteId) {
  return apiRequest(`/actividades/estudiante/${estudianteId}/activo`)
}

export function crearActividad(payload) {
  return apiRequest('/actividades', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function eliminarActividad(actividadId) {
  return apiRequest(`/actividades/${actividadId}`, {
    method: 'DELETE',
  })
}
// desvincularHorarios confirma que, si cambia la materia, sus horarios vinculados quedan sin actividad
export function actualizarActividad(actividadId, payload, { desvincularHorarios = false } = {}) {
  const consulta = desvincularHorarios ? '?desvincularHorarios=true' : ''
  return apiRequest(`/actividades/${actividadId}${consulta}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
