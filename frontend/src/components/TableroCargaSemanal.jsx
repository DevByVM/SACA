import React, { useState, useEffect } from 'react';
import { jornadaService } from '../services/jornadaLaboralService.js';
import { obtenerPorCicloActivoYEstdiante } from '../services/actividadService.js';

function TableroCargaSemanal({ estudiante }) {
  const estudianteId = estudiante?.id;
  // ESTADOS REALES CONECTADOS A LAS APIS
  const [jornadas, setJornadas] = useState([]);
  const [actividades, setActividades] = useState([]);
  const [horasPendientes, setHorasPendientes] = useState(0);
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

        // Solamente dejamos pasar las actividades que sean de HOY en adelante
        const actividadesFuturas = datosActividades.filter(act => {
          return act.fechaInicio ? new Date(act.fechaInicio) >= hoy : true;
        });

        // CRITERIO JIRA 3: Ordenar cronológicamente (las más cercanas primero)
        const actividadesOrdenadas = actividadesFuturas.sort((a, b) => {
          return new Date(a.fechaInicio) - new Date(b.fechaInicio);
        });
        
        setActividades(actividadesOrdenadas);

        //  Calculamos la suma de las horas estimadas SOLO una vez
        const totalHrs = actividadesOrdenadas.reduce((sum, act) => sum + (act.tiempoEstimatedHoras || act.tiempoEstimadoHoras || 0), 0);
        setHorasPendientes(totalHrs);

      } catch (error) {
        console.error("Error al sincronizar el Semáforo Horario SACA:", error);
      } finally {
        setLoading(false);
      }
    };

    cargarDatosSaca();
  }, [estudianteId]);

  // verificarChoqueDeHorario()
  const verificarChoqueHorario = (fechaEvaluacionStr) => {
    if (!fechaEvaluacionStr || !jornadas.length) return false;

    const fechaEval = new Date(fechaEvaluacionStr);
    const diasTexto = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];
    const diaEvalTexto = diasTexto[fechaEval.getDay()]; 

    // Extraemos la hora en formato "HH:MM"
    const horaEval = fechaEval.toTimeString().split(' ')[0].substring(0, 5); 

    // Buscamos si trabaja ese día
    const jornadaDeEseDia = jornadas.find(j => j.diaSemana.toLowerCase() === diaEvalTexto.toLowerCase());

    if (jornadaDeEseDia) {
      const trabajaDesde = jornadaDeEseDia.horaInicio.substring(0, 5);
      const trabajaHasta = jornadaDeEseDia.horaFin.substring(0, 5);

      if (horaEval >= trabajaDesde && horaEval <= trabajaHasta) {
        return true; // ¡Hay conflicto con tu horario laboral!
      }
    }
    return false;
  };

  // LÓGICA DINÁMICA DEL COLOR DEL SEMÁFORO
  let configSemaforo = {
    bg: 'bg-emerald-50/60 border-emerald-200 text-emerald-800',
    icon: 'fa-circle-check text-emerald-500',
    texto: 'Carga Normal',
    mensaje: 'Tu carga académica esta semana es completamente manejable.'
  };

  if (horasPendientes > 20 && horasPendientes <= 35) {
    configSemaforo = {
      bg: 'bg-amber-50/60 border-amber-200 text-amber-800',
      icon: 'fa-triangle-exclamation text-amber-500',
      texto: 'Carga Elevada',
      mensaje: 'Organiza tus actividades con cuidado.'
    };
  } else if (horasPendientes > 35) {
    configSemaforo = {
      bg: 'bg-rose-50/60 border-rose-200 text-rose-800',
      icon: 'fa-circle-exclamation text-rose-500',
      texto: 'Sobrecarga Crítica',
      mensaje: '¡Alerta! Exceso de carga académica.'
    };
  }

  const porcentaje = Math.min((horasPendientes / 40) * 100, 100);

  if (loading && estudianteId) {
    return (
      <div className="p-8 text-center text-[#430000] font-medium text-sm animate-pulse">
        Sincronizando análisis horario de SACA UES...
      </div>
    );
  }

  return (
    <div className="space-y-6 bg-[#eeeeee] p-4 rounded-xl">

      {/* HEADER */}
      <div className="border-b border-[#430000]/20 pb-4">
        <h3 className="text-xl font-bold text-[#430000]">
          Análisis de Carga Académica 
        </h3>
        <p className="text-xs text-[#430000]/60 mt-1">
          Carga académica en tiempo real
        </p>
      </div>

      {/* SEMÁFORO */}
      <div className={`border p-5 rounded-xl shadow-sm transition-all duration-300 ${configSemaforo.bg}`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <i className={`fa-solid ${configSemaforo.icon} text-2xl`}></i>
            <div>
              <span className="text-xs uppercase font-bold opacity-70">
                Estado del Estudiante
              </span>
              <h4 className="text-lg font-black">
                {configSemaforo.texto}
              </h4>
            </div>
          </div>

          <div className="text-right">
            <span className="text-xs opacity-70 block">Horas</span>
            <span className="text-2xl font-black">
              {horasPendientes.toFixed(1)} hrs
            </span>
          </div>
        </div>

        {/* PROGRESO */}
        <div className="mt-4">
          <div className="w-full bg-[#430000]/10 rounded-full h-2.5 overflow-hidden">
            <div
              className={`h-full transition-all duration-500 ${
                horasPendientes > 35 ? 'bg-rose-500' : horasPendientes > 20 ? 'bg-amber-500' : 'bg-emerald-500'
              }`}
              style={{ width: `${porcentaje}%` }}
            ></div>
          </div>
          <div className="flex justify-between text-[10px] font-bold mt-1.5 text-[#430000]/60">
            <span>0</span>
            <span>20</span>
            <span>40</span>
          </div>
        </div>

        <p className="text-xs mt-4 border-t border-[#430000]/20 pt-3 text-[#430000]">
          {configSemaforo.mensaje}
        </p>
      </div>

      {/* ACTIVIDADES */}
      <div className="space-y-3">
        <h4 className="text-xs font-bold text-[#430000]/70 uppercase tracking-wider">
          Actividades ({actividades.length})
        </h4>

        <div className="space-y-2.5">
          {actividades.length === 0 ? (
            <p className="text-xs text-gray-500 bg-white p-4 rounded-xl border text-center">No hay evaluaciones programadas para este ciclo.</p>
          ) : (
            actividades.map((act) => {
              const tieneConflicto = verificarChoqueHorario(act.fechaInicio);
              
              const formateadorFecha = new Intl.DateTimeFormat('es-SV', {
                weekday: 'long',
                day: '2-digit',
                month: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                hour12: true
              });

              const fechaTextoRaw = act.fechaInicio ? formateadorFecha.format(new Date(act.fechaInicio)) : '';
              const fechaCompleta = fechaTextoRaw ? fechaTextoRaw.charAt(0).toUpperCase() + fechaTextoRaw.slice(1) : 'Fecha no asignada';

              return (
                <div
                  key={act.idActividad || act.id}
                  className={`flex flex-col p-3.5 bg-white border rounded-xl transition-all ${
                    tieneConflicto ? 'border-rose-400 bg-rose-50/30' : 'border-[#430000]/20'
                  }`}
                >
                  <div className="flex items-center justify-between w-full">
                    <div className="flex items-center space-x-3">
                      <span className="font-mono text-[10px] bg-[#430000]/10 text-[#430000] px-2 py-1 rounded-md font-bold">
                        {act.materiaInscritaCodigo || 'SACA'}
                      </span>
                      <div>
                        <p className="text-sm font-semibold text-[#430000]">
                          {act.nombre}
                        </p>
                        <span className="text-[11px] text-[#430000]/60">
                          {act.tipoActividadNombre || 'Evaluación'} - {fechaCompleta}
                        </span>
                      </div>
                    </div>

                    <div className="bg-[#960000]/10 text-[#430000] font-bold px-2.5 py-1 rounded-lg text-xs border border-[#430000]/20">
                      {act.tiempoEstimadoHoras} hrs
                    </div>
                  </div>

                  {tieneConflicto && (
                    <div className="mt-3 p-2 bg-rose-600 text-white text-[11px] font-bold rounded-lg flex items-center gap-2 animate-pulse">
                      <i className="fa-solid fa-triangle-exclamation"></i>
                      <span>Conflicto de Horario: Coincide con tu jornada laboral registrada.</span>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}

export default TableroCargaSemanal;