import { useState, useEffect, useMemo } from 'react';
import { jornadaService } from '../services/jornadaLaboralService.js';
import { obtenerPorCicloActivoYEstdiante } from '../services/actividadService.js';

// =====================================================================
// Constantes compartidas
// =====================================================================

// Aviso de alta ponderación (Dashboard): porcentaje de evaluación mínimo
export const UMBRAL_ALTA_PONDERACION = 15;

// Semáforo de carga (horas pendientes)
export const UMBRAL_HORAS_ELEVADA = 20;
export const UMBRAL_HORAS_CRITICA = 35;
export const HORAS_ESCALA_MAXIMA = 40;

const DIAS_TEXTO = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];

// =====================================================================
// Utilidades compartidas (puras)
// =====================================================================

// Una actividad es "próxima" si su fecha de referencia cae entre el momento actual y exactamente 7 días después.
// Usa fechaEntrega; si no existe, usa fechaInicio. Si ambas faltan, se excluye.
const estaEnProximosDias = (act, desde, hasta) => {
  const fechaReferencia = act.fechaEntrega
    ? new Date(act.fechaEntrega)
    : act.fechaInicio
      ? new Date(act.fechaInicio)
      : null;

  if (!fechaReferencia) return false;

  return fechaReferencia >= desde && fechaReferencia <= hasta;
};

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

// Detección de choque con la jornada laboral (misma lógica de verificarChoqueHorario).
// Devuelve la jornada con la que choca, o null si no hay choque.
export const encontrarJornadaEnConflicto = (fechaEvaluacionStr, jornadas) => {
  if (!fechaEvaluacionStr || !jornadas.length) return null;

  const fechaEval = new Date(fechaEvaluacionStr);
  const diaEvalTexto = DIAS_TEXTO[fechaEval.getDay()];

  // Extraemos la hora en formato "HH:MM"
  const horaEval = fechaEval.toTimeString().split(' ')[0].substring(0, 5);

  // Buscamos si trabaja ese día
  const jornadaDeEseDia = jornadas.find(j => j.diaSemana.toLowerCase() === diaEvalTexto.toLowerCase());

  if (jornadaDeEseDia) {
    const trabajaDesde = jornadaDeEseDia.horaInicio.substring(0, 5);
    const trabajaHasta = jornadaDeEseDia.horaFin.substring(0, 5);

    if (horaEval >= trabajaDesde && horaEval <= trabajaHasta) {
      return jornadaDeEseDia; // Hay conflicto con el horario laboral
    }
  }
  return null;
};

// Semáforo de carga: umbrales <= 20 normal, > 20 y <= 35 elevada, > 35 crítica
export const obtenerConfigSemaforo = (horasPendientes) => {
  let configSemaforo = {
    bg: 'bg-emerald-50/60 border-emerald-200 text-emerald-800',
    icon: 'fa-circle-check text-emerald-500',
    texto: 'Carga Normal',
    mensaje: 'Tu carga académica de los próximos 7 días es completamente manejable.',
    barra: 'bg-emerald-500'
  };

  if (horasPendientes > UMBRAL_HORAS_ELEVADA && horasPendientes <= UMBRAL_HORAS_CRITICA) {
    configSemaforo = {
      bg: 'bg-amber-50/60 border-amber-200 text-amber-800',
      icon: 'fa-triangle-exclamation text-amber-500',
      texto: 'Carga Elevada',
      mensaje: 'Organiza tus actividades con cuidado.',
      barra: 'bg-amber-500'
    };
  } else if (horasPendientes > UMBRAL_HORAS_CRITICA) {
    configSemaforo = {
      bg: 'bg-rose-50/60 border-rose-200 text-rose-800',
      icon: 'fa-circle-exclamation text-rose-500',
      texto: 'Sobrecarga Crítica',
      mensaje: '¡Alerta! Exceso de carga académica.',
      barra: 'bg-rose-500'
    };
  }

  return configSemaforo;
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
  const [actividadesBase, setActividadesBase] = useState([]);
  const [horasPendientes, setHorasPendientes] = useState(0);
  const [ventana, setVentana] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!estudianteId) return; 

    const cargarDatosSaca = async () => {
      try {
        setLoading(true);

        // 1. Consume API de jornadas
        const datosJornadas = await jornadaService.listarPorEstudiante(estudianteId);
        setJornadas(datosJornadas);

        // 2. Consume la API 
        const datosActividades = await obtenerPorCicloActivoYEstdiante(estudianteId);
        
        const hoy = new Date();
        hoy.setHours(0, 0, 0, 0);

        // Ventana de los próximos 7 días: desde este momento hasta exactamente 7 días después
        const ahora = new Date();
        const limiteProximosDias = new Date(ahora);
        limiteProximosDias.setDate(limiteProximosDias.getDate() + 7);

        // Se consideran solo actividades pendientes con entrega en los próximos 7 días
        const actividadesPendientes = datosActividades.filter((act) => {
          const estado = act.estado || "ACTIVO";
          const fechaEntrega = act.fechaEntrega ? new Date(act.fechaEntrega) : null;

          const noEstaCompletada = estado !== "COMPLETADA";
          const aunNoVence = fechaEntrega ? fechaEntrega >= hoy : true;
          const estaEnProximosSieteDias = estaEnProximosDias(act, ahora, limiteProximosDias);

          return noEstaCompletada && aunNoVence && estaEnProximosSieteDias;
        });

        // Se ordenan por prioridad: entrega más cercana, mayor porcentaje y más horas estimadas
        const actividadesOrdenadas = [...actividadesPendientes].sort((a, b) => {
          const fechaA = a.fechaEntrega ? new Date(a.fechaEntrega).getTime() : Infinity;
          const fechaB = b.fechaEntrega ? new Date(b.fechaEntrega).getTime() : Infinity;

          if (fechaA !== fechaB) {
            return fechaA - fechaB;
          }

          const porcentajeA = Number(a.porcentajeEvaluacion || 0);
          const porcentajeB = Number(b.porcentajeEvaluacion || 0);

          if (porcentajeA !== porcentajeB) {
            return porcentajeB - porcentajeA;
          }

          const horasA = Number(a.tiempoEstimadoHoras || 0);
          const horasB = Number(b.tiempoEstimadoHoras || 0);

          return horasB - horasA;
        });

        setActividadesBase(actividadesOrdenadas);
        setVentana({ desde: ahora, hasta: limiteProximosDias });

        // Suma total de horas pendientes de los próximos 7 días
        const totalHrs = actividadesOrdenadas.reduce(
          (sum, act) => sum + Number(act.tiempoEstimadoHoras || 0),
          0
        );

        setHorasPendientes(totalHrs);

      } catch (error) {
        console.error("Error al sincronizar el Semáforo Horario SACA:", error);
      } finally {
        setLoading(false);
      }
    };

    cargarDatosSaca();
  }, [estudianteId]);

  // Actividades enriquecidas con datos derivados (se conserva el orden)
  const actividades = useMemo(() => {
    const ahora = new Date();

    return actividadesBase.map((act) => {
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
    });
  }, [actividadesBase, jornadas]);

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
    jornadas,
    horasPendientes,
    ventana,
    semaforo: obtenerConfigSemaforo(horasPendientes),
    porcentajeCarga: Math.min((horasPendientes / HORAS_ESCALA_MAXIMA) * 100, 100),
    avisosPonderacion,
    avisosConflicto
  };
}
