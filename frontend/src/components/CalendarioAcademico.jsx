import { useEffect, useState } from "react";
import { obtenerCalendarioSemana } from "../services/calendarioService";
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import EstadoVacio from "./ui/EstadoVacio";
import Insignia from "./ui/Insignia";
import { unirClases } from "./ui/unirClases";

const DIAS = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];

// Nombres legibles de los tipos que ya existen en el modelo (TipoActividadAcademica)
const TIPOS_ACTIVIDAD = {
  PARCIAL: "Parcial",
  LABORATORIO: "Laboratorio",
  PROYECTO: "Proyecto",
  INVESTIGACION: "Investigación",
};

const ESTILOS_EVENTO = {
  CLASE: "border-brand-700 bg-brand-50",
  ACTIVIDAD: "border-info-700 bg-info-50",
};

// Las fechas viajan como texto AAAA-MM-DD y se operan en UTC para que la zona horaria del navegador
// no cambie el día
function sumarDias(fechaTexto, dias) {
  const [anio, mes, dia] = fechaTexto.split("-").map(Number);
  return new Date(Date.UTC(anio, mes - 1, dia + dias)).toISOString().slice(0, 10);
}

function fechaLocalHoy() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-${String(hoy.getDate()).padStart(2, "0")}`;
}

const formateadorDiaMes = new Intl.DateTimeFormat("es-SV", { day: "numeric", month: "short", timeZone: "UTC" });
const formateadorAnio = new Intl.DateTimeFormat("es-SV", { year: "numeric", timeZone: "UTC" });

function formatearFecha(fechaTexto) {
  const [anio, mes, dia] = fechaTexto.split("-").map(Number);
  return formateadorDiaMes.format(new Date(Date.UTC(anio, mes - 1, dia)));
}

function formatearRango(inicio, fin) {
  const [anio, mes, dia] = fin.split("-").map(Number);
  return `${formatearFecha(inicio)} – ${formatearFecha(fin)} ${formateadorAnio.format(new Date(Date.UTC(anio, mes - 1, dia)))}`;
}

const hora = (valor) => (valor ? String(valor).slice(0, 5) : "");

const formatearPorcentaje = (valor) => {
  const numero = Number(valor ?? 0);
  return `${Number.isInteger(numero) ? numero : numero.toFixed(1)} %`;
};

// SDACAS-21: semana de lunes a domingo con las clases del ciclo activo y las actividades académicas.
// La navegación no tiene límite: una semana sin eventos se muestra vacía.
function CalendarioAcademico() {
  // null = semana actual según el servidor; si no, cualquier fecha AAAA-MM-DD de la semana a consultar
  const [fechaConsulta, setFechaConsulta] = useState(null);
  const [semana, setSemana] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError("");

    obtenerCalendarioSemana(fechaConsulta)
      .then((datos) => {
        if (vigente) setSemana(datos);
      })
      .catch((errorConsulta) => {
        if (vigente) setError(errorConsulta.response?.data?.message || "No se pudo cargar el calendario.");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    // Al cambiar de semana rápido, una respuesta lenta anterior no debe sobrescribir la actual
    return () => {
      vigente = false;
    };
  }, [fechaConsulta]);

  const hoy = fechaLocalHoy();
  const dias = semana
    ? DIAS.map((nombre, indice) => {
      const fecha = sumarDias(semana.inicio, indice);
      return { nombre, fecha, eventos: semana.eventos.filter((evento) => evento.fecha === fecha) };
    })
    : [];

  return (
    <section className="space-y-5">
      <EncabezadoModulo
        descripcion="Clases del ciclo activo y actividades académicas de cada semana, de lunes a domingo."
        icono="fa-regular fa-calendar"
      />

      <div className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <p className="text-lg font-bold text-ink-900" aria-live="polite">
          {semana ? formatearRango(semana.inicio, semana.fin) : "Cargando semana..."}
        </p>
        <div className="grid grid-cols-3 gap-2 sm:flex">
          <Boton
            variante="secundario"
            icono="fa-solid fa-chevron-left"
            disabled={!semana}
            onClick={() => setFechaConsulta(sumarDias(semana.inicio, -7))}
          >
            Semana anterior
          </Boton>
          <Boton variante="secundario" disabled={!semana} onClick={() => setFechaConsulta(null)}>
            Esta semana
          </Boton>
          <Boton
            variante="secundario"
            disabled={!semana}
            onClick={() => setFechaConsulta(sumarDias(semana.inicio, 7))}
          >
            Semana siguiente
            <i className="fa-solid fa-chevron-right" aria-hidden="true"></i>
          </Boton>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4 text-sm text-ink-600" aria-label="Leyenda del calendario">
        <span className="flex items-center gap-2">
          <span className={unirClases("h-4 w-4 rounded border-l-4", ESTILOS_EVENTO.CLASE)} aria-hidden="true"></span>
          Clase
        </span>
        <span className="flex items-center gap-2">
          <span className={unirClases("h-4 w-4 rounded border-l-4", ESTILOS_EVENTO.ACTIVIDAD)} aria-hidden="true"></span>
          Actividad académica
        </span>
        <span className="flex items-center gap-2">
          <span className={unirClases("h-4 w-4 rounded border-l-4 opacity-60", ESTILOS_EVENTO.ACTIVIDAD)} aria-hidden="true"></span>
          Completada o calificada
        </span>
      </div>

      {error && <Aviso tono="peligro">{error}</Aviso>}

      {semana?.actividadesSinFecha > 0 && (
        <Aviso tono="advertencia">
          {semana.actividadesSinFecha === 1
            ? "1 actividad no tiene fecha de entrega ni de inicio y no aparece en el calendario."
            : `${semana.actividadesSinFecha} actividades no tienen fecha de entrega ni de inicio y no aparecen en el calendario.`}
        </Aviso>
      )}

      {cargando && !semana ? (
        <EstadoVacio cargando mensaje="Cargando calendario..." />
      ) : semana && (
        <>
          {semana.eventos.length === 0 && (
            <EstadoVacio icono="fa-regular fa-calendar" mensaje="No hay clases ni actividades en esta semana." />
          )}

          <ol className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-7" aria-busy={cargando}>
            {dias.map((dia) => (
              <li
                key={dia.fecha}
                className={unirClases(
                  "flex min-w-0 flex-col rounded-xl border bg-surface p-3 shadow-sm",
                  dia.fecha === hoy ? "border-brand-700 ring-1 ring-brand-700" : "border-line"
                )}
              >
                <div className="mb-2 flex items-center justify-between gap-2">
                  <h2 className="text-sm font-bold text-ink-900">
                    {dia.nombre} <span className="font-normal text-ink-600">{formatearFecha(dia.fecha)}</span>
                  </h2>
                  {dia.fecha === hoy && <Insignia tono="marca">Hoy</Insignia>}
                </div>
                {dia.eventos.length === 0 ? (
                  <p className="text-xs text-ink-600">Sin eventos</p>
                ) : (
                  <ul className="space-y-2">
                    {dia.eventos.map((evento) => <EventoCalendario key={evento.id} evento={evento} />)}
                  </ul>
                )}
              </li>
            ))}
          </ol>
        </>
      )}
    </section>
  );
}

function EventoCalendario({ evento }) {
  const esClase = evento.tipo === "CLASE";
  const atenuada = !esClase && (evento.completada || evento.calificada);

  return (
    <li
      className={unirClases(
        "rounded-lg border-l-4 p-2 text-xs text-ink-900",
        ESTILOS_EVENTO[evento.tipo],
        atenuada && "opacity-60"
      )}
    >
      <div className="flex flex-wrap items-center gap-1">
        <Insignia tono={esClase ? "marca" : "info"}>{esClase ? "Clase" : TIPOS_ACTIVIDAD[evento.tipoActividad] ?? evento.tipoActividad}</Insignia>
        {!esClase && <span className="font-semibold">{formatearPorcentaje(evento.porcentajeEvaluacion)}</span>}
      </div>

      <p className="mt-1 font-mono">
        {esClase
          ? `${hora(evento.horaInicio)}–${hora(evento.horaFin)}`
          : `${evento.ubicadaPorFechaInicio ? "Inicio" : "Entrega"} ${hora(evento.horaInicio)}`}
      </p>
      <p className="break-words font-semibold">{evento.titulo}</p>
      <p className="break-words text-ink-600">
        {esClase ? `${evento.materiaCodigo} · ${evento.modalidad}` : `${evento.materiaCodigo} - ${evento.materiaNombre}`}
      </p>

      {esClase && evento.docenteTutor && <p className="break-words text-ink-600">{evento.docenteTutor}</p>}
      {esClase && evento.actividadVinculadaNombre && (
        <p className="break-words text-ink-600">Actividad: {evento.actividadVinculadaNombre}</p>
      )}

      {!esClase && (
        <div className="mt-1 flex flex-wrap gap-1">
          <Insignia tono={evento.completada ? "exito" : "neutro"}>{evento.estado}</Insignia>
          {evento.calificada && <Insignia tono="exito">Calificada: {evento.notaObtenida}</Insignia>}
        </div>
      )}
    </li>
  );
}

export default CalendarioAcademico;
