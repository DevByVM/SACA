import React, { useState } from 'react';

function TableroCargaSemanal() {
  // Estados simulados para el comportamiento del semáforo en el MVP
  const [horasPendientes, setHorasPendientes] = useState(12.5);
  const [actividades, setActividades] = useState([
    { id: 1, materia: 'INF115', tipo: 'Laboratorio', descripcion: 'Guía de POO con Java', hrs: 4.0 },
    { id: 2, materia: 'INF220', tipo: 'Proyecto', descripcion: 'Árboles binarios AVL', hrs: 8.5 }
  ]);

  // Lógica del semáforo de carga académica (Verde, Amarillo, Rojo)
  let configSemaforo = {
    bg: 'bg-emerald-50/60 border-emerald-200 text-emerald-800',
    badge: 'bg-emerald-100 text-emerald-800 border-emerald-300',
    icon: 'fa-circle-check text-emerald-500',
    texto: 'Carga Normal',
    mensaje: 'Tu carga académica esta semana es completamente manejable. ¡Buen ritmo!'
  };

  if (horasPendientes > 20 && horasPendientes <= 35) {
    configSemaforo = {
      bg: 'bg-amber-50/60 border-amber-200 text-amber-800',
      badge: 'bg-amber-100 text-amber-800 border-amber-300',
      icon: 'fa-triangle-exclamation text-amber-500',
      texto: 'Carga Elevada',
      mensaje: 'Atención: Las horas estimadas rozan el límite sugerido. Organiza tus días.'
    };
  } else if (horasPendientes > 35) {
    configSemaforo = {
      bg: 'bg-rose-50/60 border-rose-200 text-rose-800',
      badge: 'bg-rose-100 text-rose-800 border-rose-300',
      icon: 'fa-circle-exclamation text-rose-500',
      texto: 'Sobrecarga Crítica',
      mensaje: '¡Alerta! Has superado el límite de carga semanal. Riesgo de estrés académico.'
    };
  }

  // Porcentaje  para la barra de progreso (Máximo sugerido: 40 horas semanales)
  const porcentajeProgreso = Math.min((horasPendientes / 40) * 100, 100);

  return (
    <div className="space-y-6">
      {/* SECCIÓN 1: ENCABEZADO */}
      <div className="border-b border-gray-100 pb-4">
        <h3 className="text-xl font-bold text-slate-800">Análisis de Carga Académica</h3>
        <p className="text-xs text-slate-500 mt-0.5">Carga Academica en tiempo real</p>
      </div>

      {/* SECCIÓN 2: TARJETA DEL SEMÁFORO PREMIUM */}
      <div className={`border p-5 rounded-xl shadow-sm transition-all duration-300 ${configSemaforo.bg}`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <i className={`fa-solid ${configSemaforo.icon} text-2xl`}></i>
            <div>
              <span className="text-xs uppercase font-bold tracking-wider opacity-70">Estado del Estudiante</span>
              <div className="flex items-center space-x-2 mt-0.5">
                <h4 className="text-lg font-black tracking-tight">{configSemaforo.texto}</h4>
                <span className="flex h-2 w-2 relative">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-current opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-current"></span>
                </span>
              </div>
            </div>
          </div>
          
          <div className="text-right">
            <span className="text-xs opacity-70 block font-medium">Horas Estimadas</span>
            <span className="text-2xl font-black tracking-tight">{horasPendientes} <span className="text-xs font-semibold">hrs</span></span>
          </div>
        </div>

        {/* BARRA DE PROGRESO INTERACTIVA */}
        <div className="mt-4">
          <div className="w-full bg-gray-200/60 rounded-full h-2.5 overflow-hidden shadow-inner">
            <div 
              className={`h-full rounded-full transition-all duration-500 ${
                horasPendientes > 35 ? 'bg-rose-500' : horasPendientes > 20 ? 'bg-amber-500' : 'bg-emerald-500'
              }`}
              style={{ width: `${porcentajeProgreso}%` }}
            ></div>
          </div>
          <div className="flex justify-between text-[10px] font-bold mt-1.5 opacity-60 uppercase tracking-wider">
            <span>0 hrs</span>
            <span>20 hrs (Límite)</span>
            <span>40 hrs</span>
          </div>
        </div>

        <p className="text-xs mt-4 border-t border-current/10 pt-3 font-medium">
          {configSemaforo.mensaje}
        </p>
      </div>

      {/* SECCIÓN 3: ACTIVIDADES DETECTADAS */}
      <div className="space-y-3">
        <h4 className="text-xs font-bold text-slate-600 uppercase tracking-wider flex items-center space-x-2">
          <i className="fa-solid fa-list-check text-slate-400"></i>
          <span>Desglose de Actividades esta Semana ({actividades.length})</span>
        </h4>

        {actividades.length === 0 ? (
          <div className="text-center py-6 border border-dashed border-gray-200 rounded-xl text-slate-400 text-xs">
            <i className="fa-solid fa-calendar-blank text-lg mb-1 block"></i>
            No hay tareas ni evaluaciones registradas para esta semana.
          </div>
        ) : (
          <div className="space-y-2.5">
            {actividades.map((act) => (
              <div key={act.id} className="flex items-center justify-between p-3.5 bg-white border border-gray-200/70 rounded-xl hover:shadow-sm transition-all group">
                <div className="flex items-center space-x-3">
                  <span className="font-mono text-[10px] bg-slate-100 text-slate-700 font-bold px-2 py-1 rounded-md border border-slate-200 group-hover:bg-red-50 group-hover:text-red-700 group-hover:border-red-100 transition-colors">
                    {act.materia}
                  </span>
                  <div>
                    <p className="text-sm font-semibold text-slate-800 leading-tight">{act.descripcion}</p>
                    <span className="text-[11px] text-slate-400 font-medium capitalize">{act.tipo}</span>
                  </div>
                </div>
                <div className="bg-slate-50 text-slate-700 font-bold px-2.5 py-1 rounded-lg text-xs border border-gray-200/50">
                  {act.hrs} hrs
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default TableroCargaSemanal;