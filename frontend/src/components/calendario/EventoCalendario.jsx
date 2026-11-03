import Insignia from "../ui/Insignia";
import { unirClases } from "../ui/unirClases";
import { ESTILOS_EVENTO, TIPOS_ACTIVIDAD } from "./constantes";
import { formatearHoras, hora } from "./fechas";

const formatearPorcentaje = (valor) => {
  const numero = Number(valor ?? 0);
  return `${Number.isInteger(numero) ? numero : numero.toFixed(1)} %`;
};

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
        {!esClase && evento.tiempoEstimadoHoras != null && (
          <span className="text-ink-600">· {formatearHoras(evento.tiempoEstimadoHoras)}</span>
        )}
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

export default EventoCalendario;
