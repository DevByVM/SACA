import { apiRequest } from "./client";

export function listarTiposActividad() {
  return apiRequest("/tipos-actividad");
}
