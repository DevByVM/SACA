import axios from "axios";

const API_URL = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/perfil-academico`;

export const obtenerPerfilAcademico = async (estudianteId) => {
  const res = await axios.get(`${API_URL}/${estudianteId}`);
  return res.data;
};

export const actualizarPerfilAcademico = async (estudianteId, data) => {
  const res = await axios.put(`${API_URL}/${estudianteId}`, data);
  return res.data;
};