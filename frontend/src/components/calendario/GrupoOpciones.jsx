import { useId } from "react";
import { unirClases } from "../ui/unirClases";

// Grupo de opciones segmentado y accesible (role="group", aria-pressed). Lo comparten los filtros de
// eventos y el filtro de franjas de tiempo del calendario.
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

export default GrupoOpciones;
