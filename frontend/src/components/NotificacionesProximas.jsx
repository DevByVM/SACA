import { useCallback, useEffect, useState } from "react";
import { obtenerNotificacionesProximas } from "../api/notificacionesApi";
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import EstadoVacio from "./ui/EstadoVacio";
import Insignia from "./ui/Insignia";

function formatearTipoActividad(tipoActividad) {
  return String(tipoActividad || "ACTIVIDAD")
    .trim()
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
    <div className="relative flex justify-end text-left">
      <button
        type="button"
        onClick={() => setAbierto((estadoActual) => !estadoActual)}
        aria-expanded={abierto}
        aria-controls="notificaciones-proximas"
        className="relative inline-flex h-11 w-11 items-center justify-center rounded-xl border border-line bg-surface text-brand-700 shadow-sm transition-colors hover:bg-brand-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2"
        title="Ver notificaciones próximas"
      >
        <i className="fa-solid fa-bell text-lg" aria-hidden="true"></i>
        <span className="sr-only">Ver notificaciones próximas</span>
        {!cargando && !error && notificaciones.length > 0 && (
          <span className="absolute -right-2 -top-2 flex min-h-6 min-w-6 items-center justify-center rounded-full bg-brand-700 px-1.5 text-xs font-bold text-white ring-2 ring-surface">
            {notificaciones.length}
          </span>
        )}
      </button>

      {abierto && (
        <section
          id="notificaciones-proximas"
          className="absolute right-0 top-full z-30 mt-3 w-[min(24rem,calc(100vw-3rem))] overflow-hidden rounded-xl border border-line bg-surface shadow-lg"
        >
          <header className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div className="min-w-0">
              <h2 className="flex items-center gap-2 text-lg font-bold text-ink-900">
                <i className="fa-solid fa-bell text-brand-700" aria-hidden="true"></i>
                Próximas entregas
              </h2>
              <p className="mt-1 text-sm text-ink-600">
                Actividades que coinciden con tus preferencias de notificación.
              </p>
            </div>
            <Boton variante="texto" onClick={() => setAbierto(false)} className="shrink-0">
              Cerrar
            </Boton>
          </header>

          <div className="max-h-96 overflow-y-auto p-4 sm:p-5">
            {cargando && (
              <EstadoVacio cargando mensaje="Cargando próximas entregas..." />
            )}

            {!cargando && error && (
              <div className="space-y-3">
                <Aviso tono="peligro">{error}</Aviso>
                <Boton variante="secundario" tamano="sm" icono="fa-solid fa-rotate-right" onClick={cargarNotificaciones}>
                  Reintentar
                </Boton>
              </div>
            )}

            {!cargando && !error && notificaciones.length === 0 && (
              <EstadoVacio
                icono="fa-regular fa-bell-slash"
                mensaje="No hay actividades próximas que coincidan con tus preferencias."
                accion={
                  <Boton variante="texto" onClick={onConfigurar}>
                    Configurar tipos de notificación
                  </Boton>
                }
              />
            )}

            {!cargando && !error && notificaciones.length > 0 && (
              <ul className="space-y-3">
                {notificaciones.map((actividad) => (
                  <li
                    key={actividad.idActividad}
                    className="rounded-lg border border-line bg-surface p-3"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <Insignia tono="marca">
                          {formatearTipoActividad(actividad.tipoActividadNombre || actividad.tipoActividad)}
                        </Insignia>
                        <h3 className="mt-2 break-words text-base font-semibold text-ink-900">
                          {actividad.nombre || "Actividad sin nombre"}
                        </h3>
                      </div>
                      <i className="fa-solid fa-calendar-day mt-1 text-brand-700" aria-hidden="true"></i>
                    </div>
                    <p className="mt-2 text-sm text-ink-600">
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
