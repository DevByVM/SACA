import { useEffect, useId } from "react";
import { unirClases } from "./unirClases";

const ANCHOS = {
  md: "max-w-md",
  lg: "max-w-lg",
  "2xl": "max-w-2xl",
};

const TONOS_ICONO = {
  marca: "bg-brand-50 text-brand-700",
  peligro: "bg-danger-50 text-danger-700",
};

// El modal ya escucha Escape mientras está abierto: al migrar un módulo hay que
// quitar su propio useEffect de Escape para no cerrar dos veces.
// El fondo cierra con mousedown (no click) para que una selección de texto que
// empieza dentro del diálogo y termina fuera no lo cierre.
// !m-0 en el fondo: dentro de un contenedor space-y-* el margen superior que
// reciben los hermanos desplazaría el fondo fijo y dejaría una franja sin cubrir.
function Modal({
  abierto,
  titulo,
  descripcion,
  icono,
  tonoIcono = "marca",
  onCerrar,
  ancho = "2xl",
  rol = "dialog",
  mostrarCerrar = true,
  etiquetaCerrar = "Cerrar",
  cerrarConEscape = true,
  cerrarAlPulsarFondo = true,
  className,
  children,
}) {
  const idTitulo = useId();
  const idDescripcion = useId();

  useEffect(() => {
    if (!abierto || !cerrarConEscape) return undefined;

    function cerrarConTecla(event) {
      if (event.key === "Escape") onCerrar?.();
    }

    window.addEventListener("keydown", cerrarConTecla);
    return () => window.removeEventListener("keydown", cerrarConTecla);
  }, [abierto, cerrarConEscape, onCerrar]);

  if (!abierto) return null;

  return (
    <div
      className="fixed inset-0 z-50 !m-0 flex items-center justify-center bg-black/50 p-4"
      onMouseDown={(event) => {
        if (cerrarAlPulsarFondo && event.target === event.currentTarget) onCerrar?.();
      }}
    >
      <div
        role={rol}
        aria-modal="true"
        aria-labelledby={titulo ? idTitulo : undefined}
        aria-describedby={descripcion ? idDescripcion : undefined}
        className={unirClases(
          "max-h-[90vh] w-full overflow-y-auto rounded-xl bg-surface p-5 shadow-xl sm:p-7",
          ANCHOS[ancho] || ANCHOS["2xl"],
          className
        )}
      >
        {(titulo || mostrarCerrar) && (
          <div className="mb-5 flex items-start justify-between gap-4">
            <div className="flex items-start gap-3">
              {icono && (
                <div
                  className={unirClases(
                    "flex h-10 w-10 shrink-0 items-center justify-center rounded-lg",
                    TONOS_ICONO[tonoIcono] || TONOS_ICONO.marca
                  )}
                >
                  <i className={icono} aria-hidden="true"></i>
                </div>
              )}
              <div>
                {titulo && <h3 id={idTitulo} className="text-xl font-bold text-ink-900">{titulo}</h3>}
                {descripcion && <p id={idDescripcion} className="mt-1 text-sm text-ink-600">{descripcion}</p>}
              </div>
            </div>
            {mostrarCerrar && (
              <button
                aria-label={etiquetaCerrar}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-ink-600 transition hover:bg-surface-muted hover:text-ink-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2"
                type="button"
                onClick={onCerrar}
              >
                <i className="fa-solid fa-xmark" aria-hidden="true"></i>
              </button>
            )}
          </div>
        )}
        {children}
      </div>
    </div>
  );
}

export default Modal;
