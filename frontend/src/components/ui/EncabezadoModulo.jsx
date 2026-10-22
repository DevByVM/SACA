import { unirClases } from "./unirClases";

// Usa h2 porque el h1 de la página lo pone App.jsx.
function EncabezadoModulo({ titulo, descripcion, icono, acciones, className }) {
  return (
    <div
      className={unirClases(
        "flex flex-col gap-3 md:flex-row md:items-end md:justify-between",
        className
      )}
    >
      <div className="flex items-center gap-3">
        {icono && (
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
            <i className={icono} aria-hidden="true"></i>
          </div>
        )}
        <div>
          <h2 className="text-2xl font-bold text-ink-900">{titulo}</h2>
          {descripcion && <p className="mt-1 text-sm text-ink-600">{descripcion}</p>}
        </div>
      </div>
      {acciones && <div className="flex flex-wrap items-center gap-2">{acciones}</div>}
    </div>
  );
}

export default EncabezadoModulo;
