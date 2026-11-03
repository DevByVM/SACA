import { useState, useEffect, useMemo } from 'react';
import { jornadaService } from '../services/jornadaLaboralService.js';
import { obtenerCargaSemanal } from '../api/cargaApi.js';

// =====================================================================
// Constantes compartidas
// =====================================================================

// Aviso de alta ponderación (Dashboard): porcentaje de evaluación mínimo
export const UMBRAL_ALTA_PONDERACION = 15;

// Escala de la barra de horas; los umbrales coinciden con CargaAcademicaService y la clasificación la decide el backend
export const UMBRAL_HORAS_ELEVADA = 20;
export const UMBRAL_HORAS_CRITICA = 35;
export const HORAS_ESCALA_MAXIMA = 40;

export const NIVELES_CARGA = {
  NORMAL: 'Normal',
  ELEVADA: 'Elevada',
  CRITICA: 'Crítica'
};

// Referencia estable para no recalcular los useMemo mientras no haya datos
const SIN_ACTIVIDADES = [];

const DIAS_TEXTO = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];

// =====================================================================
// Utilidades compartidas (puras)
// =====================================================================

export const obtenerIdActividad = (act) => act.idActividad || act.id;

export const esAltaPonderacion = (act) =>
  Number(act.porcentajeEvaluacion || 0) >= UMBRAL_ALTA_PONDERACION;

// Prioridad: mismas reglas que la tabla de actividades de Gestión Académica
//  Alta:  faltan 2 días o menos, o porcentaje >= 30%
//  Media: faltan 7 días o menos, o porcentaje >= 15%
//  Baja:  las demás
export const obtenerPrioridad = (actividad, ahora = new Date()) => {
  const fechaEntrega = actividad.fechaEntrega ? new Date(actividad.fechaEntrega) : null;

  if (!fechaEntrega || Number.isNaN(fechaEntrega.getTime())) {
    return "Sin fecha";
  }

  const diferenciaMs = fechaEntrega.getTime() - ahora.getTime();
  const diasRestantes = Math.ceil(diferenciaMs / (1000 * 60 * 60 * 24));
  const porcentaje = Number(actividad.porcentajeEvaluacion || 0);

  if (diasRestantes <= 2 || porcentaje >= 30) {
    return "Alta";
  }

  if (diasRestantes <= 7 || porcentaje >= 15) {
    return "Media";
  }

  return "Baja";
};

// Detección de choque de una entrega con la jornada laboral. Devuelve la jornada con la que choca, o null.
// Se revisan todas las jornadas de ese día (puede haber dos turnos) y cada una se trata como [inicio, fin):
// una entrega a la hora de entrada coincide con el trabajo y una a la hora de salida ya no.
export const encontrarJornadaEnConflicto = (fechaEvaluacionStr, jornadas) => {
  if (!fechaEvaluacionStr || !jornadas.length) return null;

  const fechaEval = new Date(fechaEvaluacionStr);
  if (Number.isNaN(fechaEval.getTime())) return null;

  const diaEval = normalizarDia(DIAS_TEXTO[fechaEval.getDay()]);
  // Hora local en formato "HH:MM"
  const horaEval = fechaEval.toTimeString().substring(0, 5);

  return jornadas.find((jornada) =>
    normalizarDia(jornada.diaSemana) === diaEval
    && horaEval >= jornada.horaInicio.substring(0, 5)
    && horaEval < jornada.horaFin.substring(0, 5)
  ) ?? null;
};

// La jornada usa días con tilde («Miércoles») y los horarios de clase el enum sin tilde («MIERCOLES»)
const normalizarDia = (dia) => String(dia || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase();

// Misma regla que ConflictoHorarioService en el backend: mismo día y traslape estricto,
// así que 07:00–08:40 y 08:40–10:20 no chocan
export const horarioChocaConJornada = (horario, jornada) =>
  normalizarDia(horario.diaSemana) === normalizarDia(jornada.diaSemana)
  && horario.horaInicio.substring(0, 5) < jornada.horaFin.substring(0, 5)
  && horario.horaFin.substring(0, 5) > jornada.horaInicio.substring(0, 5);

// Semáforo de la carga general (SDACAS-20): el nivel lo calcula el backend combinando horas y exigencia
// evaluativa. Los textos se conservan porque Panel y Tablero los usan como clave de sus colores.
export const obtenerConfigSemaforo = (nivelGeneral) => {
  if (nivelGeneral === 'CRITICA') {
    return {
      bg: 'bg-rose-50/60 border-rose-200 text-rose-800',
      icon: 'fa-circle-exclamation text-rose-500',
      texto: 'Sobrecarga Crítica',
      mensaje: '¡Alerta! Exceso de carga académica.',
      barra: 'bg-rose-500'
    };
  }

  if (nivelGeneral === 'ELEVADA') {
    return {
      bg: 'bg-amber-50/60 border-amber-200 text-amber-800',
      icon: 'fa-triangle-exclamation text-amber-500',
      texto: 'Carga Elevada',
      mensaje: 'Organiza tus actividades con cuidado.',
      barra: 'bg-amber-500'
    };
  }

  return {
    bg: 'bg-emerald-50/60 border-emerald-200 text-emerald-800',
    icon: 'fa-circle-check text-emerald-500',
    texto: 'Carga Normal',
    mensaje: 'Tu carga académica de los próximos 7 días es completamente manejable.',
    barra: 'bg-emerald-500'
  };
};

// =====================================================================
// Formateadores compartidos
// =====================================================================

const formateadorFecha = new Intl.DateTimeFormat('es-SV', {
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true
});

const formateadorFechaCorta = new Intl.DateTimeFormat('es-SV', {
  day: '2-digit',
  month: '2-digit'
});

export const formatearFechaEntrega = (fechaBase) => {
  const fechaTextoRaw = fechaBase ? formateadorFecha.format(new Date(fechaBase)) : '';
  return fechaTextoRaw
    ? fechaTextoRaw.charAt(0).toUpperCase() + fechaTextoRaw.slice(1)
    : 'Fecha no asignada';
};

// dd/mm
export const formatearFechaCorta = (fecha) => formateadorFechaCorta.format(new Date(fecha));

export const formatearPorcentaje = (valor) => {
  const numero = Number(valor);
  if (!Number.isFinite(numero) || numero <= 0) return null;
  return `${Number.isInteger(numero) ? numero : numero.toFixed(1)}%`;
};

// =====================================================================
// Hook principal: única fuente de datos de carga académica
// =====================================================================

export function useCargaAcademica(estudiante) {
  const estudianteId = estudiante?.id;
  // ESTADOS REALES CONECTADOS A LAS APIS
  const [jornadas, setJornadas] = useState([]);
  const [carga, setCarga] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!estudianteId) return;

    const cargarDatosSaca = async () => {
      try {
        setLoading(true);

        // Las jornadas solo alimentan los avisos de conflicto; la carga la calcula el backend (SDACAS-20)
        const [datosJornadas, datosCarga] = await Promise.all([
          jornadaService.listarPorEstudiante(estudianteId),
          obtenerCargaSemanal()
        ]);
        setJornadas(datosJornadas);
        setCarga(datosCarga);
      } catch (error) {
        console.error("Error al sincronizar el Semáforo Horario SACA:", error);
      } finally {
        setLoading(false);
      }
    };

    cargarDatosSaca();
  }, [estudianteId]);

  // El backend ya devuelve las actividades de la ventana ordenadas por entrega, porcentaje y horas
  const actividadesBase = carga?.actividadesPendientes ?? SIN_ACTIVIDADES;
  const completadasBase = carga?.actividadesNoPendientes ?? SIN_ACTIVIDADES;
  const horasPendientes = Number(carga?.horas?.horasPendientes ?? 0);
  const ventana = useMemo(
    () => (carga ? { desde: new Date(carga.desde), hasta: new Date(carga.hasta) } : null),
    [carga]
  );

  // Actividades enriquecidas con datos derivados (se conserva el orden)
  const [actividades, actividadesCompletadas] = useMemo(() => {
    const ahora = new Date();

    const enriquecer = (act) => {
      const fechaReferencia = act.fechaEntrega || act.fechaInicio;
      const jornadaConflicto = encontrarJornadaEnConflicto(fechaReferencia, jornadas);

      return {
        ...act,
        fechaReferencia,
        prioridad: obtenerPrioridad(act, ahora),
        altaPonderacion: esAltaPonderacion(act),
        jornadaConflicto,
        tieneConflicto: Boolean(jornadaConflicto)
      };
    };

    return [actividadesBase.map(enriquecer), completadasBase.map(enriquecer)];
  }, [actividadesBase, completadasBase, jornadas]);

  const avisosPonderacion = useMemo(
    () => actividades.filter((act) => act.altaPonderacion),
    [actividades]
  );

  const avisosConflicto = useMemo(
    () => actividades.filter((act) => act.tieneConflicto),
    [actividades]
  );

  return {
    cargando: loading && Boolean(estudianteId),
    actividades,
    actividadesCompletadas,
    jornadas,
    horasPendientes,
    ventana,
    semaforo: obtenerConfigSemaforo(carga?.nivelGeneral),
    porcentajeCarga: Math.min((horasPendientes / HORAS_ESCALA_MAXIMA) * 100, 100),
    avisosPonderacion,
    avisosConflicto,
    // SDACAS-20: indicadores independientes y explicación de la clasificación general
    nivelGeneral: carga?.nivelGeneral ?? null,
    indicadorHoras: carga?.horas ?? null,
    exigencia: carga?.exigencia ?? null,
    motivos: carga?.motivos ?? SIN_ACTIVIDADES,
    actividadesSinFecha: carga?.actividadesSinFecha ?? 0,
    actividadesSinPorcentaje: carga?.actividadesSinPorcentaje ?? 0
  };
}
