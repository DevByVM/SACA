import React, { useState } from 'react';

function TableroCargaSemanal() {
  const [horasPendientes] = useState(12.5);
  const [actividades] = useState([
    { id: 1, materia: 'INF115', tipo: 'Laboratorio', descripcion: 'Guía de POO con Java', hrs: 4.0 },
    { id: 2, materia: 'INF220', tipo: 'Proyecto', descripcion: 'Árboles binarios AVL', hrs: 8.5 }
  ]);

 
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

  return (
    <div className="space-y-6 bg-[#eeeeee] p-4 rounded-x1" >

      {/* HEADER */}
      <div className="border-b border-[#430000]/20 pb-4">
        <h3 className="text-xl font-bold text-[#430000]">
          Análisis de Carga Académica
        </h3>
        <p className="text-xs text-[#430000]/60 mt-1">
          Carga académica en tiempo real
        </p>
      </div>

      {/* SEMÁFORO (SIN CAMBIOS) */}
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
              {horasPendientes} hrs
            </span>
          </div>

        </div>

        {/* PROGRESO */}
        <div className="mt-4">
          <div className="w-full bg-[#430000]/10 rounded-full h-2.5 overflow-hidden">
            <div
              className={`h-full transition-all duration-500 ${
                horasPendientes > 35
                  ? 'bg-rose-500'
                  : horasPendientes > 20
                  ? 'bg-amber-500'
                  : 'bg-emerald-500'
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

          {actividades.map((act) => (
            <div
              key={act.id}
              className="flex items-center justify-between p-3.5 bg-white border border-[#430000]/20 rounded-xl"
            >

              <div className="flex items-center space-x-3">

                <span className="font-mono text-[10px] bg-[#430000]/10 text-[#430000] px-2 py-1 rounded-md">
                  {act.materia}
                </span>

                <div>
                  <p className="text-sm font-semibold text-[#430000]">
                    {act.descripcion}
                  </p>
                  <span className="text-[11px] text-[#430000]/60 capitalize">
                    {act.tipo}
                  </span>
                </div>

              </div>

              <div className="bg-[#960000]/10 text-[#430000] font-bold px-2.5 py-1 rounded-lg text-xs border border-[#430000]/20">
                {act.hrs} hrs
              </div>

            </div>
          ))}

        </div>

      </div>

    </div>
  );
}

export default TableroCargaSemanal;