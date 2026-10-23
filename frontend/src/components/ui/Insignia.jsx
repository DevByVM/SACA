import { unirClases } from "./unirClases";

const TONOS = {
  neutro: "border-line bg-surface-muted text-ink-600",
  marca: "border-brand-200 bg-brand-50 text-brand-700",
  info: "border-info-200 bg-info-50 text-info-700",
  exito: "border-success-200 bg-success-50 text-success-700",
  advertencia: "border-warning-200 bg-warning-50 text-warning-700",
  peligro: "border-danger-200 bg-danger-50 text-danger-700",
};

function Insignia({ tono = "neutro", icono, className, children, ...props }) {
  const clases = unirClases(
    "inline-flex items-center gap-1 whitespace-nowrap rounded-md border px-2 py-0.5 text-xs font-semibold",
    TONOS[tono] || TONOS.neutro,
    className
  );

  return (
    <span className={clases} {...props}>
      {icono && <i className={icono} aria-hidden="true"></i>}
      {children}
    </span>
  );
}

export default Insignia;
