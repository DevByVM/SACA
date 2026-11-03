import { useId, useState } from "react";
import { unirClases } from "../ui/unirClases";
import GrupoOpciones from "./GrupoOpciones";
import { TIPOS_FRANJA } from "./constantes";

const OPCIONES = Object.entries(TIPOS_FRANJA).map(([clave, tipo]) => [clave, tipo.etiqueta]);

// Filtro de franjas de la vista semanal, independiente de los filtros de clases y actividades. Igual que
// esos filtros, en escritorio se muestra siempre y en tablet y móvil se pliega tras un botón que indica
// cuántas franjas están ocultas
function FiltroFranjas({ valores, onCambiar, franjasDeLaSemana }) {
  const [abierto, setAbierto] = useState(false);
  const idPanel = useId();
  const ocultas = Object.values(valores).filter((visible) => !visible).length;

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
          <i className="fa-regular fa-clock text-ink-600" aria-hidden="true"></i>
          Franjas de tiempo
          {ocultas > 0 && (
            <span className="rounded-full bg-brand-700 px-2 py-0.5 text-xs font-bold text-white">
              {ocultas}
              <span className="sr-only"> {ocultas === 1 ? "oculta" : "ocultas"}</span>
            </span>
          )}
        </span>
        <i className={unirClases("fa-solid text-ink-600", abierto ? "fa-chevron-up" : "fa-chevron-down")} aria-hidden="true"></i>
      </button>

      <div id={idPanel} className={unirClases(abierto ? "block" : "hidden", "border-t border-line p-3 lg:block lg:border-t-0")}>
        <div className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between lg:gap-6">
          <GrupoOpciones
            titulo="Franjas de tiempo"
            opciones={OPCIONES}
            valores={valores}
            onAlternar={(clave) => onCambiar({ ...valores, [clave]: !valores[clave] })}
            columnas="grid-cols-1 sm:grid-cols-3"
          />
          <p className="text-xs text-ink-600 lg:max-w-md lg:text-right">
            Jornada laboral y bloques «No disponible» restringen tu tiempo de estudio; «Tiempo de estudio» es el
            tiempo que reservaste para estudiar.
          </p>
        </div>
      </div>

      {/* Fuera del panel plegable para que también se vea en móvil con el filtro cerrado */}
      {franjasDeLaSemana === 0 && (
        <p className="border-t border-line px-3 py-2 text-xs text-ink-600 lg:border-t-0 lg:pt-0" aria-live="polite">
          <i className="fa-regular fa-calendar mr-1" aria-hidden="true"></i>
          No tienes jornada laboral ni bloques «No disponible» o «Tiempo de estudio» del ciclo activo en esta semana.
          Puedes registrarlos en Horario Laboral y en Gestión de disponibilidad.
        </p>
      )}
    </div>
  );
}

export default FiltroFranjas;
