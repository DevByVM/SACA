import axios from "axios";

const API = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/auth`;

export const obtenerPerfil = async () => {
  const correo = localStorage.getItem("correoInstitucional");

  const response = await axios.get(
    `${API}/perfil/correo/${correo}`
  );

  return response.data;
};

export const actualizarPerfil = async (id, datos) => {
  const response = await axios.put(
    `${API}/perfil/${id}`,
    datos
  );

  return response.data;
};