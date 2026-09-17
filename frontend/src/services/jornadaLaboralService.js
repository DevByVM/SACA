const API_BASE_URL = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/jornadas`;

export const jornadaService = {
  // 1. POST /api/jornadas
  crear: async (jornada) => {
    const response = await fetch(API_BASE_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(jornada),
    });
    if (!response.ok) throw new Error('Error al crear la jornada');
    return response.json();
  },

  // 2. GET /api/jornadas/estudiante/{estudianteId}
  listarPorEstudiante: async (estudianteId) => {
    const response = await fetch(`${API_BASE_URL}/estudiante/${estudianteId}`);
    if (!response.ok) throw new Error('Error al obtener las jornadas');
    return response.json();
  },

  // 3. DELETE /api/jornadas/{id}
  eliminar: async (id) => {
    const response = await fetch(`${API_BASE_URL}/${id}`, {
      method: 'DELETE'
    });
    if (!response.ok) throw new Error('Error al eliminar la jornada');
    return true; // Retorna true si el backend respondió con un 204 No Content con éxito
  },

  // 4. PUT /api/jornadas/{id}
  actualizar: async (id, jornada) => {
    const response = await fetch(`${API_BASE_URL}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(jornada),
    });
    if (!response.ok) throw new Error('Error al actualizar la jornada');
    return response.json();
  }
};