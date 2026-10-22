import { unirClases } from "./unirClases";

const TONOS = {
  info: "border-info-200 bg-info-50 text-info-700",
  exito: "border-success-200 bg-success-50 text-success-700",
  advertencia: "border-warning-200 bg-warning-50 text-warning-700",
  peligro: "border-danger-200 bg-danger-50 text-danger-700",
};

const ICONOS = {
  info: "fa-solid fa-circle-info",
  exito: "fa-solid fa-circle-check",
  advertencia: "fa-solid fa-triangle-exclamation",
  peligro: "fa-solid fa-circle-exclamation",
};

// role="alert" interrumpe al lector de pantalla, así que se reserva para
// advertencias y errores. Con icono={null} se oculta el icono.
function Aviso({ tono = "info", titulo, icono, className, children, ...props }) {
  const tonoValido = TONOS[tono] ? tono : "info";
  const iconoVisible = icono === undefined ? ICONOS[tonoValido] : icono;
  const esUrgente = tonoValido === "advertencia" || tonoValido === "peligro";

  return (
    <div
      {...props}
      role={esUrgente ? "alert" : undefined}
      className={unirClases(
        "flex items-start gap-2 rounded-lg border p-3 text-sm",
        TONOS[tonoValido],
        className
      )}
    >
      {iconoVisible && <i className={unirClases(iconoVisible, "mt-0.5 shrink-0")} aria-hidden="true"></i>}
      <div className="min-w-0">
        {titulo && <p className="font-semibold">{titulo}</p>}
        {children}
      </div>
    </div>
  );
}

export default Aviso;
