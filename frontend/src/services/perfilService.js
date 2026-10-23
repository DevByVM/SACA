import { clienteHttp } from "../api/client";

const API = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/auth`;

// El backend identifica al estudiante por el token; no se envía su correo ni su id
export const obtenerPerfil = async () => {
  const response = await clienteHttp.get(
    `${API}/perfil`
  );

  return response.data;
};

export const actualizarPerfil = async (id, datos) => {
  const response = await clienteHttp.put(
    `${API}/perfil`,
    datos
  );

  return response.data;
};