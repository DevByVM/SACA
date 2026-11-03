import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { obtenerCargaPorSemanas } from '../../api/cargaApi.js';
import {
  formatearFechaEntrega,
  formatearPorcentaje,
  obtenerPrioridad,
  obtenerIdActividad,
  NIVELES_CARGA,
  HORAS_ESCALA_MAXIMA
} from '../../hooks/useCargaAcademica.js';
import Aviso from '../ui/Aviso';
import Boton from '../ui/Boton';
import EstadoVacio from '../ui/EstadoVacio';
import Insignia from '../ui/Insignia';
import Tarjeta from '../ui/Tarjeta';
import { unirClases } from '../ui/unirClases';

// Nombre de cada semana según la clasificación híbrida de SDACAS-20, que decide el backend
const NIVELES_SEMANA = { NORMAL: 'Baja', ELEVADA: 'Media', CRITICA: 'Alta' };
const TONOS_NIVEL = { NORMAL: 'exito', ELEVADA: 'advertencia', CRITICA: 'peligro' };
const BARRAS_NIVEL = { NORMAL: 'bg-success-700', ELEVADA: 'bg-warning-700', CRITICA: 'bg-danger-700' };

// Mismos colores de prioridad que la lista priorizada del tablero
const ESTILOS_PRIORIDAD = {
  'Alta': 'border-brand-700 bg-brand-700 text-white',
  'Media': 'border-warning-200 bg-warning-50 text-warning-700',
  'Baja': 'border-success-200 bg-success-50 text-success-700',
  'Sin fecha': 'border-line bg-surface-muted text-ink-600'
};
const ORDEN_PRIORIDAD = { 'Alta': 0, 'Media': 1, 'Baja': 2, 'Sin fecha': 3 };

const formateadorDiaMes = new Intl.DateTimeFormat('es-SV', { day: 'numeric', month: 'short' });
const formateadorFechaLarga = new Intl.DateTimeFormat('es-SV', { day: 'numeric', month: 'long', year: 'numeric' });

// AAAA-MM-DD como fecha local, sin el desfase de interpretarla en UTC
const fechaLocal = (texto) => {
  const [anio, mes, dia] = texto.split('-').map(Number);
  return new Date(anio, mes - 1, dia);
};

const diaMes = (texto) => formateadorDiaMes.format(fechaLocal(texto)).replace('.', '');
const rangoSemana = (semana) => `${diaMes(semana.inicio)} – ${diaMes(semana.fin)}`;

const formatearHoras = (valor) => {
  const numero = Number(valor ?? 0);
  return `${Number.isInteger(numero) ? numero : numero.toFixed(1)} h`;
};

const formatearPeso = (valor) => formatearPorcentaje(valor) ?? '0%';

// Pendientes primero, por prioridad (a igual prioridad se conserva el orden del backend: entrega, peso
// y horas); después las completadas o calificadas
const desglosar = (carga) => {
  const ahora = new Date();
  const pendientes = carga.actividadesPendientes
    .map((act, indice) => ({ ...act, prioridad: obtenerPrioridad(act, ahora), indice }))
    .sort((a, b) => ORDEN_PRIORIDAD[a.prioridad] - ORDEN_PRIORIDAD[b.prioridad] || a.indice - b.indice);
  const cerradas = carga.actividadesNoPendientes.map((act) => ({
    ...act,
    cierre: act.notaObtenida != null ? 'Calificada' : 'Completada'
  }));
  return { pendientes, cerradas };
};

function ActividadDesglose({ actividad, cerrada = false }) {
  const peso = formatearPorcentaje(actividad.porcentajeEvaluacion);
  return (
    <li
      className={unirClases(
        'flex flex-col gap-2 rounded-lg border border-line bg-surface px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between',
        cerrada && 'bg-surface-muted opacity-70'
      )}
    >
      <div className="min-w-0">
        <p className="text-sm font-semibold text-ink-900">
          {actividad.nombre}
        </p>
        <p className="mt-0.5 text-xs text-ink-600">
          <span className="font-semibold">{actividad.materiaInscritaCodigo || 'Sin materia'}</span>
          {' · '}
          {actividad.tipoActividadNombre || 'Evaluación'}
          {' · '}
          {formatearFechaEntrega(actividad.fechaEntrega || actividad.fechaInicio)}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-1.5">
        {cerrada ? (
          <Insignia tono="exito" icono={actividad.cierre === 'Calificada' ? 'fa-solid fa-star' : 'fa-solid fa-check'}>
            {actividad.cierre}
          </Insignia>
        ) : (
          <span
            className={unirClases(
              'inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-semibold',
              ESTILOS_PRIORIDAD[actividad.prioridad]
            )}
          >
            <span className="sr-only">Prioridad </span>
            {actividad.prioridad}
          </span>
        )}
        <Insignia tono="neutro">{peso ? `${peso} de la nota` : 'Sin porcentaje'}</Insignia>
        {!cerrada && actividad.tiempoEstimadoHoras ? (
          <Insignia tono="neutro" icono="fa-regular fa-clock">{formatearHoras(actividad.tiempoEstimadoHoras)}</Insignia>
        ) : null}
      </div>
    </li>
  );
}

function DesgloseSemana({ semana, id }) {
  const idTitulo = `${id}-titulo`;
  const { carga } = semana;
  const { pendientes, cerradas } = useMemo(() => desglosar(carga), [carga]);
  const titulo = semana.semanaActual
    ? `Esta semana · desde hoy hasta el ${diaMes(semana.fin)}`
    : `Semana del ${diaMes(semana.inicio)} al ${diaMes(semana.fin)}`;

  const encabezado = (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h3 id={idTitulo} className="text-base font-bold text-ink-900">{titulo}</h3>
      <Insignia tono={TONOS_NIVEL[carga.nivelGeneral]} className="text-sm">
        Carga {NIVELES_SEMANA[carga.nivelGeneral].toLowerCase()}
      </Insignia>
    </div>
  );

  // La semana actual ya se analiza en detalle arriba (SDACAS-20): aquí solo se remite a ese análisis
  if (semana.semanaActual) {
    return (
      <div id={id} role="region" aria-labelledby={idTitulo} aria-live="polite" className="mt-4 rounded-xl border border-line bg-surface-muted p-3 sm:p-4">
        {encabezado}
        <Aviso tono="info" icono="fa-solid fa-arrow-up" className="mt-3">
          El detalle de esta semana (horas, exigencia evaluativa y actividades pendientes) está en el análisis de carga de
          la parte superior. Selecciona una semana próxima para ver su desglose.
        </Aviso>
      </div>
    );
  }

  return (
    <div id={id} role="region" aria-labelledby={idTitulo} aria-live="polite" className="mt-4 rounded-xl border border-line bg-surface-muted p-3 sm:p-4">
      {encabezado}

      <dl className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
        {[
          ['Horas pendientes', formatearHoras(carga.horas.horasPendientes), NIVELES_CARGA[carga.horas.nivel]],
          ['Peso acumulado', formatearPeso(carga.exigencia.pesoAcumulado), NIVELES_CARGA[carga.exigencia.nivel]],
          ['Alto peso', carga.exigencia.evaluacionesAltoPeso, `${formatearPeso(carga.exigencia.umbralAltoPeso)} o más`],
          ['Concentración', carga.exigencia.concentracion ? 'Sí' : 'No', 'Alto peso el mismo día']
        ].map(([etiqueta, valor, detalle]) => (
          <div key={etiqueta} className="rounded-lg border border-line bg-surface px-3 py-2">
            <dt className="text-xs font-semibold text-ink-600">{etiqueta}</dt>
            <dd className="mt-0.5 text-xl font-bold text-ink-900">{valor}</dd>
            <dd className="text-xs text-ink-600">{detalle}</dd>
          </div>
        ))}
      </dl>

      <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-ink-900">
        {carga.motivos.map((motivo) => <li key={motivo}>{motivo}</li>)}
      </ul>

      <h4 className="mt-4 text-sm font-bold text-ink-900">
        Actividades de la semana
        <span className="ml-2 font-semibold text-ink-600">
          {pendientes.length} {pendientes.length === 1 ? 'pendiente' : 'pendientes'}
          {cerradas.length > 0 && ` · ${cerradas.length} ${cerradas.length === 1 ? 'cerrada' : 'cerradas'}`}
        </span>
      </h4>
      {pendientes.length === 0 && cerradas.length === 0 ? (
        <EstadoVacio
          className="mt-2 bg-surface"
          icono="fa-regular fa-calendar-check"
          mensaje="No hay actividades programadas para esta semana."
        />
      ) : (
        <ul className="mt-2 space-y-2">
          {pendientes.map((act) => <ActividadDesglose key={obtenerIdActividad(act)} actividad={act} />)}
          {cerradas.map((act) => <ActividadDesglose key={obtenerIdActividad(act)} actividad={act} cerrada />)}
        </ul>
      )}
    </div>
  );
}

// Carga por semana (SDACAS-26): independiente de los indicadores de los próximos 7 días, que no cambian
// al seleccionar otra semana
function CargaPorSemanas({ onIrAAcademico }) {
  const [datos, setDatos] = useState(null);
  const [estado, setEstado] = useState('cargando');
  const [seleccionada, setSeleccionada] = useState(null);
  const botones = useRef([]);
  const idDesglose = useId();

  const cargar = useCallback(async () => {
    setEstado('cargando');
    try {
      const respuesta = await obtenerCargaPorSemanas();
      setDatos(respuesta);
      setSeleccionada(respuesta.semanas[0]?.inicio ?? null);
      setEstado('listo');
    } catch {
      setEstado('error');
    }
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const semanas = useMemo(() => datos?.semanas ?? [], [datos]);
  const indiceSeleccionado = semanas.findIndex((semana) => semana.inicio === seleccionada);
  const semana = semanas[indiceSeleccionado];

  // Las flechas mueven la selección y el foco entre semanas, como un grupo de opciones
  const alPulsarTecla = (evento, indice) => {
    const destinos = {
      ArrowRight: indice + 1,
      ArrowDown: indice + 1,
      ArrowLeft: indice - 1,
      ArrowUp: indice - 1,
      Home: 0,
      End: semanas.length - 1
    };
    if (!(evento.key in destinos)) return;
    evento.preventDefault();
    const destino = Math.min(Math.max(destinos[evento.key], 0), semanas.length - 1);
    setSeleccionada(semanas[destino].inicio);
    botones.current[destino]?.focus();
  };

  const leyenda = (
    <div className="flex flex-wrap items-center gap-1.5" aria-hidden="true">
      {Object.entries(NIVELES_SEMANA).map(([nivel, texto]) => (
        <Insignia key={nivel} tono={TONOS_NIVEL[nivel]}>{texto}</Insignia>
      ))}
    </div>
  );

  let contenido;
  if (estado === 'cargando') {
    contenido = <EstadoVacio cargando mensaje="Calculando la carga de cada semana..." />;
  } else if (estado === 'error') {
    contenido = (
      <Aviso tono="peligro" titulo="No se pudo cargar la carga por semana">
        <p>Revisa tu conexión e inténtalo de nuevo. Los indicadores de los próximos 7 días no se ven afectados.</p>
        <Boton variante="secundario" tamano="sm" icono="fa-solid fa-rotate-right" className="mt-2" onClick={cargar}>
          Reintentar
        </Boton>
      </Aviso>
    );
  } else if (!datos.cicloActivo) {
    contenido = (
      <EstadoVacio
        icono="fa-regular fa-calendar-xmark"
        mensaje="No hay un ciclo académico activo para consultar la carga semanal. Activa o registra tu ciclo en Gestión Académica."
        accion={onIrAAcademico && (
          <Boton variante="secundario" tamano="sm" icono="fa-solid fa-book-open" onClick={onIrAAcademico}>
            Ir a Gestión Académica
          </Boton>
        )}
      />
    );
  } else if (semanas.length === 0) {
    contenido = (
      <EstadoVacio
        icono="fa-regular fa-calendar-xmark"
        mensaje={`El ciclo marcado como activo terminó el ${formateadorFechaLarga.format(fechaLocal(datos.finCiclo))}, así que no quedan semanas por consultar. Actualiza el estado de tus ciclos en Gestión Académica.`}
        accion={onIrAAcademico && (
          <Boton variante="secundario" tamano="sm" icono="fa-solid fa-book-open" onClick={onIrAAcademico}>
            Ir a Gestión Académica
          </Boton>
        )}
      />
    );
  } else {
    contenido = (
      <>
        <p className="mb-3 text-sm text-ink-600">
          Desde esta semana hasta el fin del ciclo ({formateadorFechaLarga.format(fechaLocal(datos.finCiclo))}).
          Cada semana se clasifica con las mismas reglas del estado de carga: baja equivale a normal, media a elevada y alta a crítica.
        </p>
        <div
          role="group"
          aria-label="Semanas del ciclo. Usa las flechas para cambiar de semana."
          className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6"
        >
          {semanas.map((item, indice) => {
            const activa = indice === indiceSeleccionado;
            const nivel = item.carga.nivelGeneral;
            const horas = Number(item.carga.horas.horasPendientes ?? 0);
            return (
              <button
                key={item.inicio}
                ref={(nodo) => { botones.current[indice] = nodo; }}
                type="button"
                aria-pressed={activa}
                aria-controls={idDesglose}
                tabIndex={activa ? 0 : -1}
                onClick={() => setSeleccionada(item.inicio)}
                onKeyDown={(evento) => alPulsarTecla(evento, indice)}
                aria-label={`${item.semanaActual ? 'Esta semana, ' : ''}${rangoSemana(item)}: carga ${NIVELES_SEMANA[nivel].toLowerCase()}, ${formatearHoras(horas)} pendientes y ${formatearPeso(item.carga.exigencia.pesoAcumulado)} de peso acumulado`}
                className={unirClases(
                  'flex min-h-11 flex-col gap-1.5 rounded-lg border px-3 py-2 text-left transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2',
                  activa ? 'border-brand-700 bg-brand-50 ring-1 ring-brand-700' : 'border-line bg-surface hover:border-brand-200'
                )}
              >
                <span className="flex items-center justify-between gap-1">
                  <span className="text-xs font-bold text-ink-900">{rangoSemana(item)}</span>
                  {item.semanaActual && <span className="text-[0.6875rem] font-semibold text-brand-700">Actual</span>}
                </span>
                <Insignia tono={TONOS_NIVEL[nivel]} className="self-start">{NIVELES_SEMANA[nivel]}</Insignia>
                <span className="block h-1.5 w-full overflow-hidden rounded-full bg-surface-muted">
                  <span
                    className={unirClases('block h-full rounded-full', BARRAS_NIVEL[nivel])}
                    style={{ width: `${Math.min((horas / HORAS_ESCALA_MAXIMA) * 100, 100)}%` }}
                  ></span>
                </span>
                <span className="flex justify-between gap-1 text-xs text-ink-600">
                  <span><i className="fa-regular fa-clock mr-1" aria-hidden="true"></i>{formatearHoras(horas)}</span>
                  <span>{formatearPeso(item.carga.exigencia.pesoAcumulado)}</span>
                </span>
              </button>
            );
          })}
        </div>

        {datos.semanasLimitadas && (
          <p className="mt-2 text-xs text-ink-600">Se muestran las primeras {semanas.length} semanas del ciclo.</p>
        )}
        {datos.actividadesSinFecha > 0 && (
          <Aviso tono="advertencia" className="mt-3">
            {datos.actividadesSinFecha === 1
              ? '1 actividad pendiente no tiene fecha y no se incluye en ninguna semana.'
              : `${datos.actividadesSinFecha} actividades pendientes no tienen fecha y no se incluyen en ninguna semana.`}
          </Aviso>
        )}

        {semana && <DesgloseSemana semana={semana} id={idDesglose} />}
      </>
    );
  }

  return (
    <Tarjeta
      titulo="Carga por semana"
      nivelTitulo={2}
      icono="fa-solid fa-calendar-days"
      className="p-4 sm:p-5"
      acciones={estado === 'listo' && semanas.length > 0 ? leyenda : null}
    >
      {contenido}
    </Tarjeta>
  );
}

export default CargaPorSemanas;
