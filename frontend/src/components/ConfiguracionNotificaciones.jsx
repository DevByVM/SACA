import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  guardarPreferenciasNotificacion,
  obtenerPreferenciasNotificacion,
} from "../api/notificacionesApi";
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import EstadoVacio from "./ui/EstadoVacio";
import Tarjeta from "./ui/Tarjeta";

function formatearTipoActividad(tipoActividad) {
  return tipoActividad
    .toLowerCase()
    .replace(/_/g, " ")
    .replace(/\b\w/g, (letra) => letra.toUpperCase());
}

function ConfiguracionNotificaciones({ estudiante, onPreferenciasGuardadas }) {
  const estudianteId = estudiante?.id;
  const [tiposDisponibles, setTiposDisponibles] = useState([]);
  const [tiposHabilitados, setTiposHabilitados] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState("");

  const cargarPreferencias = useCallback(async () => {
    if (!estudianteId) {
      setCargando(false);
      setError("No se pudo identificar al estudiante.");
      return;
    }

    try {
      setCargando(true);
      setError("");
      const preferencias = await obtenerPreferenciasNotificacion(estudianteId);
      setTiposDisponibles(preferencias.tiposDisponibles || []);
      setTiposHabilitados(preferencias.tiposActividadHabilitados || []);
    } catch (cargaError) {
      setError(cargaError.message || "No se pudieron cargar las preferencias de notificaciones.");
    } finally {
      setCargando(false);
    }
  }, [estudianteId]);

  useEffect(() => {
    cargarPreferencias();
  }, [cargarPreferencias]);

  const cambiarTipoActividad = (tipoActividad) => {
    setTiposHabilitados((actuales) => (
      actuales.includes(tipoActividad)
        ? actuales.filter((tipo) => tipo !== tipoActividad)
        : [...actuales, tipoActividad]
    ));
  };

  const guardarPreferencias = async (event) => {
    event.preventDefault();

    if (!estudianteId) {
      toast.error("No se pudo identificar al estudiante.");
      return;
    }

    try {
      setGuardando(true);
      setError("");
      const preferencias = await guardarPreferenciasNotificacion(estudianteId, tiposHabilitados);
      setTiposHabilitados(preferencias.tiposActividadHabilitados || []);
      onPreferenciasGuardadas?.();
      toast.success("Preferencias de notificaciones guardadas.");
    } catch (guardadoError) {
      const mensaje = guardadoError.message || "No se pudieron guardar las preferencias.";
      setError(mensaje);
      toast.error(mensaje);
    } finally {
      setGuardando(false);
    }
  };

  if (cargando) {
    return <EstadoVacio cargando mensaje="Cargando configuración de notificaciones..." />;
  }

  return (
    <section className="space-y-5">
      <EncabezadoModulo
        descripcion="Elige los tipos de actividades para los que deseas recibir recordatorios."
        icono="fa-solid fa-bell"
      />

      <Tarjeta>
        <form className="space-y-5" onSubmit={guardarPreferencias}>
          {error && <Aviso tono="peligro">{error}</Aviso>}

          {tiposDisponibles.length === 0 ? (
            <EstadoVacio mensaje="No hay tipos de actividad disponibles para configurar." />
          ) : (
            <fieldset>
              <legend className="text-sm font-semibold text-ink-900">
                Tipos de actividades
              </legend>
              <p className="mt-1 text-xs text-ink-600">
                Puedes desmarcar todos los tipos si no deseas recibir recordatorios.
              </p>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {tiposDisponibles.map((tipoActividad) => {
                  const idCampo = `notificacion-${tipoActividad.toLowerCase()}`;
                  const habilitado = tiposHabilitados.includes(tipoActividad);

                  return (
                    <label
                      key={tipoActividad}
                      htmlFor={idCampo}
                      className={`flex cursor-pointer items-center gap-3 rounded-lg border p-4 text-sm transition-colors ${
                        habilitado
                          ? "border-brand-700 bg-brand-50 text-ink-900"
                          : "border-line bg-surface text-ink-600 hover:bg-surface-muted"
                      }`}
                    >
                      <input
                        id={idCampo}
                        type="checkbox"
                        checked={habilitado}
                        onChange={() => cambiarTipoActividad(tipoActividad)}
                        className="h-4 w-4 accent-brand-700"
                      />
                      <span className="font-semibold">{formatearTipoActividad(tipoActividad)}</span>
                    </label>
                  );
                })}
              </div>
            </fieldset>
          )}

          <div className="flex flex-wrap items-center gap-3 border-t border-line pt-5">
            <Boton type="submit" disabled={guardando || tiposDisponibles.length === 0}>
              {guardando ? "Guardando..." : "Guardar preferencias"}
            </Boton>
            <span className="text-xs text-ink-600" aria-live="polite">
              {tiposHabilitados.length} tipo(s) habilitado(s)
            </span>
          </div>
        </form>
      </Tarjeta>
    </section>
  );
}

export default ConfiguracionNotificaciones;
