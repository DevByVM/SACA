import { useCallback, useEffect, useState } from "react";
import { obtenerNotificacionesProximas } from "../api/notificacionesApi";

function formatearTipoActividad(tipoActividad) {
  return (tipoActividad || "ACTIVIDAD")
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letra) => letra.toUpperCase());
}

function formatearFechaEntrega(fechaEntrega) {
  if (!fechaEntrega) return "Fecha de entrega no disponible";

  return new Intl.DateTimeFormat("es-SV", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(fechaEntrega));
}

function NotificacionesProximas({ estudiante, onConfigurar, actualizacion }) {
  const estudianteId = estudiante?.id;
  const [notificaciones, setNotificaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");
  const [abierto, setAbierto] = useState(false);

  const cargarNotificaciones = useCallback(async () => {
    if (!estudianteId) {
      setCargando(false);
      setError("No se pudo identificar al estudiante.");
      return;
    }

    try {
      setCargando(true);
      setError("");
      const proximas = await obtenerNotificacionesProximas(estudianteId);
      setNotificaciones(proximas || []);
    } catch (cargaError) {
      setError(cargaError.message || "No se pudieron cargar las actividades próximas.");
    } finally {
      setCargando(false);
    }
  }, [estudianteId]);

  useEffect(() => {
    cargarNotificaciones();
  }, [actualizacion, cargarNotificaciones]);

  return (
    <div className="relative flex justify-end">
      <button
        type="button"
        onClick={() => setAbierto((estadoActual) => !estadoActual)}
        aria-expanded={abierto}
        aria-controls="notificaciones-proximas"
        className="relative inline-flex h-11 w-11 items-center justify-center rounded-xl border border-[#430000]/20 bg-[#ffffff] text-[#960000] shadow-sm transition-colors hover:bg-[#960000]/10 focus:outline-none focus:ring-2 focus:ring-[#960000] focus:ring-offset-2"
        title="Ver notificaciones próximas"
      >
        <i className="fa-solid fa-bell text-lg" aria-hidden="true"></i>
        <span className="sr-only">Ver notificaciones próximas</span>
        {!cargando && !error && notificaciones.length > 0 && (
          <span className="absolute -right-2 -top-2 flex min-h-5 min-w-5 items-center justify-center rounded-full bg-[#960000] px-1 text-[10px] font-bold text-[#ffffff]">
            {notificaciones.length}
          </span>
        )}
      </button>

      {abierto && (
        <section
          id="notificaciones-proximas"
          className="absolute right-0 top-full z-30 mt-3 w-[min(24rem,calc(100vw-3rem))] overflow-hidden rounded-2xl border border-[#430000]/20 bg-[#ffffff] shadow-lg"
        >
          <header className="flex items-start justify-between gap-4 border-b border-[#430000]/15 px-5 py-4">
            <div>
              <h2 className="flex items-center gap-2 text-lg font-bold text-[#430000]">
                <i className="fa-solid fa-bell text-[#960000]" aria-hidden="true"></i>
                Próximas entregas
              </h2>
              <p className="mt-1 text-xs text-[#430000]/60">
                Actividades que coinciden con tus preferencias de notificación.
              </p>
            </div>
            <button
              type="button"
              onClick={() => setAbierto(false)}
              className="text-sm font-bold text-[#960000] hover:text-[#430000]"
            >
              Cerrar
            </button>
          </header>

          <div className="max-h-96 overflow-y-auto p-5">
            {cargando && (
              <p className="text-sm font-medium text-[#430000] animate-pulse">
                Cargando próximas entregas...
              </p>
            )}

            {!cargando && error && (
              <div className="space-y-3" role="alert">
                <p className="rounded-xl border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800">
                  {error}
                </p>
                <button
                  type="button"
                  onClick={cargarNotificaciones}
                  className="rounded-lg border border-[#960000] px-3 py-2 text-xs font-bold text-[#960000] transition-colors hover:bg-[#960000] hover:text-[#eeeeee]"
                >
                  Reintentar
                </button>
              </div>
            )}

            {!cargando && !error && notificaciones.length === 0 && (
              <div className="space-y-3 rounded-xl border border-[#430000]/15 bg-[#430000]/5 p-4">
                <p className="text-sm text-[#430000]/75">
                  No hay actividades próximas que coincidan con tus preferencias.
                </p>
                <button
                  type="button"
                  onClick={onConfigurar}
                  className="text-xs font-bold text-[#960000] underline underline-offset-2"
                >
                  Configurar tipos de notificación
                </button>
              </div>
            )}

            {!cargando && !error && notificaciones.length > 0 && (
              <ul className="space-y-3">
                {notificaciones.map((actividad) => (
                  <li
                    key={actividad.idActividad}
                    className="rounded-xl border border-[#430000]/15 bg-[#ffffff] p-3"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <span className="rounded-md bg-[#960000]/10 px-2 py-1 text-[10px] font-bold text-[#430000]">
                          {formatearTipoActividad(actividad.tipoActividadNombre || actividad.tipoActividad)}
                        </span>
                        <h3 className="mt-2 text-sm font-bold text-[#430000]">
                          {actividad.nombre || "Actividad sin nombre"}
                        </h3>
                      </div>
                      <i className="fa-solid fa-calendar-day text-[#960000]" aria-hidden="true"></i>
                    </div>
                    <p className="mt-2 text-xs text-[#430000]/70">
                      {actividad.materiaInscritaCodigo || "Materia no disponible"} · Entrega: {formatearFechaEntrega(actividad.fechaEntrega)}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      )}
    </div>
  );
}

export default NotificacionesProximas;
