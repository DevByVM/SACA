import { clienteHttp } from "../api/client";

const API_URL = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/perfil-academico`;

// El backend identifica al estudiante por el token; el id que reciben estas funciones ya no se envía
export const obtenerPerfilAcademico = async () => {
  const res = await clienteHttp.get(API_URL);
  return res.data;
};

export const actualizarPerfilAcademico = async (estudianteId, data) => {
  const res = await clienteHttp.put(API_URL, data);
  return res.data;
};