import { unirClases } from "./unirClases";

const VARIANTES = {
  primario: "bg-brand-700 text-white hover:bg-brand-800",
  secundario: "border border-ink-500 bg-surface text-ink-900 hover:bg-surface-muted",
  peligro: "border border-danger-200 bg-surface text-danger-700 hover:bg-danger-50",
  texto: "text-brand-700 hover:underline",
};

const TAMANOS = {
  sm: "text-xs",
  md: "text-sm",
};

const RELLENOS = {
  sm: "px-3 py-1",
  md: "px-4 py-2",
};

// type="button" por defecto: un botón sin type dentro de un <form> lo envía.
// Los botones de envío deben indicar type="submit" de forma explícita.
function Boton({
  variante = "primario",
  tamano = "md",
  icono,
  type = "button",
  className,
  children,
  ...props
}) {
  const clases = unirClases(
    "inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2",
    "disabled:cursor-not-allowed disabled:opacity-60",
    VARIANTES[variante] || VARIANTES.primario,
    TAMANOS[tamano] || TAMANOS.md,
    variante !== "texto" && (RELLENOS[tamano] || RELLENOS.md),
    className
  );

  return (
    <button type={type} className={clases} {...props}>
      {icono && <i className={icono} aria-hidden="true"></i>}
      {children}
    </button>
  );
}

export default Boton;
