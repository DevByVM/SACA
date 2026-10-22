import { unirClases } from "./unirClases";

// Solo la carga se anuncia con aria-live; el estado vacío es contenido
// estático y anunciarlo también sería redundante.
function EstadoVacio({
  cargando = false,
  mensaje,
  icono = "fa-regular fa-folder-open",
  accion,
  className,
}) {
  const textoVisible = mensaje || (cargando ? "Cargando información..." : "No hay registros.");
  const iconoVisible = cargando ? "fa-solid fa-spinner fa-spin" : icono;

  return (
    <div
      aria-live={cargando ? "polite" : undefined}
      className={unirClases(
        "flex flex-col items-center justify-center gap-2 rounded-lg bg-surface-muted p-6 text-center text-sm text-ink-600",
        className
      )}
    >
      {iconoVisible && <i className={unirClases(iconoVisible, "text-lg")} aria-hidden="true"></i>}
      <p>{textoVisible}</p>
      {!cargando && accion}
    </div>
  );
}

export default EstadoVacio;
