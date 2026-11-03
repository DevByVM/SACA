import Insignia from "../ui/Insignia";
import { unirClases } from "../ui/unirClases";
import EventoCalendario from "./EventoCalendario";
import { DIAS_SEMANA, diasDeLaSemana, formatearFecha } from "./fechas";

// Cuadrícula de lunes a domingo de la semana que contiene la fecha seleccionada.
// Pulsar el encabezado de un día lo selecciona para la agenda.
function VistaSemanal({ seleccionada, hoy, eventosPorFecha, onSeleccionar }) {
  const dias = diasDeLaSemana(seleccionada);

  return (
    <ol className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-7">
      {dias.map((fecha, indice) => {
        const eventos = eventosPorFecha.get(fecha) ?? [];
        return (
          <li
            key={fecha}
            className={unirClases(
              "flex min-w-0 flex-col rounded-xl border bg-surface p-3 shadow-sm",
              fecha === hoy ? "border-brand-700 ring-1 ring-brand-700" : "border-line"
            )}
          >
            <div className="mb-2 flex items-center justify-between gap-2">
              <h2 className="text-sm font-bold text-ink-900">
                <button
                  type="button"
                  className={unirClases(
                    "rounded text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700",
                    fecha === seleccionada && "underline decoration-2 underline-offset-4"
                  )}
                  aria-pressed={fecha === seleccionada}
                  onClick={() => onSeleccionar(fecha)}
                >
                  {DIAS_SEMANA[indice]} <span className="font-normal text-ink-600">{formatearFecha(fecha)}</span>
                </button>
              </h2>
              {fecha === hoy && <Insignia tono="marca">Hoy</Insignia>}
            </div>
            {eventos.length === 0 ? (
              <p className="text-xs text-ink-600">Sin eventos</p>
            ) : (
              <ul className="space-y-2">
                {eventos.map((evento) => <EventoCalendario key={evento.id} evento={evento} />)}
              </ul>
            )}
          </li>
        );
      })}
    </ol>
  );
}

export default VistaSemanal;
