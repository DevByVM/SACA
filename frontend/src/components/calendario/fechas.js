// Las fechas del calendario viajan como texto AAAA-MM-DD y se operan en UTC para que la zona horaria
// del navegador no cambie el día. Los meses se representan como AAAA-MM.

export const DIAS_SEMANA = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
export const DIAS_CORTOS = ["L", "M", "X", "J", "V", "S", "D"];

function aUtc(fecha) {
  const [anio, mes, dia] = fecha.split("-").map(Number);
  return new Date(Date.UTC(anio, mes - 1, dia));
}

export function sumarDias(fecha, dias) {
  const [anio, mes, dia] = fecha.split("-").map(Number);
  return new Date(Date.UTC(anio, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

export function fechaLocalHoy() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-${String(hoy.getDate()).padStart(2, "0")}`;
}

// 0 = lunes … 6 = domingo
export const indiceDiaSemana = (fecha) => (aUtc(fecha).getUTCDay() + 6) % 7;

export const inicioSemana = (fecha) => sumarDias(fecha, -indiceDiaSemana(fecha));

export const diasDeLaSemana = (fecha) => {
  const lunes = inicioSemana(fecha);
  return DIAS_SEMANA.map((_, indice) => sumarDias(lunes, indice));
};

export const mesDe = (fecha) => fecha.slice(0, 7);

export const primerDiaMes = (mes) => `${mes}-01`;

export function sumarMeses(mes, meses) {
  const [anio, numero] = mes.split("-").map(Number);
  return new Date(Date.UTC(anio, numero - 1 + meses, 1)).toISOString().slice(0, 7);
}

// Cuadrícula de 6 semanas completas: del lunes anterior al día 1 hasta 41 días después
export function cuadriculaMes(mes) {
  const desde = inicioSemana(primerDiaMes(mes));
  return { desde, hasta: sumarDias(desde, 41) };
}

const formateadorDiaMes = new Intl.DateTimeFormat("es-SV", { day: "numeric", month: "short", timeZone: "UTC" });
const formateadorLargo = new Intl.DateTimeFormat("es-SV", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" });
const formateadorMes = new Intl.DateTimeFormat("es-SV", { month: "long", year: "numeric", timeZone: "UTC" });
const formateadorAnio = new Intl.DateTimeFormat("es-SV", { year: "numeric", timeZone: "UTC" });

const capitalizar = (texto) => texto.charAt(0).toUpperCase() + texto.slice(1);

export const formatearFecha = (fecha) => formateadorDiaMes.format(aUtc(fecha));

export const formatearFechaLarga = (fecha) => capitalizar(formateadorLargo.format(aUtc(fecha)));

export const formatearMes = (mes) => capitalizar(formateadorMes.format(aUtc(primerDiaMes(mes))));

export const formatearRango = (inicio, fin) =>
  `${formatearFecha(inicio)} – ${formatearFecha(fin)} ${formateadorAnio.format(aUtc(fin))}`;

export const hora = (valor) => (valor ? String(valor).slice(0, 5) : "");

export function formatearHoras(valor) {
  const numero = Number(valor ?? 0);
  return `${Number.isInteger(numero) ? numero : numero.toFixed(1)} h`;
}
