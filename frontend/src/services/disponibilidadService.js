// Configuración de la URL base para tu API de Java
const API_BASE_URL = "http://localhost:8080/api";

export async function listarDisponibilidades(estudianteId) {
    const response = await fetch(`${API_BASE_URL}/disponibilidades`);
    if (!response.ok) throw new Error("Error al obtener las disponibilidades.");
    return await response.json();
}

export async function crearDisponibilidad(estudianteId, payload) {
    const response = await fetch(`${API_BASE_URL}/disponibilidades`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error("Error al crear la disponibilidad.");
    return await response.json();
}

export async function actualizarDisponibilidad(id, estudianteId, payload) {
    const response = await fetch(`${API_BASE_URL}/disponibilidades/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error("Error al actualizar la disponibilidad.");
    return await response.json();
}

export async function eliminarDisponibilidad(id, estudianteId) {
    const response = await fetch(`${API_BASE_URL}/disponibilidades/${id}`, {
        method: "DELETE",
    });
    if (!response.ok) throw new Error("Error al eliminar la disponibilidad.");
    return true;
}

// Consumo de Ciclos (necesario para el selector)
export async function listarCiclos(estudianteId) {
    const response = await fetch(`${API_BASE_URL}/ciclos?estudianteId=${estudianteId}`);
    if (!response.ok) throw new Error("Error al cargar los ciclos académicos.");
    return await response.json();
}