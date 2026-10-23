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
import Aviso from './ui/Aviso';
import EncabezadoModulo from './ui/EncabezadoModulo';
import EstadoVacio from './ui/EstadoVacio';
import Selector from './ui/Selector';
import Tabla from './ui/Tabla';
import Tarjeta from './ui/Tarjeta';
import { unirClases } from './ui/unirClases';

const formateadorDiaCorto = new Intl.DateTimeFormat('es-SV', { weekday: 'short' });

const capitalizar = (texto) => (texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : texto);

const ESTILOS_PRIORIDAD = {
  'Alta': 'bg-brand-700 text-white border-brand-700',
  'Media': 'bg-warning-50 text-warning-700 border-warning-200',
  'Baja': 'bg-success-50 text-success-700 border-success-200',
  'Sin fecha': 'bg-surface-muted text-ink-600 border-line',
  'Completada': 'bg-success-50 text-success-700 border-success-200'
};


// Colores del semáforo con los tonos de SACA; si el hook cambiara sus textos se usan sus clases originales
const TONOS_SEMAFORO = {
  'Carga Normal': { caja: 'border-success-200 bg-success-50 text-success-700', icono: 'text-success-700', barra: 'bg-success-700' },
  'Carga Elevada': { caja: 'border-warning-200 bg-warning-50 text-warning-700', icono: 'text-warning-700', barra: 'bg-warning-700' },
  'Sobrecarga Crítica': { caja: 'border-danger-200 bg-danger-50 text-danger-700', icono: 'text-danger-700', barra: 'bg-danger-700' }
};
const OPCIONES_PRIORIDAD = ['Alta', 'Media', 'Baja', 'Sin fecha'];

const OPCIONES_ESTADO = [
  { value: 'pendientes', label: 'Pendientes' },
  { value: 'completadas', label: 'Completadas' },
  { value: 'todas', label: 'Todos los estados' }
];

// Una actividad completada ya no compite por prioridad: se muestra y se filtra como «Completada»
const etiquetaPrioridad = (act) => (act.estado === 'COMPLETADA' ? 'Completada' : act.prioridad);

// Análisis de Carga (vista detallada, solo lectura)
// Responde: "¿De qué está compuesta mi carga y cómo la planifico?"
// Las acciones sobre actividades (editar, completar, notas) viven en Gestión Académica.
function TableroCargaSemanal({ estudiante, onNavegar }) {
  const {
    cargando,
    actividades,
    actividadesCompletadas,
    horasPendientes,
    ventana,
    semaforo,
    porcentajeCarga,
    avisosConflicto
  } = useCargaAcademica(estudiante);

  // Filtros de la lista priorizada
  const [filtroMateria, setFiltroMateria] = useState('todas');
  const [filtroEstado, setFiltroEstado] = useState('pendientes');
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

  // Pendientes primero (en su orden de prioridad) y después las completadas de la misma ventana
  const actividadesDeLaSemana = useMemo(
    () => [...actividades, ...actividadesCompletadas],
    [actividades, actividadesCompletadas]
  );

  const materiasDisponibles = useMemo(
    () => [...new Set(actividadesDeLaSemana.map((act) => act.materiaInscritaCodigo || 'Sin materia'))].sort(),
    [actividadesDeLaSemana]
  );

  // Lista priorizada filtrada (mantiene el orden original)
  const actividadesFiltradas = useMemo(
    () =>
      (filtroEstado === 'pendientes'
        ? actividades
        : filtroEstado === 'completadas'
          ? actividadesCompletadas
          : actividadesDeLaSemana
      ).filter((act) => {
        const codigo = act.materiaInscritaCodigo || 'Sin materia';
        if (filtroMateria !== 'todas' && codigo !== filtroMateria) return false;
        if (filtroPrioridad !== 'todas' && etiquetaPrioridad(act) !== filtroPrioridad) return false;
        if (soloConflictos && !act.tieneConflicto) return false;
        return true;
      }),
    [actividades, actividadesCompletadas, actividadesDeLaSemana, filtroEstado, filtroMateria, filtroPrioridad, soloConflictos]
  );

  const irA = (vista) => () => {
    if (onNavegar) onNavegar(vista);
  };

  if (cargando) {
    return (
      <EstadoVacio cargando mensaje="Sincronizando análisis horario de SACA UES..." />
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

  const tonoSemaforo = TONOS_SEMAFORO[semaforo.texto];

  const filasActividades = actividadesFiltradas.map((act) => {
    const porcentajeTexto = formatearPorcentaje(act.porcentajeEvaluacion);

    return {
      id: obtenerIdActividad(act),
      className: act.tieneConflicto ? 'bg-danger-50' : undefined,
      celdas: [
        <span className={`inline-block rounded-md border px-2 py-0.5 text-xs font-bold ${ESTILOS_PRIORIDAD[etiquetaPrioridad(act)] || ESTILOS_PRIORIDAD['Sin fecha']}`}>
          {etiquetaPrioridad(act)}
        </span>,
        <span className="rounded-md bg-surface-muted px-2 py-1 font-mono text-xs font-bold text-ink-900">
          {act.materiaInscritaCodigo || 'SACA'}
        </span>,
        <span>{act.nombre}</span>,
        <span>{act.tipoActividadNombre || 'Evaluación'}</span>,
        <span>{formatearFechaEntrega(act.fechaReferencia)}</span>,
        porcentajeTexto ? (
          <span
            className={`inline-block rounded-md border px-2 py-0.5 font-bold ${
              act.altaPonderacion
                ? 'border-brand-700 bg-brand-700 text-white'
                : 'border-line bg-surface text-ink-900'
            }`}
            title={act.altaPonderacion ? 'Alta ponderación' : undefined}
          >
            {porcentajeTexto}
          </span>
        ) : (
          <span className="text-ink-500">Sin dato</span>
        ),
        <span>{act.tiempoEstimadoHoras ? `${act.tiempoEstimadoHoras} hrs` : 'Sin dato'}</span>,
        act.tieneConflicto ? (
          <span className="inline-flex items-center gap-1 font-semibold text-danger-700">
            <i className="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
            Sí
          </span>
        ) : (
          <span className="text-ink-500">No</span>
        )
      ]
    };
  });

  return (
    <section className="w-full min-w-0 flex-1 space-y-5">

      {/* ENCABEZADO */}
      <EncabezadoModulo
        descripcion={`Próximos 7 días${rangoTexto ? ` · ${rangoTexto}` : ''} · Vista de solo lectura`}
        icono="fa-solid fa-gauge-high"
      />

      {/* SEMÁFORO COMPLETO */}
      <div className={unirClases('rounded-xl border p-4 shadow-sm transition-all duration-300 sm:p-5', tonoSemaforo?.caja ?? semaforo.bg)}>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <i
              className={unirClases('fa-solid text-3xl', tonoSemaforo ? `${tonoSemaforo.icono} ${semaforo.icon.split(' ')[0]}` : semaforo.icon)}
              aria-hidden="true"
            ></i>
            <div>
              <p className="text-xs font-semibold">Estado de carga</p>
              <h2 className="text-xl font-bold leading-tight">{semaforo.texto}</h2>
              <p className="mt-0.5 text-sm">{semaforo.mensaje}</p>
            </div>
          </div>

          <div className="sm:text-right">
            <p className="text-xs font-semibold">Horas pendientes</p>
            <p className="text-3xl font-bold leading-tight">
              {horasPendientes.toFixed(1)} hrs
            </p>
          </div>
        </div>

        {/* BARRA CON UMBRALES */}
        <div className="mt-5">
          <div className="relative">
            <div className="h-3 w-full overflow-hidden rounded-full bg-surface">
              <div
                className={unirClases('h-full transition-all duration-500', tonoSemaforo?.barra ?? semaforo.barra)}
                style={{ width: `${porcentajeCarga}%` }}
              ></div>
            </div>
            <span
              className="absolute top-0 h-3 w-px bg-ink-600"
              style={{ left: `${(UMBRAL_HORAS_ELEVADA / HORAS_ESCALA_MAXIMA) * 100}%` }}
              aria-hidden="true"
            ></span>
            <span
              className="absolute top-0 h-3 w-px bg-ink-600"
              style={{ left: `${(UMBRAL_HORAS_CRITICA / HORAS_ESCALA_MAXIMA) * 100}%` }}
              aria-hidden="true"
            ></span>
          </div>

          <div className="relative mt-1 h-5 text-xs font-bold text-ink-600">
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

        <p className="mt-3 border-t border-ink-900/10 pt-3 text-sm text-ink-900">
          {margenTexto}
        </p>
      </div>

      {/* COMPOSICIÓN DE LA CARGA */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">

        {/* HORAS QUE VENCEN CADA DÍA */}
        <Tarjeta titulo="Horas que vencen cada día" nivelTitulo={2} icono="fa-solid fa-chart-column" className="p-3 sm:p-5">
          {horasPendientes === 0 ? (
            <EstadoVacio icono="fa-regular fa-clock" mensaje="No hay horas pendientes en los próximos 7 días." />
          ) : (
            <>
              {/* En móvil los huecos son mínimos para que las fechas dd/mm quepan en sus 8 columnas */}
              <div className="flex items-end gap-0.5 sm:gap-1.5">
                {horasPorFecha.map((dia, indice) => {
                  const altura = maxHorasDia > 0 ? (dia.horas / maxHorasDia) * 100 : 0;
                  const esMasCargado = diaMasCargado && dia.clave === diaMasCargado.clave;

                  return (
                    <div key={dia.clave} className="flex min-w-0 flex-1 flex-col items-center">
                      <span className="h-5 text-xs font-bold text-ink-900">
                        {dia.horas > 0 ? dia.horas.toFixed(1) : ''}
                      </span>
                      <div className="flex h-28 w-full items-end justify-center">
                        <div
                          className={`w-full max-w-[28px] rounded-t-md ${
                            dia.horas === 0
                              ? 'bg-line'
                              : esMasCargado
                                ? 'bg-brand-700'
                                : 'bg-ink-500'
                          }`}
                          style={{ height: dia.horas === 0 ? '2px' : `${Math.max(altura, 4)}%` }}
                        ></div>
                      </div>
                      <span className="mt-1 text-xs font-semibold text-ink-900">
                        {indice === 0 ? 'Hoy' : capitalizar(formateadorDiaCorto.format(dia.fecha))}
                      </span>
                      <span className="text-xs tracking-tight text-ink-600">
                        {formatearFechaCorta(dia.fecha)}
                      </span>
                    </div>
                  );
                })}
              </div>

              {diaMasCargado && (
                <p className="mt-4 text-sm text-ink-900">
                  <span className="font-bold">Día más cargado:</span>{' '}
                  {capitalizar(formateadorDiaCorto.format(diaMasCargado.fecha))} {formatearFechaCorta(diaMasCargado.fecha)} ({diaMasCargado.horas.toFixed(1)} hrs)
                </p>
              )}
              <p className="mt-1 text-xs text-ink-600">
                Las horas se cuentan en la fecha de entrega, no en el día en que conviene trabajarlas.
              </p>
            </>
          )}
        </Tarjeta>

        {/* HORAS POR MATERIA */}
        <Tarjeta titulo="Horas pendientes por materia" nivelTitulo={2} icono="fa-solid fa-chart-bar" className="p-4 sm:p-5">
          {horasPorMateria.length === 0 ? (
            <EstadoVacio icono="fa-regular fa-folder-open" mensaje="No hay actividades pendientes en los próximos 7 días." />
          ) : (
            <ul className="space-y-3">
              {horasPorMateria.map((materia) => {
                const proporcion = totalHorasMaterias > 0 ? (materia.horas / totalHorasMaterias) * 100 : 0;

                return (
                  <li key={materia.codigo}>
                    <div className="flex flex-wrap items-baseline justify-between gap-x-2 text-sm">
                      <span className="font-bold text-ink-900">
                        {materia.codigo}
                        <span className="font-normal text-ink-600">
                          {' '}· {materia.cantidad} {materia.cantidad === 1 ? 'actividad' : 'actividades'}
                        </span>
                      </span>
                      <span className="font-bold text-ink-900">
                        {materia.horas.toFixed(1)} hrs
                        <span className="font-normal text-ink-600"> · {Math.round(proporcion)}%</span>
                      </span>
                    </div>
                    <div className="mt-1 h-2 w-full overflow-hidden rounded-full bg-surface-muted">
                      <div
                        className="h-full rounded-full bg-brand-700"
                        style={{ width: `${proporcion}%` }}
                      ></div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Tarjeta>
      </div>

      {/* CONFLICTOS CON LA JORNADA LABORAL */}
      <Tarjeta
        titulo="Conflictos con la jornada laboral"
        nivelTitulo={2}
        icono="fa-solid fa-briefcase"
        className="p-4 sm:p-5"
        acciones={
          avisosConflicto.length > 0 && (
            <span className="rounded-full bg-warning-700 px-2 py-0.5 text-xs font-bold text-white">
              {avisosConflicto.length}
            </span>
          )
        }
      >
        {avisosConflicto.length === 0 ? (
          <Aviso tono="exito">
            Ninguna entrega de los próximos 7 días coincide con tu jornada laboral.
          </Aviso>
        ) : (
          <ul className="divide-y divide-line">
            {avisosConflicto.map((act) => (
              <li key={`conflicto-${obtenerIdActividad(act)}`} className="flex items-start gap-2.5 py-3 first:pt-0 last:pb-0">
                <i className="fa-solid fa-triangle-exclamation mt-0.5 text-warning-700" aria-hidden="true"></i>
                <div className="min-w-0 text-sm">
                  <p className="break-words font-semibold text-ink-900">
                    {act.materiaInscritaCodigo || 'SACA'} · {act.nombre}
                  </p>
                  <p className="text-ink-600">
                    Entrega {formatearFechaEntrega(act.fechaReferencia)}, dentro de tu jornada del{' '}
                    {act.jornadaConflicto.diaSemana} ({act.jornadaConflicto.horaInicio.substring(0, 5)} – {act.jornadaConflicto.horaFin.substring(0, 5)}).
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Tarjeta>

      {/* LISTA PRIORIZADA COMPLETA */}
      <Tarjeta
        titulo="Lista priorizada de actividades"
        nivelTitulo={2}
        icono="fa-solid fa-list-ol"
        className="p-4 sm:p-5"
        acciones={
          <span className="text-xs font-semibold text-ink-600">
            {actividadesFiltradas.length} de {actividadesDeLaSemana.length}
          </span>
        }
      >
        <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end">
          <Selector
            label="Materia"
            value={filtroMateria}
            onChange={(e) => setFiltroMateria(e.target.value)}
            options={[
              { value: 'todas', label: 'Todas las materias' },
              ...materiasDisponibles
            ]}
            placeholder={null}
            required={false}
          />

          <Selector
            label="Estado"
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            options={OPCIONES_ESTADO}
            placeholder={null}
            required={false}
          />

          <Selector
            label="Prioridad"
            value={filtroPrioridad}
            onChange={(e) => setFiltroPrioridad(e.target.value)}
            options={[
              { value: 'todas', label: 'Todas las prioridades' },
              ...OPCIONES_PRIORIDAD
            ]}
            placeholder={null}
            required={false}
          />

          <label className="flex min-h-[2.375rem] cursor-pointer items-center gap-2 rounded-lg border border-ink-500 bg-surface px-3 py-2 text-sm text-ink-900 focus-within:ring-2 focus-within:ring-brand-700/20">
            <input
              type="checkbox"
              checked={soloConflictos}
              onChange={(e) => setSoloConflictos(e.target.checked)}
              className="h-4 w-4 accent-brand-700"
            />
            Solo con conflicto
          </label>
        </div>

        {actividadesDeLaSemana.length === 0 ? (
          <EstadoVacio icono="fa-regular fa-calendar-check" mensaje="No hay evaluaciones programadas para los próximos 7 días." />
        ) : actividadesFiltradas.length === 0 ? (
          <EstadoVacio icono="fa-solid fa-filter" mensaje="Ninguna actividad coincide con los filtros seleccionados." />
        ) : (
          <Tabla
            compacta
            anchoTabla="w-full min-w-[760px]"
            columnas={[
              { titulo: 'Prioridad', className: 'whitespace-nowrap' },
              'Materia',
              { titulo: 'Actividad', className: 'font-semibold' },
              { titulo: 'Tipo', className: 'text-ink-600' },
              { titulo: 'Entrega', className: 'whitespace-nowrap' },
              '%',
              { titulo: 'Horas', className: 'whitespace-nowrap font-semibold' },
              'Conflicto'
            ]}
            filas={filasActividades}
          />
        )}
      </Tarjeta>

      {/* ENLACES A OTRAS VISTAS */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {[
          { vista: 'academico', icono: 'fa-book-open', titulo: 'Gestionar actividades', detalle: 'Editar, completar y registrar notas' },
          { vista: 'academico', icono: 'fa-chart-line', titulo: 'Ver rendimiento por materia', detalle: 'Gestión Académica > Actividades' },
          { vista: 'calendario', icono: 'fa-calendar-week', titulo: 'Ver horario de clases', detalle: 'Calendario Académico' }
        ].map((enlace) => (
          <button
            key={enlace.titulo}
            type="button"
            onClick={irA(enlace.vista)}
            className="flex items-center gap-3 rounded-xl border border-line bg-surface px-4 py-3 text-left shadow-sm transition-colors hover:border-brand-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
              <i className={`fa-solid ${enlace.icono}`} aria-hidden="true"></i>
            </div>
            <div className="min-w-0">
              <p className="text-sm font-bold leading-tight text-ink-900">{enlace.titulo}</p>
              <p className="mt-0.5 text-xs text-ink-600">{enlace.detalle}</p>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}

export default TableroCargaSemanal;
