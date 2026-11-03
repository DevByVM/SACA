import { clienteHttp } from "../api/client";

const API = `${
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"
}/calendario`;

export const obtenerCalendario =
  async (estudianteId) => {

    const response =
      await clienteHttp.get(
        `${API}/semanal/${estudianteId}`
      );

    return response.data;
  };

// SDACAS-21: semana (lunes a domingo) que contiene la fecha AAAA-MM-DD; sin fecha, la semana actual
export const obtenerCalendarioSemana = async (fecha) => {
  const response = await clienteHttp.get(`${API}/semana`, {
    params: fecha ? { fecha } : undefined,
  });
  return response.data;
};

// Periodo de hasta 42 días (cuadrícula mensual) con eventos y resumen diario
export const obtenerCalendarioPeriodo = async (desde, hasta) => {
  const response = await clienteHttp.get(`${API}/periodo`, { params: { desde, hasta } });
  return response.data;
};