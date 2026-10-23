import { unirClases } from "./unirClases";

// nivelTitulo={2} para tarjetas que son secciones directas de un módulo sin h2 propio;
// por defecto sigue siendo h3.
function Tarjeta({ titulo, nivelTitulo = 3, icono, acciones, className, children, ...props }) {
  const tieneEncabezado = titulo || acciones;
  const EtiquetaTitulo = nivelTitulo === 2 ? "h2" : "h3";

  return (
    <div
      className={unirClases("rounded-xl border border-line bg-surface p-5 shadow-sm", className)}
      {...props}
    >
      {tieneEncabezado && (
        <div className="mb-4 flex items-center gap-2">
          {icono && <i className={unirClases(icono, "text-ink-600")} aria-hidden="true"></i>}
          {titulo && <EtiquetaTitulo className="text-lg font-semibold text-ink-900">{titulo}</EtiquetaTitulo>}
          {acciones && <div className="ml-auto flex items-center gap-2">{acciones}</div>}
        </div>
      )}
      {children}
    </div>
  );
}

export default Tarjeta;
