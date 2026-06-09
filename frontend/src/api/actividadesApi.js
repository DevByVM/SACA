import { apiRequest } from './client'

export function getActividadesByEstudianteIdAndCicloId(estudianteId, cicloId) {
  return apiRequest(`/actividades?estudianteId=${estudianteId}&cicloId=${cicloId}`)
}

export function getActividadesByEstudianteIdAndCicloActivo(estudianteId) {
  return apiRequest(`/actividades/estudiante/${estudianteId}/activo`)
}

export function crearActividad(payload) {
  console.log(JSON.stringify(payload));
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
export function actualizarActividad(actividadId, payload) {
    console.log(payload)
  return apiRequest(`/actividades/${actividadId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}