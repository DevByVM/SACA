import { useId, useState } from "react";
import { unirClases } from "../ui/unirClases";
import { FILTROS_INICIALES, TIPOS_ACTIVIDAD } from "./constantes";

const OPCIONES_TIPO = [["CLASE", "Clase"], ...Object.entries(TIPOS_ACTIVIDAD)];
const OPCIONES_ESTADO = [["PENDIENTE", "Pendiente"], ["COMPLETADA", "Completada"], ["CALIFICADA", "Calificada"]];

// Un filtro aplicado es una opción desmarcada: restringe lo que se muestra
const contarAplicados = (filtros) =>
  [...Object.values(filtros.tipos), ...Object.values(filtros.estados)].filter((activo) => !activo).length;

function GrupoOpciones({ titulo, opciones, valores, onAlternar, columnas, ultimaAncha = false }) {
  const idTitulo = useId();
  return (
    <div className="min-w-0">
      <p id={idTitulo} className="mb-1 text-xs font-bold uppercase tracking-wide text-ink-600">{titulo}</p>
      <div
        role="group"
        aria-labelledby={idTitulo}
        className={unirClases("grid gap-1 rounded-lg border border-line bg-surface-muted p-1 lg:inline-flex lg:flex-wrap", columnas)}
      >
        {opciones.map(([clave, etiqueta]) => {
          const activo = valores[clave];
          return (
            <button
              key={clave}
              type="button"
              aria-pressed={activo}
              onClick={() => onAlternar(clave)}
              className={unirClases(
                "inline-flex min-h-11 items-center justify-center gap-1.5 rounded-md px-3 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 lg:min-h-8 lg:px-2.5",
                activo ? "bg-surface text-brand-700 shadow-sm ring-1 ring-brand-200" : "text-ink-600 hover:bg-surface hover:text-ink-900",
                // Con dos columnas en móvil, una última opción impar ocupa la fila completa para no dejar un hueco
                ultimaAncha && "max-sm:last:odd:col-span-2"
              )}
            >
              <i className={unirClases("text-xs", activo ? "fa-solid fa-check" : "fa-regular fa-circle opacity-60")} aria-hidden="true"></i>
              {etiqueta}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// En escritorio los filtros se muestran siempre en una tarjeta compacta; en tablet y móvil se pliegan
// tras un botón con el número de filtros aplicados para que el calendario quede arriba
function FiltrosCalendario({ filtros, onCambiar }) {
  const [abierto, setAbierto] = useState(false);
  const idPanel = useId();
  const aplicados = contarAplicados(filtros);
  const resumen = aplicados === 0 ? "Sin filtros" : aplicados === 1 ? "1 filtro aplicado" : `${aplicados} filtros aplicados`;

  const alternar = (grupo) => (clave) =>
    onCambiar({ ...filtros, [grupo]: { ...filtros[grupo], [clave]: !filtros[grupo][clave] } });

  return (
    <div className="rounded-xl border border-line bg-surface shadow-sm">
      <button
        type="button"
        className="flex min-h-11 w-full items-center justify-between gap-2 rounded-xl px-4 text-sm font-semibold text-ink-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 lg:hidden"
        aria-expanded={abierto}
        aria-controls={idPanel}
        onClick={() => setAbierto((actual) => !actual)}
      >
        <span className="flex items-center gap-2">
          <i className="fa-solid fa-filter text-ink-600" aria-hidden="true"></i>
          Filtros
          {aplicados > 0 && (
            <span className="rounded-full bg-brand-700 px-2 py-0.5 text-xs font-bold text-white">
              {aplicados}
              <span className="sr-only"> {aplicados === 1 ? "aplicado" : "aplicados"}</span>
            </span>
          )}
        </span>
        <i className={unirClases("fa-solid text-ink-600", abierto ? "fa-chevron-up" : "fa-chevron-down")} aria-hidden="true"></i>
      </button>

      <div id={idPanel} className={unirClases(abierto ? "block" : "hidden", "border-t border-line p-3 lg:block lg:border-t-0")}>
        <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end lg:gap-x-6">
          <GrupoOpciones
            titulo="Tipo de evento"
            opciones={OPCIONES_TIPO}
            valores={filtros.tipos}
            onAlternar={alternar("tipos")}
            columnas="grid-cols-2 sm:grid-cols-5"
            ultimaAncha
          />
          <GrupoOpciones
            titulo="Estado"
            opciones={OPCIONES_ESTADO}
            valores={filtros.estados}
            onAlternar={alternar("estados")}
            columnas="grid-cols-3"
          />
          <div className="flex items-center justify-between gap-3 lg:ml-auto lg:flex-col lg:items-end lg:gap-1">
            <span className="text-xs font-semibold text-ink-600" aria-live="polite">{resumen}</span>
            <button
              type="button"
              disabled={aplicados === 0}
              onClick={() => onCambiar(FILTROS_INICIALES)}
              className="inline-flex min-h-11 items-center gap-1.5 rounded-md px-2 text-sm font-semibold text-brand-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 disabled:cursor-not-allowed disabled:text-ink-500 disabled:no-underline lg:min-h-8"
            >
              <i className="fa-solid fa-xmark" aria-hidden="true"></i>
              Limpiar filtros
            </button>
          </div>
        </div>
        <p className="mt-2 text-xs text-ink-600">
          El estado filtra solo actividades. Las horas pendientes y la concentración no dependen de los filtros.
        </p>
      </div>
    </div>
  );
}

export default FiltrosCalendario;
