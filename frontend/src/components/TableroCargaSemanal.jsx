import React, { useState, useMemo } from 'react';
import {
  useCargaAcademica,
  obtenerIdActividad,
  formatearFechaEntrega,
  formatearFechaCorta,
  formatearPorcentaje,
  UMBRAL_HORAS_ELEVADA,
  UMBRAL_HORAS_CRITICA,
  HORAS_ESCALA_MAXIMA
} from '../hooks/useCargaAcademica.js';

const formateadorDiaCorto = new Intl.DateTimeFormat('es-SV', { weekday: 'short' });

const capitalizar = (texto) => (texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : texto);

const ESTILOS_PRIORIDAD = {
  'Alta': 'bg-[#960000] text-white border-[#960000]',
  'Media': 'bg-amber-100 text-amber-800 border-amber-200',
  'Baja': 'bg-emerald-50 text-emerald-800 border-emerald-200',
  'Sin fecha': 'bg-gray-100 text-gray-600 border-gray-200'
};

const OPCIONES_PRIORIDAD = ['Alta', 'Media', 'Baja', 'Sin fecha'];

// Análisis de Carga (vista detallada, solo lectura)
// Responde: "¿De qué está compuesta mi carga y cómo la planifico?"
// Las acciones sobre actividades (editar, completar, notas) viven en Gestión Académica.
function TableroCargaSemanal({ estudiante, onNavegar }) {
  const {
    cargando,
    actividades,
    horasPendientes,
    ventana,
    semaforo,
    porcentajeCarga,
    avisosConflicto
  } = useCargaAcademica(estudiante);

  // Filtros de la lista priorizada
  const [filtroMateria, setFiltroMateria] = useState('todas');
  const [filtroPrioridad, setFiltroPrioridad] = useState('todas');
  const [soloConflictos, setSoloConflictos] = useState(false);

  // Horas que vencen por fecha calendario: hoy + 7 días = 8 fechas
  const horasPorFecha = useMemo(() => {
    if (!ventana) return [];

    const claveDe = (fecha) => `${fecha.getFullYear()}-${fecha.getMonth()}-${fecha.getDate()}`;

    const inicio = new Date(ventana.desde);
    inicio.setHours(0, 0, 0, 0);
    const fin = new Date(ventana.hasta);
    fin.setHours(0, 0, 0, 0);

    const dias = [];
    for (let d = new Date(inicio); d <= fin; d.setDate(d.getDate() + 1)) {
      dias.push({ fecha: new Date(d), clave: claveDe(d), horas: 0 });
    }

    actividades.forEach((act) => {
      const referencia = act.fechaReferencia ? new Date(act.fechaReferencia) : null;
      if (!referencia || Number.isNaN(referencia.getTime())) return;

      const dia = dias.find((item) => item.clave === claveDe(referencia));
      if (dia) dia.horas += Number(act.tiempoEstimadoHoras || 0);
    });

    return dias;
  }, [ventana, actividades]);

  // Horas pendientes por materia
  const horasPorMateria = useMemo(() => {
    const grupos = new Map();

    actividades.forEach((act) => {
      const codigo = act.materiaInscritaCodigo || 'Sin materia';
      const grupo = grupos.get(codigo) || { codigo, horas: 0, cantidad: 0 };
      grupo.horas += Number(act.tiempoEstimadoHoras || 0);
      grupo.cantidad += 1;
      grupos.set(codigo, grupo);
    });

    return [...grupos.values()].sort((a, b) => b.horas - a.horas);
  }, [actividades]);

  const materiasDisponibles = useMemo(
    () => [...new Set(actividades.map((act) => act.materiaInscritaCodigo || 'Sin materia'))].sort(),
    [actividades]
  );

  // Lista priorizada filtrada (mantiene el orden original)
  const actividadesFiltradas = useMemo(
    () =>
      actividades.filter((act) => {
        const codigo = act.materiaInscritaCodigo || 'Sin materia';
        if (filtroMateria !== 'todas' && codigo !== filtroMateria) return false;
        if (filtroPrioridad !== 'todas' && act.prioridad !== filtroPrioridad) return false;
        if (soloConflictos && !act.tieneConflicto) return false;
        return true;
      }),
    [actividades, filtroMateria, filtroPrioridad, soloConflictos]
  );

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

  const rangoTexto = ventana
    ? `${formatearFechaCorta(ventana.desde)} – ${formatearFechaCorta(ventana.hasta)}`
    : '';

  const margenTexto =
    horasPendientes > UMBRAL_HORAS_CRITICA
      ? `Superas el umbral crítico (${UMBRAL_HORAS_CRITICA} hrs) por ${(horasPendientes - UMBRAL_HORAS_CRITICA).toFixed(1)} hrs.`
      : horasPendientes > UMBRAL_HORAS_ELEVADA
        ? `Te quedan ${(UMBRAL_HORAS_CRITICA - horasPendientes).toFixed(1)} hrs antes de Sobrecarga Crítica (más de ${UMBRAL_HORAS_CRITICA} hrs).`
        : `Te quedan ${(UMBRAL_HORAS_ELEVADA - horasPendientes).toFixed(1)} hrs antes de Carga Elevada (más de ${UMBRAL_HORAS_ELEVADA} hrs).`;

  const maxHorasDia = Math.max(...horasPorFecha.map((dia) => dia.horas), 0);
  const diaMasCargado = maxHorasDia > 0 ? horasPorFecha.find((dia) => dia.horas === maxHorasDia) : null;
  const totalHorasMaterias = horasPorMateria.reduce((suma, materia) => suma + materia.horas, 0);

  return (
    <div className="w-full flex-1 min-w-0 space-y-4 bg-[#eeeeee] p-4 sm:p-5 rounded-xl">

      {/* ENCABEZADO */}
      <div className="flex items-center gap-3 border-b border-[#430000]/20 pb-3">
        <div className="w-9 h-9 shrink-0 rounded-lg bg-[#430000] text-white flex items-center justify-center">
          <i className="fa-solid fa-gauge-high"></i>
        </div>
        <div>
          <h3 className="text-xl font-bold text-[#430000] leading-tight">
            Análisis de Carga Académica
          </h3>
          <p className="text-xs text-[#430000]/60">
            Próximos 7 días{rangoTexto && ` · ${rangoTexto}`} · Vista de solo lectura
          </p>
        </div>
      </div>

      {/* SEMÁFORO COMPLETO */}
      <div className={`border p-5 rounded-xl shadow-sm transition-all duration-300 ${semaforo.bg}`}>
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div className="flex items-center gap-3">
            <i className={`fa-solid ${semaforo.icon} text-3xl`}></i>
            <div>
              <span className="text-xs font-semibold opacity-70">Estado de carga</span>
              <h4 className="text-xl font-black leading-tight">{semaforo.texto}</h4>
              <p className="text-xs mt-0.5">{semaforo.mensaje}</p>
            </div>
          </div>

          <div className="sm:text-right">
            <span className="text-xs opacity-70 block">Horas pendientes</span>
            <span className="text-3xl font-black leading-tight">
              {horasPendientes.toFixed(1)} hrs
            </span>
          </div>
        </div>

        {/* BARRA CON UMBRALES */}
        <div className="mt-5">
          <div className="relative">
            <div className="w-full bg-[#430000]/10 rounded-full h-3 overflow-hidden">
              <div
                className={`h-full transition-all duration-500 ${semaforo.barra}`}
                style={{ width: `${porcentajeCarga}%` }}
              ></div>
            </div>
            <span
              className="absolute top-0 h-3 w-px bg-[#430000]/50"
              style={{ left: `${(UMBRAL_HORAS_ELEVADA / HORAS_ESCALA_MAXIMA) * 100}%` }}
            ></span>
            <span
              className="absolute top-0 h-3 w-px bg-[#430000]/50"
              style={{ left: `${(UMBRAL_HORAS_CRITICA / HORAS_ESCALA_MAXIMA) * 100}%` }}
            ></span>
          </div>

          <div className="relative h-4 mt-1 text-[10px] font-bold text-[#430000]/60">
            <span className="absolute left-0">0</span>
            <span
              className="absolute -translate-x-1/2"
              style={{ left: `${(UMBRAL_HORAS_ELEVADA / HORAS_ESCALA_MAXIMA) * 100}%` }}
            >
              {UMBRAL_HORAS_ELEVADA}
            </span>
            <span
              className="absolute -translate-x-1/2"
              style={{ left: `${(UMBRAL_HORAS_CRITICA / HORAS_ESCALA_MAXIMA) * 100}%` }}
            >
              {UMBRAL_HORAS_CRITICA}
            </span>
            <span className="absolute right-0">{HORAS_ESCALA_MAXIMA}</span>
          </div>
        </div>

        <p className="text-xs mt-3 border-t border-[#430000]/20 pt-3 text-[#430000]">
          {margenTexto}
        </p>
      </div>

      {/* COMPOSICIÓN DE LA CARGA */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

        {/* HORAS QUE VENCEN CADA DÍA */}
        <div className="bg-white border border-[#430000]/10 rounded-xl p-4 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <i className="fa-solid fa-chart-column text-[#430000]/70"></i>
            <h4 className="text-sm font-bold text-[#430000]">Horas que vencen cada día</h4>
          </div>

          {horasPendientes === 0 ? (
            <p className="text-xs text-gray-500 bg-[#eeeeee]/60 p-4 rounded-lg text-center">
              No hay horas pendientes en los próximos 7 días.
            </p>
          ) : (
            <>
              <div className="flex items-end gap-1.5">
                {horasPorFecha.map((dia, indice) => {
                  const altura = maxHorasDia > 0 ? (dia.horas / maxHorasDia) * 100 : 0;
                  const esMasCargado = diaMasCargado && dia.clave === diaMasCargado.clave;

                  return (
                    <div key={dia.clave} className="flex-1 min-w-0 flex flex-col items-center">
                      <span className="text-[10px] font-bold text-[#430000] h-4">
                        {dia.horas > 0 ? dia.horas.toFixed(1) : ''}
                      </span>
                      <div className="w-full h-28 flex items-end justify-center">
                        <div
                          className={`w-full max-w-[28px] rounded-t-md ${
                            dia.horas === 0
                              ? 'bg-[#430000]/10'
                              : esMasCargado
                                ? 'bg-[#960000]'
                                : 'bg-[#430000]/70'
                          }`}
                          style={{ height: dia.horas === 0 ? '2px' : `${Math.max(altura, 4)}%` }}
                        ></div>
                      </div>
                      <span className="text-[10px] font-semibold text-[#430000]/80 mt-1">
                        {indice === 0 ? 'Hoy' : capitalizar(formateadorDiaCorto.format(dia.fecha))}
                      </span>
                      <span className="text-[9px] text-[#430000]/50">
                        {formatearFechaCorta(dia.fecha)}
                      </span>
                    </div>
                  );
                })}
              </div>

              {diaMasCargado && (
                <p className="text-[11px] text-[#430000] mt-3">
                  <span className="font-bold">Día más cargado:</span>{' '}
                  {capitalizar(formateadorDiaCorto.format(diaMasCargado.fecha))} {formatearFechaCorta(diaMasCargado.fecha)} ({diaMasCargado.horas.toFixed(1)} hrs)
                </p>
              )}
              <p className="text-[10px] text-[#430000]/50 mt-1">
                Las horas se cuentan en la fecha de entrega, no en el día en que conviene trabajarlas.
              </p>
            </>
          )}
        </div>

        {/* HORAS POR MATERIA */}
        <div className="bg-white border border-[#430000]/10 rounded-xl p-4 shadow-sm">
          <div className="flex items-center gap-2 mb-3">
            <i className="fa-solid fa-chart-bar text-[#430000]/70"></i>
            <h4 className="text-sm font-bold text-[#430000]">Horas pendientes por materia</h4>
          </div>

          {horasPorMateria.length === 0 ? (
            <p className="text-xs text-gray-500 bg-[#eeeeee]/60 p-4 rounded-lg text-center">
              No hay actividades pendientes en los próximos 7 días.
            </p>
          ) : (
            <ul className="space-y-3">
              {horasPorMateria.map((materia) => {
                const proporcion = totalHorasMaterias > 0 ? (materia.horas / totalHorasMaterias) * 100 : 0;

                return (
                  <li key={materia.codigo}>
                    <div className="flex items-baseline justify-between gap-2 text-xs">
                      <span className="font-bold text-[#430000]">
                        {materia.codigo}
                        <span className="font-normal text-[#430000]/60">
                          {' '}· {materia.cantidad} {materia.cantidad === 1 ? 'actividad' : 'actividades'}
                        </span>
                      </span>
                      <span className="font-bold text-[#430000]">
                        {materia.horas.toFixed(1)} hrs
                        <span className="font-normal text-[#430000]/60"> · {Math.round(proporcion)}%</span>
                      </span>
                    </div>
                    <div className="mt-1 w-full bg-[#430000]/10 rounded-full h-2 overflow-hidden">
                      <div
                        className="h-full bg-[#960000] rounded-full"
                        style={{ width: `${proporcion}%` }}
                      ></div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>

      {/* CONFLICTOS CON LA JORNADA LABORAL */}
      <div className="bg-white border border-[#430000]/10 rounded-xl p-4 shadow-sm">
        <div className="flex items-center gap-2 mb-2">
          <i className="fa-solid fa-briefcase text-[#430000]/70"></i>
          <h4 className="text-sm font-bold text-[#430000]">Conflictos con la jornada laboral</h4>
          {avisosConflicto.length > 0 && (
            <span className="ml-auto text-[10px] font-bold bg-amber-500 text-white px-2 py-0.5 rounded-full">
              {avisosConflicto.length}
            </span>
          )}
        </div>

        {avisosConflicto.length === 0 ? (
          <div className="flex items-center gap-2 text-xs text-emerald-800 bg-emerald-50/60 border border-emerald-200 rounded-lg p-3">
            <i className="fa-solid fa-circle-check text-emerald-500"></i>
            <span>Ninguna entrega de los próximos 7 días coincide con tu jornada laboral.</span>
          </div>
        ) : (
          <ul className="divide-y divide-[#430000]/10">
            {avisosConflicto.map((act) => (
              <li key={`conflicto-${obtenerIdActividad(act)}`} className="flex items-start gap-2.5 py-2 first:pt-0 last:pb-0">
                <i className="fa-solid fa-triangle-exclamation text-amber-600 mt-0.5"></i>
                <div className="min-w-0 text-xs">
                  <p className="font-semibold text-[#430000] break-words">
                    {act.materiaInscritaCodigo || 'SACA'} · {act.nombre}
                  </p>
                  <p className="text-[#430000]/70">
                    Entrega {formatearFechaEntrega(act.fechaReferencia)}, dentro de tu jornada del{' '}
                    {act.jornadaConflicto.diaSemana} ({act.jornadaConflicto.horaInicio.substring(0, 5)} – {act.jornadaConflicto.horaFin.substring(0, 5)}).
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* LISTA PRIORIZADA COMPLETA */}
      <div className="bg-white border border-[#430000]/10 rounded-xl p-4 shadow-sm space-y-3">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3">
          <div className="flex items-center gap-2">
            <i className="fa-solid fa-list-ol text-[#430000]/70"></i>
            <h4 className="text-sm font-bold text-[#430000]">Lista priorizada de actividades</h4>
            <span className="text-[11px] text-[#430000]/60">
              {actividadesFiltradas.length} de {actividades.length}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-2 text-xs">
            <select
              value={filtroMateria}
              onChange={(e) => setFiltroMateria(e.target.value)}
              className="border border-[#430000]/20 rounded-lg px-2.5 py-1.5 bg-white text-[#430000]"
              aria-label="Filtrar por materia"
            >
              <option value="todas">Todas las materias</option>
              {materiasDisponibles.map((codigo) => (
                <option key={codigo} value={codigo}>{codigo}</option>
              ))}
            </select>

            <select
              value={filtroPrioridad}
              onChange={(e) => setFiltroPrioridad(e.target.value)}
              className="border border-[#430000]/20 rounded-lg px-2.5 py-1.5 bg-white text-[#430000]"
              aria-label="Filtrar por prioridad"
            >
              <option value="todas">Todas las prioridades</option>
              {OPCIONES_PRIORIDAD.map((prioridad) => (
                <option key={prioridad} value={prioridad}>{prioridad}</option>
              ))}
            </select>

            <label className="flex items-center gap-1.5 border border-[#430000]/20 rounded-lg px-2.5 py-1.5 text-[#430000] cursor-pointer">
              <input
                type="checkbox"
                checked={soloConflictos}
                onChange={(e) => setSoloConflictos(e.target.checked)}
              />
              Solo con conflicto
            </label>
          </div>
        </div>

        {actividades.length === 0 ? (
          <div className="flex items-center justify-center gap-2 text-xs text-gray-500 bg-[#eeeeee]/60 p-4 rounded-lg text-center">
            <i className="fa-regular fa-calendar-check"></i>
            <span>No hay evaluaciones programadas para los próximos 7 días.</span>
          </div>
        ) : actividadesFiltradas.length === 0 ? (
          <p className="text-xs text-gray-500 bg-[#eeeeee]/60 p-4 rounded-lg text-center">
            Ninguna actividad coincide con los filtros seleccionados.
          </p>
        ) : (
          <div className="w-full overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-xs">
              <thead className="bg-[#430000]/10 text-[#430000]">
                <tr>
                  <th className="px-2 py-2">Prioridad</th>
                  <th className="px-2 py-2">Materia</th>
                  <th className="px-2 py-2">Actividad</th>
                  <th className="px-2 py-2">Tipo</th>
                  <th className="px-2 py-2">Entrega</th>
                  <th className="px-2 py-2">%</th>
                  <th className="px-2 py-2">Horas</th>
                  <th className="px-2 py-2">Conflicto</th>
                </tr>
              </thead>
              <tbody>
                {actividadesFiltradas.map((act) => {
                  const porcentajeTexto = formatearPorcentaje(act.porcentajeEvaluacion);

                  return (
                    <tr
                      key={obtenerIdActividad(act)}
                      className={`border-b border-[#430000]/10 ${act.tieneConflicto ? 'bg-rose-50/40' : ''}`}
                    >
                      <td className="px-2 py-2">
                        <span className={`inline-block text-[10px] font-bold px-2 py-0.5 rounded-md border ${ESTILOS_PRIORIDAD[act.prioridad] || ESTILOS_PRIORIDAD['Sin fecha']}`}>
                          {act.prioridad}
                        </span>
                      </td>
                      <td className="px-2 py-2">
                        <span className="font-mono text-[10px] bg-[#430000]/10 text-[#430000] px-2 py-1 rounded-md font-bold">
                          {act.materiaInscritaCodigo || 'SACA'}
                        </span>
                      </td>
                      <td className="px-2 py-2 font-semibold text-[#430000]">{act.nombre}</td>
                      <td className="px-2 py-2 text-[#430000]/70">{act.tipoActividadNombre || 'Evaluación'}</td>
                      <td className="px-2 py-2 whitespace-nowrap text-[#430000]/80">
                        {formatearFechaEntrega(act.fechaReferencia)}
                      </td>
                      <td className="px-2 py-2">
                        {porcentajeTexto ? (
                          <span
                            className={`inline-block font-bold px-2 py-0.5 rounded-md border ${
                              act.altaPonderacion
                                ? 'bg-[#960000] text-white border-[#960000]'
                                : 'bg-white text-[#430000] border-[#430000]/20'
                            }`}
                            title={act.altaPonderacion ? 'Alta ponderación' : undefined}
                          >
                            {porcentajeTexto}
                          </span>
                        ) : (
                          <span className="text-[#430000]/50">Sin dato</span>
                        )}
                      </td>
                      <td className="px-2 py-2 whitespace-nowrap font-semibold text-[#430000]">
                        {act.tiempoEstimadoHoras ? `${act.tiempoEstimadoHoras} hrs` : 'Sin dato'}
                      </td>
                      <td className="px-2 py-2">
                        {act.tieneConflicto ? (
                          <span className="inline-flex items-center gap-1 text-rose-700 font-semibold">
                            <i className="fa-solid fa-triangle-exclamation"></i>
                            Sí
                          </span>
                        ) : (
                          <span className="text-[#430000]/50">No</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ENLACES A OTRAS VISTAS */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        {[
          { vista: 'academico', icono: 'fa-book-open', titulo: 'Gestionar actividades', detalle: 'Editar, completar y registrar notas' },
          { vista: 'academico', icono: 'fa-chart-line', titulo: 'Ver rendimiento por materia', detalle: 'Gestión Académica > Actividades' },
          { vista: 'calendario', icono: 'fa-calendar-week', titulo: 'Ver horario de clases', detalle: 'Calendario Académico' }
        ].map((enlace) => (
          <button
            key={enlace.titulo}
            type="button"
            onClick={irA(enlace.vista)}
            className="flex items-center gap-3 bg-white border border-[#430000]/10 hover:border-[#960000]/40 rounded-xl px-4 py-3 shadow-sm text-left transition-colors"
          >
            <div className="w-9 h-9 shrink-0 rounded-lg bg-[#430000]/10 text-[#430000] flex items-center justify-center">
              <i className={`fa-solid ${enlace.icono}`}></i>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-bold text-[#430000] leading-tight">{enlace.titulo}</p>
              <p className="text-[11px] text-[#430000]/60">{enlace.detalle}</p>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

export default TableroCargaSemanal;
