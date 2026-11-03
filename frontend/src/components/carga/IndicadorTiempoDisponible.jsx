import { useCallback, useEffect, useState } from 'react';
import { obtenerTiempoDisponible } from '../../api/cargaApi.js';
import Boton from '../ui/Boton';

// Minutos enteros como «2 h 30 min», «45 min» o «3 h»
const formatearDuracion = (minutos) => {
  const total = Math.max(0, Math.round(Number(minutos) || 0));
  const horas = Math.floor(total / 60);
  const resto = total % 60;
  if (horas === 0) return `${resto} min`;
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`;
};

// Comparación informativa entre el tiempo disponible y las horas pendientes de la semana
function comparacion(disponibles, minutosPendientes) {
  if (minutosPendientes === 0) return 'No tienes horas pendientes en esta semana.';
  if (disponibles >= minutosPendientes) return 'El tiempo disponible cubre las horas pendientes de la semana.';
  return `Faltan ${formatearDuracion(minutosPendientes - disponibles)} de tiempo de estudio para cubrir las horas pendientes.`;
}

// SDACAS-32: tiempo de estudio disponible de la semana seleccionada. El cálculo lo hace el backend (bloques
// «Tiempo de estudio» menos clases, jornada laboral y bloques «No disponible», sin contar dos veces un mismo
// minuto). Es solo informativo: usa colores neutros y no cambia el estado de carga ni sus niveles.
// La caché la aporta el panel para no repetir la consulta al volver a una semana ya vista.
function IndicadorTiempoDisponible({ semana, cache }) {
  const clave = `${semana.inicio}|${semana.fin}`;
  const [consulta, setConsulta] = useState(() => (cache.has(clave) ? { clave, datos: cache.get(clave) } : { clave, datos: null }));
  const [intento, setIntento] = useState(0);
  const datos = consulta.clave === clave ? consulta.datos : null;
  const error = consulta.clave === clave && consulta.error;

  useEffect(() => {
    if (cache.has(clave)) {
      setConsulta({ clave, datos: cache.get(clave) });
      return undefined;
    }
    let vigente = true;
    setConsulta({ clave, datos: null });
    obtenerTiempoDisponible(semana.inicio, semana.fin)
      .then((respuesta) => {
        cache.set(clave, respuesta);
        if (vigente) setConsulta({ clave, datos: respuesta });
      })
      .catch(() => {
        if (vigente) setConsulta({ clave, datos: null, error: true });
      });
    return () => {
      vigente = false;
    };
  }, [cache, clave, semana.inicio, semana.fin, intento]);

  const reintentar = useCallback(() => setIntento((valor) => valor + 1), []);
  const minutosPendientes = Math.round(Number(semana.carga.horas.horasPendientes ?? 0) * 60);

  return (
    <section aria-label="Tiempo de estudio disponible" className="mt-3 rounded-lg border border-line bg-surface px-3 py-3">
      <h4 className="flex items-center gap-2 text-sm font-bold text-ink-900">
        <i className="fa-regular fa-hourglass-half text-ink-600" aria-hidden="true"></i>
        Tiempo de estudio disponible
        {semana.semanaActual && <span className="text-xs font-semibold text-ink-600">· desde ahora</span>}
      </h4>

      {error ? (
        <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-ink-600">
          <span>No se pudo calcular el tiempo disponible. El resto de la carga se muestra igualmente.</span>
          <Boton variante="texto" tamano="sm" icono="fa-solid fa-rotate-right" onClick={reintentar}>
            Reintentar
          </Boton>
        </div>
      ) : !datos ? (
        <p className="mt-2 text-sm text-ink-600">
          <i className="fa-solid fa-spinner fa-spin mr-1" aria-hidden="true"></i>
          Calculando el tiempo disponible…
        </p>
      ) : datos.minutosReservados === 0 ? (
        <p className="mt-2 text-sm text-ink-600">
          No tienes bloques «Tiempo de estudio» del ciclo activo {semana.semanaActual ? 'en lo que queda de esta semana' : 'en esta semana'}.
          Puedes reservarlos en Gestión de disponibilidad. Horas pendientes: {formatearDuracion(minutosPendientes)}.
        </p>
      ) : (
        <>
          <dl className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {[
              ['Disponible', datos.minutosDisponibles, 'Para estudiar'],
              ['Reservado', datos.minutosReservados, 'Bloques «Tiempo de estudio»'],
              ['Descontado', datos.minutosDescontados, 'Clases, jornada y «No disponible»'],
              ['Pendiente', minutosPendientes, 'Horas de las actividades']
            ].map(([etiqueta, minutos, detalle]) => (
              <div key={etiqueta} className="min-w-0">
                <dt className="text-xs font-semibold text-ink-600">{etiqueta}</dt>
                <dd className="text-lg font-bold text-ink-900">{formatearDuracion(minutos)}</dd>
                <dd className="text-xs text-ink-600">{detalle}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-2 text-sm text-ink-900">{comparacion(datos.minutosDisponibles, minutosPendientes)}</p>
        </>
      )}
      <p className="mt-1 text-xs text-ink-600">Dato informativo: no cambia el estado de carga de la semana.</p>
    </section>
  );
}

export default IndicadorTiempoDisponible;
