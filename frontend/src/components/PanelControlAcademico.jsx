import React from 'react';
import {
  useCargaAcademica,
  obtenerIdActividad,
  formatearFechaEntrega,
  formatearFechaCorta,
  formatearPorcentaje
} from '../hooks/useCargaAcademica.js';
import Aviso from './ui/Aviso';
import Boton from './ui/Boton';
import EncabezadoModulo from './ui/EncabezadoModulo';
import EstadoVacio from './ui/EstadoVacio';
import Insignia from './ui/Insignia';
import { unirClases } from './ui/unirClases';

// Máximo de actividades que muestra el panel (la lista completa vive en Análisis de Carga)
const MAX_ACTIVIDADES_PANEL = 5;

// Colores del semáforo con los tonos de SACA; si el hook cambiara sus textos se usan sus clases originales
const TONOS_SEMAFORO = {
  'Carga Normal': { caja: 'border-success-200 bg-success-50 text-success-700', icono: 'text-success-700', barra: 'bg-success-700' },
  'Carga Elevada': { caja: 'border-warning-200 bg-warning-50 text-warning-700', icono: 'text-warning-700', barra: 'bg-warning-700' },
  'Sobrecarga Crítica': { caja: 'border-danger-200 bg-danger-50 text-danger-700', icono: 'text-danger-700', barra: 'bg-danger-700' }
};

// Panel de control académico (SDACAS-28)
// Responde: "¿Cómo estoy y qué debo atender?"
// Solo lectura: las acciones sobre actividades están en Gestión Académica.
function PanelControlAcademico({ estudiante, onNavegar }) {
  const {
    cargando,
    actividades,
    horasPendientes,
    ventana,
    semaforo,
    porcentajeCarga,
    avisosPonderacion,
    avisosConflicto
  } = useCargaAcademica(estudiante);

  const irA = (vista) => () => {
    if (onNavegar) onNavegar(vista);
  };

  if (cargando) {
    return (
      <EstadoVacio cargando mensaje="Sincronizando análisis horario de SACA UES..." />
    );
  }

  // Avisos: una entrada por tipo (alta ponderación / conflicto laboral), en el orden de las actividades
  const avisos = actividades.flatMap((act) => {
    const items = [];
    if (act.altaPonderacion) items.push({ tipo: 'ponderacion', act });
    if (act.tieneConflicto) items.push({ tipo: 'conflicto', act });
    return items;
  });

  const proximas = actividades.slice(0, MAX_ACTIVIDADES_PANEL);
  const restantes = actividades.length - proximas.length;

  const rangoTexto = ventana
    ? `Próximos 7 días · ${formatearFechaCorta(ventana.desde)} – ${formatearFechaCorta(ventana.hasta)}`
    : 'Próximos 7 días';

  const indicadores = [
    {
      icono: 'fa-hourglass-half',
      etiqueta: 'Horas pendientes',
      valor: `${horasPendientes.toFixed(1)} hrs`,
      detalle: 'Carga estimada'
    },
    {
      icono: 'fa-list-check',
      etiqueta: 'Actividades próximas',
      valor: actividades.length,
      detalle: actividades.length === 1 ? 'Pendiente' : 'Pendientes'
    },
    {
      icono: 'fa-bell',
      etiqueta: 'Avisos',
      valor: avisos.length,
      detalle: `${avisosPonderacion.length} ponderación · ${avisosConflicto.length} ${avisosConflicto.length === 1 ? 'conflicto' : 'conflictos'}`,
      destacado: avisos.length > 0
    }
  ];

  const accesos = [
    { vista: 'academico', icono: 'fa-book-open', titulo: 'Gestionar actividades', detalle: 'Crear, editar, completar y registrar notas' },
    { vista: 'calendario', icono: 'fa-calendar-week', titulo: 'Ver horario de clases', detalle: 'Calendario Académico' },
    { vista: 'analisis', icono: 'fa-gauge-high', titulo: 'Ver análisis completo', detalle: 'Distribución de la carga y lista priorizada' }
  ];

  const tonoSemaforo = TONOS_SEMAFORO[semaforo.texto];

  const clasesTarjetaBoton =
    'rounded-xl border bg-surface px-4 py-3 text-left shadow-sm transition-colors hover:border-brand-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2';

  return (
    <section className="w-full min-w-0 flex-1 space-y-5">

      {/* ENCABEZADO */}
      <EncabezadoModulo
        descripcion={rangoTexto}
        icono="fa-solid fa-table-cells-large"
        acciones={
          <Boton onClick={irA('analisis')}>
            Ver análisis completo
            <i className="fa-solid fa-arrow-right" aria-hidden="true"></i>
          </Boton>
        }
      />

      {/* INDICADORES */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">

        {/* Estado de carga */}
        <button
          type="button"
          onClick={irA('analisis')}
          className={unirClases(
            'order-first sm:col-span-2 lg:order-last lg:col-span-1 rounded-xl border px-4 py-3 text-left shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2',
            tonoSemaforo?.caja ?? semaforo.bg
          )}
        >
          <div className="flex items-center gap-2">
            <i
              className={unirClases('fa-solid text-xl', tonoSemaforo ? `${tonoSemaforo.icono} ${semaforo.icon.split(' ')[0]}` : semaforo.icon)}
              aria-hidden="true"
            ></i>
            <div className="min-w-0">
              <p className="text-xs font-semibold">Estado de carga</p>
              <p className="text-lg font-bold leading-tight">{semaforo.texto}</p>
            </div>
          </div>
          <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-surface-muted">
            <div
              className={unirClases('h-full transition-all duration-500', tonoSemaforo?.barra ?? semaforo.barra)}
              style={{ width: `${porcentajeCarga}%` }}
            ></div>
          </div>
        </button>

        {indicadores.map((item) => (
          <button
            key={item.etiqueta}
            type="button"
            onClick={irA('analisis')}
            className={unirClases(
              'flex items-center gap-3',
              clasesTarjetaBoton,
              item.destacado ? 'border-brand-200' : 'border-line'
            )}
          >
            <div
              className={unirClases(
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                item.destacado ? 'bg-brand-700 text-white' : 'bg-brand-50 text-brand-700'
              )}
            >
              <i className={`fa-solid ${item.icono}`} aria-hidden="true"></i>
            </div>
            <div className="min-w-0">
              <p className="text-xs font-semibold text-ink-600">{item.etiqueta}</p>
              <p className="text-xl font-bold leading-tight text-ink-900">{item.valor}</p>
              <p className="text-xs text-ink-600">{item.detalle}</p>
            </div>
          </button>
        ))}
      </div>

      {/* AVISOS + PRÓXIMAS ACTIVIDADES */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">

        {/* AVISOS IMPORTANTES */}
        <div className="flex flex-col rounded-xl border border-line border-l-4 border-l-brand-700 bg-surface p-4 shadow-sm sm:p-5 lg:col-span-5">
          <div className="mb-3 flex items-center gap-2">
            <i className="fa-solid fa-bell text-brand-700" aria-hidden="true"></i>
            <h2 className="text-base font-bold text-ink-900">Avisos importantes</h2>
            {avisos.length > 0 && (
              <span className="ml-auto rounded-full bg-brand-700 px-2 py-0.5 text-xs font-bold text-white">
                {avisos.length}
              </span>
            )}
          </div>

          {avisos.length === 0 ? (
            <Aviso tono="exito">
              No tienes actividades de alta ponderación ni conflictos con tu jornada laboral en los próximos 7 días.
            </Aviso>
          ) : (
            <ul className="max-h-72 divide-y divide-line overflow-y-auto">
              {avisos.map(({ tipo, act }) => {
                const esConflicto = tipo === 'conflicto';
                const detalle = esConflicto
                  ? `Coincide con tu jornada del ${act.jornadaConflicto.diaSemana}, ${act.jornadaConflicto.horaInicio.substring(0, 5)} – ${act.jornadaConflicto.horaFin.substring(0, 5)}`
                  : [
                      act.materiaInscritaCodigo || 'SACA',
                      act.tipoActividadNombre || 'Evaluación',
                      formatearPorcentaje(act.porcentajeEvaluacion)
                    ].filter(Boolean).join(' · ');

                return (
                  <li
                    key={`aviso-${tipo}-${obtenerIdActividad(act)}`}
                    className="flex items-start gap-2.5 py-3 first:pt-1 last:pb-1"
                  >
                    <i
                      className={`fa-solid ${
                        esConflicto
                          ? 'fa-triangle-exclamation text-warning-700'
                          : 'fa-circle-exclamation text-brand-700'
                      } mt-0.5`}
                      aria-hidden="true"
                    ></i>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <p className="break-words text-sm font-semibold leading-tight text-ink-900">
                          {act.nombre}
                        </p>
                        <Insignia tono={esConflicto ? 'advertencia' : 'marca'}>
                          {esConflicto ? 'Conflicto laboral' : 'Alta ponderación'}
                        </Insignia>
                      </div>
                      <p className="mt-1 text-sm text-ink-600">{detalle}</p>
                      <p className="mt-0.5 text-sm font-semibold text-brand-700">
                        Vence {formatearFechaEntrega(act.fechaReferencia)}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {avisosConflicto.length > 0 && (
            <Boton variante="texto" onClick={irA('jornada')} className="mt-3 self-start">
              Revisar horario laboral
              <i className="fa-solid fa-arrow-right" aria-hidden="true"></i>
            </Boton>
          )}
        </div>

        {/* PRÓXIMAS ACTIVIDADES */}
        <div className="flex flex-col rounded-xl border border-line bg-surface p-4 shadow-sm sm:p-5 lg:col-span-7">
          <div className="mb-3 flex items-center gap-2">
            <i className="fa-regular fa-calendar text-ink-600" aria-hidden="true"></i>
            <h2 className="text-base font-bold text-ink-900">Próximas actividades</h2>
            <span className="ml-auto text-xs font-semibold text-ink-600">
              {actividades.length === 0
                ? ''
                : `${proximas.length} de ${actividades.length}`}
            </span>
          </div>

          {actividades.length === 0 ? (
            <EstadoVacio
              icono="fa-regular fa-calendar-check"
              mensaje="No hay evaluaciones programadas para los próximos 7 días."
            />
          ) : (
            <ul>
              {proximas.map((act) => {
                const porcentajeTexto = formatearPorcentaje(act.porcentajeEvaluacion);

                return (
                  <li
                    key={obtenerIdActividad(act)}
                    // Solo el borde inferior separa filas: divide-y pintaba también el izquierdo de las que no son de alta ponderación
                    className={`flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-l-4 border-b-line py-3 pl-3 last:border-b-0 sm:flex-nowrap ${
                      act.altaPonderacion ? 'border-l-brand-700' : 'border-l-transparent'
                    }`}
                  >
                    <span className="shrink-0 rounded-md bg-surface-muted px-2 py-1 font-mono text-xs font-bold text-ink-900">
                      {act.materiaInscritaCodigo || 'SACA'}
                    </span>

                    <div className="min-w-0 flex-1 basis-40">
                      <p className="break-words text-sm font-semibold leading-tight text-ink-900">
                        {act.nombre}
                        {act.tieneConflicto && (
                          <i
                            className="fa-solid fa-triangle-exclamation ml-1.5 text-xs text-warning-700"
                            title="Coincide con tu jornada laboral"
                            role="img"
                            aria-label="Coincide con tu jornada laboral"
                          ></i>
                        )}
                      </p>
                      <p className="mt-1 text-sm text-ink-600">
                        {act.tipoActividadNombre || 'Evaluación'} · {formatearFechaEntrega(act.fechaReferencia)}
                      </p>
                    </div>

                    <div className="ml-auto flex shrink-0 items-center gap-1.5">
                      {porcentajeTexto && (
                        <span
                          className={`rounded-md border px-2 py-0.5 text-xs font-bold ${
                            act.altaPonderacion
                              ? 'border-brand-700 bg-brand-700 text-white'
                              : 'border-line bg-surface text-ink-900'
                          }`}
                        >
                          {porcentajeTexto}
                        </span>
                      )}
                      <span className="rounded-md border border-brand-200 bg-brand-50 px-2 py-0.5 text-xs font-bold text-ink-900">
                        {act.tiempoEstimadoHoras} hrs
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {restantes > 0 && (
            <Boton variante="texto" onClick={irA('analisis')} className="mt-3 self-start text-left">
              y {restantes} {restantes === 1 ? 'actividad más' : 'actividades más'} en Análisis de Carga
              <i className="fa-solid fa-arrow-right" aria-hidden="true"></i>
            </Boton>
          )}
        </div>
      </div>

      {/* ACCESOS RÁPIDOS */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {accesos.map((acceso) => (
          <button
            key={acceso.vista}
            type="button"
            onClick={irA(acceso.vista)}
            className={unirClases('flex items-center gap-3 border-line', clasesTarjetaBoton)}
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
              <i className={`fa-solid ${acceso.icono}`} aria-hidden="true"></i>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-bold leading-tight text-ink-900">{acceso.titulo}</p>
              <p className="mt-0.5 text-xs text-ink-600">{acceso.detalle}</p>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

export default PanelControlAcademico;
