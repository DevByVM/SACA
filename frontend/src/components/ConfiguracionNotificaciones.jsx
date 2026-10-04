import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  guardarPreferenciasNotificacion,
  obtenerPreferenciasNotificacion,
} from "../api/notificacionesApi";

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
    return (
      <div className="rounded-2xl border border-[#430000]/20 bg-[#ffffff] p-6 text-sm font-medium text-[#430000] animate-pulse">
        Cargando configuración de notificaciones...
      </div>
    );
  }

  return (
    <section className="overflow-hidden rounded-2xl border border-[#430000]/20 bg-[#ffffff] shadow-sm">
      <header className="bg-[#960000] px-6 py-4 text-[#eeeeee]">
        <h2 className="flex items-center gap-2 text-lg font-bold">
          <i className="fa-solid fa-bell" aria-hidden="true"></i>
          Configuración de notificaciones
        </h2>
        <p className="mt-1 text-sm text-[#eeeeee]/80">
          Elige los tipos de actividades para los que deseas recibir recordatorios.
        </p>
      </header>

      <form className="space-y-5 p-6" onSubmit={guardarPreferencias}>
        {error && (
          <div className="rounded-xl border border-rose-300 bg-rose-50 p-3 text-sm text-rose-800" role="alert">
            {error}
          </div>
        )}

        {tiposDisponibles.length === 0 ? (
          <p className="rounded-xl border border-[#430000]/20 bg-[#430000]/5 p-4 text-sm text-[#430000]/70">
            No hay tipos de actividad disponibles para configurar.
          </p>
        ) : (
          <fieldset>
            <legend className="text-sm font-bold text-[#430000]">
              Tipos de actividades
            </legend>
            <p className="mt-1 text-xs text-[#430000]/60">
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
                    className={`flex cursor-pointer items-center gap-3 rounded-xl border p-4 text-sm transition-colors ${
                      habilitado
                        ? "border-[#960000] bg-[#960000]/10 text-[#430000]"
                        : "border-[#430000]/20 bg-[#ffffff] text-[#430000]/80 hover:bg-[#430000]/5"
                    }`}
                  >
                    <input
                      id={idCampo}
                      type="checkbox"
                      checked={habilitado}
                      onChange={() => cambiarTipoActividad(tipoActividad)}
                      className="h-4 w-4 accent-[#960000]"
                    />
                    <span className="font-semibold">{formatearTipoActividad(tipoActividad)}</span>
                  </label>
                );
              })}
            </div>
          </fieldset>
        )}

        <div className="flex flex-wrap items-center gap-3 border-t border-[#430000]/15 pt-5">
          <button
            type="submit"
            disabled={guardando || tiposDisponibles.length === 0}
            className="rounded-xl bg-[#960000] px-5 py-3 text-sm font-bold text-[#eeeeee] transition-colors hover:bg-[#430000] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {guardando ? "Guardando..." : "Guardar preferencias"}
          </button>
          <span className="text-xs text-[#430000]/60" aria-live="polite">
            {tiposHabilitados.length} tipo(s) habilitado(s)
          </span>
        </div>
      </form>
    </section>
  );
}

export default ConfiguracionNotificaciones;
