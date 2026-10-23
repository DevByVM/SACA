import { unirClases } from "./unirClases";

// Usa h2 porque el h1 de la página lo pone App.jsx. El titulo es opcional: si el
// módulo coincide con el h1 se omite y solo quedan la descripción y las acciones.
function EncabezadoModulo({ titulo, descripcion, icono, acciones, className }) {
  return (
    <div
      className={unirClases(
        titulo
          ? "flex flex-col gap-3 md:flex-row md:items-end md:justify-between"
          : "flex flex-col gap-3 md:flex-row md:items-center md:justify-between",
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
          {titulo && <h2 className="text-2xl font-bold text-ink-900">{titulo}</h2>}
          {descripcion && <p className={unirClases(titulo && "mt-1", "text-sm text-ink-600")}>{descripcion}</p>}
        </div>
      </div>
      {acciones && <div className="flex flex-wrap items-center gap-2">{acciones}</div>}
    </div>
  );
}

export default EncabezadoModulo;
