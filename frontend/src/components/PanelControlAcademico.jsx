import React from 'react';
import {
  useCargaAcademica,
  obtenerIdActividad,
  formatearFechaEntrega,
  formatearFechaCorta,
  formatearPorcentaje
} from '../hooks/useCargaAcademica.js';

// Máximo de actividades que muestra el panel (la lista completa vive en Análisis de Carga)
const MAX_ACTIVIDADES_PANEL = 5;

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
      <div className="p-8 text-center text-[#430000] font-medium text-sm animate-pulse">
        Sincronizando análisis horario de SACA UES...
      </div>
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

  return (
    <div className="w-full flex-1 min-w-0 space-y-4 bg-[#eeeeee] p-4 sm:p-5 rounded-xl">

      {/* ENCABEZADO */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b border-[#430000]/20 pb-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 shrink-0 rounded-lg bg-[#430000] text-white flex items-center justify-center">
            <i className="fa-solid fa-table-cells-large"></i>
          </div>
          <div>
            <h3 className="text-xl font-bold text-[#430000] leading-tight">
              Panel de control académico
            </h3>
            <p className="text-xs text-[#430000]/60">{rangoTexto}</p>
          </div>
        </div>

        <button
          type="button"
          onClick={irA('analisis')}
          className="inline-flex items-center justify-center gap-2 bg-[#960000] hover:bg-[#430000] text-[#eeeeee] text-xs font-bold px-4 py-2.5 rounded-lg transition-colors"
        >
          Ver análisis completo
          <i className="fa-solid fa-arrow-right"></i>
        </button>
      </div>

      {/* INDICADORES */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">

        {/* Estado de carga */}
        <button
          type="button"
          onClick={irA('analisis')}
          className={`order-first lg:order-last col-span-2 lg:col-span-1 text-left border rounded-xl px-4 py-3 shadow-sm ${semaforo.bg}`}
        >
          <div className="flex items-center gap-2">
            <i className={`fa-solid ${semaforo.icon} text-xl`}></i>
            <div className="min-w-0">
              <p className="text-[11px] font-semibold opacity-70">Estado de carga</p>
              <p className="text-base font-black leading-tight">{semaforo.texto}</p>
            </div>
          </div>
          <div className="mt-3 w-full bg-[#430000]/10 rounded-full h-2 overflow-hidden">
            <div
              className={`h-full transition-all duration-500 ${semaforo.barra}`}
              style={{ width: `${porcentajeCarga}%` }}
            ></div>
          </div>
        </button>

        {indicadores.map((item) => (
          <button
            key={item.etiqueta}
            type="button"
            onClick={irA('analisis')}
            className={`flex items-center gap-3 bg-white border rounded-xl px-4 py-3 shadow-sm text-left hover:border-[#960000]/40 transition-colors ${
              item.destacado ? 'border-[#960000]/40' : 'border-[#430000]/10'
            }`}
          >
            <div
              className={`w-9 h-9 shrink-0 rounded-lg flex items-center justify-center ${
                item.destacado ? 'bg-[#960000] text-white' : 'bg-[#430000]/10 text-[#430000]'
              }`}
            >
              <i className={`fa-solid ${item.icono}`}></i>
            </div>
            <div className="min-w-0">
              <p className="text-[11px] font-semibold text-[#430000]/60">{item.etiqueta}</p>
              <p className="text-lg font-black text-[#430000] leading-tight">{item.valor}</p>
              <p className="text-[10px] text-[#430000]/50">{item.detalle}</p>
            </div>
          </button>
        ))}
      </div>

      {/* AVISOS + PRÓXIMAS ACTIVIDADES */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">

        {/* AVISOS IMPORTANTES */}
        <div className="lg:col-span-5 flex flex-col bg-white border border-[#430000]/10 border-l-4 border-l-[#960000] rounded-xl p-4 shadow-sm">
          <div className="flex items-center gap-2 mb-2">
            <i className="fa-solid fa-bell text-[#960000]"></i>
            <h4 className="text-sm font-bold text-[#430000]">Avisos importantes</h4>
            {avisos.length > 0 && (
              <span className="ml-auto text-[10px] font-bold bg-[#960000] text-white px-2 py-0.5 rounded-full">
                {avisos.length}
              </span>
            )}
          </div>

          {avisos.length === 0 ? (
            <div className="flex items-start gap-2 text-xs text-emerald-800 bg-emerald-50/60 border border-emerald-200 rounded-lg p-3">
              <i className="fa-solid fa-circle-check text-emerald-500 mt-0.5"></i>
              <span>
                No tienes actividades de alta ponderación ni conflictos con tu jornada laboral en los próximos 7 días.
              </span>
            </div>
          ) : (
            <ul className="divide-y divide-[#430000]/10 max-h-72 overflow-y-auto">
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
                    className="flex items-start gap-2.5 py-2.5 first:pt-1 last:pb-1"
                  >
                    <i
                      className={`fa-solid ${
                        esConflicto
                          ? 'fa-triangle-exclamation text-amber-600'
                          : 'fa-circle-exclamation text-[#960000]'
                      } mt-0.5`}
                    ></i>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                        <p className="text-sm font-semibold text-[#430000] break-words leading-tight">
                          {act.nombre}
                        </p>
                        <span
                          className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                            esConflicto
                              ? 'text-amber-800 bg-amber-100'
                              : 'text-[#960000] bg-[#960000]/10'
                          }`}
                        >
                          {esConflicto ? 'Conflicto laboral' : 'Alta ponderación'}
                        </span>
                      </div>
                      <p className="text-[11px] text-[#430000]/70 mt-0.5">{detalle}</p>
                      <p className="text-[11px] font-semibold text-[#960000] mt-0.5">
                        Vence {formatearFechaEntrega(act.fechaReferencia)}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {avisosConflicto.length > 0 && (
            <button
              type="button"
              onClick={irA('jornada')}
              className="mt-3 self-start text-[11px] font-bold text-[#960000] hover:underline"
            >
              Revisar horario laboral
              <i className="fa-solid fa-arrow-right ml-1.5"></i>
            </button>
          )}
        </div>

        {/* PRÓXIMAS ACTIVIDADES */}
        <div className="lg:col-span-7 flex flex-col bg-white border border-[#430000]/10 rounded-xl p-4 shadow-sm">
          <div className="flex items-center gap-2 mb-2">
            <i className="fa-regular fa-calendar text-[#430000]/70"></i>
            <h4 className="text-sm font-bold text-[#430000]">Próximas actividades</h4>
            <span className="ml-auto text-[10px] font-bold text-[#430000]/60">
              {actividades.length === 0
                ? ''
                : `${proximas.length} de ${actividades.length}`}
            </span>
          </div>

          {actividades.length === 0 ? (
            <div className="flex items-center justify-center gap-2 text-xs text-gray-500 bg-[#eeeeee]/60 p-4 rounded-lg text-center">
              <i className="fa-regular fa-calendar-check"></i>
              <span>No hay evaluaciones programadas para los próximos 7 días.</span>
            </div>
          ) : (
            <ul className="divide-y divide-[#430000]/10">
              {proximas.map((act) => {
                const porcentajeTexto = formatearPorcentaje(act.porcentajeEvaluacion);

                return (
                  <li
                    key={obtenerIdActividad(act)}
                    className={`flex items-center gap-3 py-2.5 pl-3 border-l-4 ${
                      act.altaPonderacion ? 'border-l-[#960000]' : 'border-l-transparent'
                    }`}
                  >
                    <span className="font-mono text-[10px] bg-[#430000]/10 text-[#430000] px-2 py-1 rounded-md font-bold shrink-0">
                      {act.materiaInscritaCodigo || 'SACA'}
                    </span>

                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-[#430000] break-words leading-tight">
                        {act.nombre}
                        {act.tieneConflicto && (
                          <i
                            className="fa-solid fa-triangle-exclamation text-amber-600 ml-1.5 text-xs"
                            title="Coincide con tu jornada laboral"
                          ></i>
                        )}
                      </p>
                      <p className="text-[11px] text-[#430000]/60 mt-0.5">
                        {act.tipoActividadNombre || 'Evaluación'} · {formatearFechaEntrega(act.fechaReferencia)}
                      </p>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      {porcentajeTexto && (
                        <span
                          className={`text-[11px] font-bold px-2 py-0.5 rounded-md border ${
                            act.altaPonderacion
                              ? 'bg-[#960000] text-white border-[#960000]'
                              : 'bg-white text-[#430000] border-[#430000]/20'
                          }`}
                        >
                          {porcentajeTexto}
                        </span>
                      )}
                      <span className="text-[11px] font-bold px-2 py-0.5 rounded-md bg-[#960000]/10 text-[#430000] border border-[#430000]/20">
                        {act.tiempoEstimadoHoras} hrs
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}

          {restantes > 0 && (
            <button
              type="button"
              onClick={irA('analisis')}
              className="mt-3 self-start text-[11px] font-bold text-[#960000] hover:underline"
            >
              y {restantes} {restantes === 1 ? 'actividad más' : 'actividades más'} en Análisis de Carga
              <i className="fa-solid fa-arrow-right ml-1.5"></i>
            </button>
          )}
        </div>
      </div>

      {/* ACCESOS RÁPIDOS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {accesos.map((acceso) => (
          <button
            key={acceso.vista}
            type="button"
            onClick={irA(acceso.vista)}
            className="flex items-center gap-3 bg-white border border-[#430000]/10 hover:border-[#960000]/40 rounded-xl px-4 py-3 shadow-sm text-left transition-colors"
          >
            <div className="w-9 h-9 shrink-0 rounded-lg bg-[#430000]/10 text-[#430000] flex items-center justify-center">
              <i className={`fa-solid ${acceso.icono}`}></i>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-bold text-[#430000] leading-tight">{acceso.titulo}</p>
              <p className="text-[11px] text-[#430000]/60">{acceso.detalle}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

export default PanelControlAcademico;
