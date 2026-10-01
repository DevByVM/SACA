import { apiRequest } from "./client";

export function obtenerPreferenciasNotificacion(estudianteId) {
  return apiRequest(`/notificaciones/preferencias/${estudianteId}`);
}

export function guardarPreferenciasNotificacion(estudianteId, tiposActividadHabilitados) {
  return apiRequest(`/notificaciones/preferencias/${estudianteId}`, {
    method: "PUT",
    body: JSON.stringify({ tiposActividadHabilitados }),
  });
}

export function obtenerNotificacionesProximas(estudianteId) {
  return apiRequest(`/notificaciones/estudiante/${estudianteId}/proximas`);
}
