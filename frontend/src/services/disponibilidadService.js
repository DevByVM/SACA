import { fetchConSesion } from "../api/client";

// Configuración de la URL base para tu API de Java
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export async function listarDisponibilidades(estudianteId) {
    const response = await fetchConSesion(`${API_BASE_URL}/disponibilidades`);
    if (!response.ok) throw new Error("Error al obtener las disponibilidades.");
    return await response.json();
}

export async function crearDisponibilidad(estudianteId, payload) {
    const response = await fetchConSesion(`${API_BASE_URL}/disponibilidades`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error(await obtenerMensajeError(response, "Error al crear la disponibilidad."));
    return await response.json();
}

export async function actualizarDisponibilidad(id, estudianteId, payload) {
    const response = await fetchConSesion(`${API_BASE_URL}/disponibilidades/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error(await obtenerMensajeError(response, "Error al actualizar la disponibilidad."));
    return await response.json();
}

export async function eliminarDisponibilidad(id, estudianteId) {
    const response = await fetchConSesion(`${API_BASE_URL}/disponibilidades/${id}`, {
        method: "DELETE",
    });
    if (!response.ok) throw new Error("Error al eliminar la disponibilidad.");
    return true;
}

// Consumo de Ciclos (necesario para el selector)
export async function listarCiclos(estudianteId) {
    const response = await fetchConSesion(`${API_BASE_URL}/ciclos?estudianteId=${estudianteId}`);
    if (!response.ok) throw new Error("Error al cargar los ciclos académicos.");  
    return await response.json();
}

// Los errores 4xx son de negocio (intervalo inválido, duplicado, cruce) y el backend
// explica el motivo en ErrorResponse.message; para el resto se mantiene el texto genérico.
async function obtenerMensajeError(response, mensajePorDefecto) {
    if (response.status < 400 || response.status >= 500) return mensajePorDefecto;

    try {
        const error = await response.json();
        return error?.message || mensajePorDefecto;
    } catch {
        return mensajePorDefecto;
    }
}