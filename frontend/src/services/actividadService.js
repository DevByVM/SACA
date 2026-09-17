// src/services/actividadService.js
// Módulo de red para el recurso ActividadAcademica.
// Centraliza todas las llamadas al backend para que los componentes no manejen fetch directamente.

const BASE_URL = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/actividades`;

/**
 * Obtiene todas las actividades académicas del backend.
 * @returns {Promise<Array>} Lista de actividades
 */
export async function getActividades() {
  const respuesta = await fetch(BASE_URL);
  if (!respuesta.ok) {
    throw new Error(`Error al obtener actividades: ${respuesta.status}`);
  }
  return respuesta.json();
}

/**
 * Obtiene una actividad por su ID.
 * @param {number} id
 * @returns {Promise<Object>} Actividad encontrada
 */
export async function getActividadPorId(id) {
  const respuesta = await fetch(`${BASE_URL}/${id}`);
  if (!respuesta.ok) {
    throw new Error(`Actividad con ID ${id} no encontrada: ${respuesta.status}`);
  }
  return respuesta.json();
}

/**
 * Registra una nueva actividad académica.
 * @param {Object} actividad - Datos de la actividad a registrar
 * @returns {Promise<Object>} Actividad creada con su ID asignado
 */
export async function crearActividad(actividad) {
  const respuesta = await fetch(BASE_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(actividad),
  });

  if (!respuesta.ok) {
    // Intentar leer el mensaje de error del backend
    const mensajeError = await respuesta.text();
    throw new Error(mensajeError || `Error al crear actividad: ${respuesta.status}`);
  }

  return respuesta.json();
}

/**
 * Actualiza una actividad existente.
 * @param {number} id - ID de la actividad a actualizar
 * @param {Object} actividad - Nuevos datos de la actividad
 * @returns {Promise<Object>} Actividad actualizada
 */
export async function actualizarActividad(id, actividad) {
  const respuesta = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(actividad),
  });

  if (!respuesta.ok) {
    const mensajeError = await respuesta.text();
    throw new Error(mensajeError || `Error al actualizar actividad: ${respuesta.status}`);
  }

  return respuesta.json();
  
}

/**
 * Obtiene las actividades académicas del ciclo activo de un estudiante específico.
 * Conectado con /api/actividades/estudiante/{id}/activo
 * @param {number} estudianteId - ID del estudiante logueado
 * @returns {Promise<Array>} Lista de actividades del ciclo activo
 */
export async function obtenerPorCicloActivoYEstdiante(estudianteId) {

  const respuesta = await fetch(`${BASE_URL}/estudiante/${estudianteId}/activo`);
  
  if (!respuesta.ok) {
    throw new Error(`Error al obtener actividades del ciclo activo: ${respuesta.status}`);
  }
  return respuesta.json();
}
