import { apiRequest } from './client'

// Propuesta semanal generada a partir de las actividades pendientes; no se guarda
export function obtenerHorarioSugerido() {
  return apiRequest('/estudiante/horario-sugerido')
}

// Horario confirmado de la semana que empieza en desde (AAAA-MM-DD); null si no hay
export function obtenerHorarioConfirmado(desde) {
  const parametros = new URLSearchParams({ desde })
  return apiRequest(`/estudiante/horario-estudio?${parametros}`)
}

// Crea o reemplaza el horario de la semana con los bloques editados
export function confirmarHorario(solicitud) {
  return apiRequest('/estudiante/horario-estudio', {
    method: 'PUT',
    body: JSON.stringify(solicitud),
  })
}
