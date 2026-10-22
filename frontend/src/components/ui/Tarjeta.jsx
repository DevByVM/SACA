import { unirClases } from "./unirClases";

function Tarjeta({ titulo, icono, acciones, className, children, ...props }) {
  const tieneEncabezado = titulo || acciones;

  return (
    <div
      className={unirClases("rounded-xl border border-line bg-surface p-5 shadow-sm", className)}
      {...props}
    >
      {tieneEncabezado && (
        <div className="mb-4 flex items-center gap-2">
          {icono && <i className={unirClases(icono, "text-ink-600")} aria-hidden="true"></i>}
          {titulo && <h3 className="text-lg font-semibold text-ink-900">{titulo}</h3>}
          {acciones && <div className="ml-auto flex items-center gap-2">{acciones}</div>}
        </div>
      )}
      {children}
    </div>
  );
}

export default Tarjeta;
