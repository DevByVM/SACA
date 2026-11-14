import { apiRequest } from "./client";

export const listarPlantillasAdmin = () => apiRequest("/admin/plantillas-planificacion");

export const guardarPlantillaAdmin = (plantilla, id) => apiRequest(
  id ? `/admin/plantillas-planificacion/${id}` : "/admin/plantillas-planificacion",
  {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(plantilla),
  },
);

export const obtenerPlantillaVigente = () => apiRequest("/plantillas-planificacion/vigente");
