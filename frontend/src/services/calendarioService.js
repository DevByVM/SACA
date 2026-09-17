import axios from "axios";

const API = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/calendario`;

export const obtenerCalendario =
  async (estudianteId) => {

    const response =
      await axios.get(
        `${API}/semanal/${estudianteId}`
      );

    return response.data;
  };