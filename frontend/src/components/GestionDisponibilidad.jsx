import React, { useState } from 'react';

function GestionDisponibilidad() {
  // Estado para SDACAS-7 (Configuración de disponibilidad)
  const [horasDisponibles, setHorasDisponibles] = useState(40);
  
  // Estados para SDACAS-8 (Registro de jornada laboral)
  const [trabaja, setTrabaja] = useState(false);
  const [horasTrabajo, setHorasTrabajo] = useState(0);

  const guardarConfiguracion = (e) => {
    e.preventDefault();
    alert(`¡Configuración guardada! Horas netas disponibles para estudiar: ${horasDisponibles - horasTrabajo} hrs.`);
  };

  return (
    <div className="space-y-6 text-slate-300">
      {/* ENCABEZADO */}
      <div className="border-b border-slate-800 pb-4">
        <h3 className="text-lg font-bold text-white">Disponibilidad y Jornada Laboral</h3>
        <p className="text-xs text-slate-500 mt-0.5">Establece tus límites de tiempo semanales para el cálculo de carga</p>
      </div>

      {/* RESTRICCIONES EN GRID */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        
        {/* FORMULARIO DE RESTRICCIONES (SDACAS-7 y SDACAS-8) */}
        <div className="lg:col-span-2 bg-[#1e293b]/40 border border-slate-800 p-6 rounded-xl shadow-2xl backdrop-blur-sm h-fit">
          <h4 className="text-xs font-bold text-white uppercase tracking-widest mb-5 flex items-center space-x-2 border-b border-slate-800 pb-3">
            <i className="fa-solid fa-clock text-cyan-400 text-sm"></i>
            <span>Configurar Tiempos</span>
          </h4>

          <form onSubmit={guardarConfiguracion} className="space-y-4">
            {/* SDACAS-7: Disponibilidad Semanal */}
            <div>
              <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                Horas máximas de estudio semanales
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-500 text-xs">
                  <i className="fa-solid fa-hourglass-half"></i>
                </span>
                <input
                  type="number"
                  value={horasDisponibles}
                  onChange={(e) => setHorasDisponibles(Number(e.target.value))}
                  className="w-full text-sm pl-9 pr-4 py-2.5 bg-[#111827]/60 border border-slate-800 rounded-xl focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-400 text-white transition-all outline-none"
                />
              </div>
            </div>

            {/* SDACAS-8: Toggle de Jornada Laboral */}
            <div className="p-3 bg-[#111827]/40 border border-slate-800 rounded-xl flex items-center justify-between">
              <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">¿Posees jornada laboral?</span>
              <input 
                type="checkbox" 
                checked={trabaja}
                onChange={(e) => {
                  setTrabaja(e.target.checked);
                  if(!e.target.checked) setHorasTrabajo(0);
                }}
                className="w-4 h-4 text-cyan-600 bg-gray-700 border-gray-600 rounded focus:ring-cyan-500 focus:ring-2 cursor-pointer"
              />
            </div>

            {/* Horas de trabajo si aplica */}
            {trabaja && (
              <div className="animate-fadeIn">
                <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">
                  Horas semanales de trabajo
                </label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-500 text-xs">
                    <i className="fa-solid fa-briefcase"></i>
                  </span>
                  <input
                    type="number"
                    value={horasTrabajo}
                    onChange={(e) => setHorasTrabajo(Number(e.target.value))}
                    placeholder="Ej: 20 o 40 hrs"
                    className="w-full text-sm pl-9 pr-4 py-2.5 bg-[#111827]/60 border border-slate-800 rounded-xl focus:ring-2 focus:ring-cyan-500/20 focus:border-cyan-400 text-white transition-all outline-none"
                  />
                </div>
              </div>
            )}

            <button
              type="submit"
              className="w-full mt-3 flex items-center justify-center space-x-2 py-3 bg-cyan-600 hover:bg-cyan-500 active:bg-cyan-700 text-white font-bold text-xs uppercase tracking-wider rounded-xl shadow-lg shadow-cyan-600/20 transition-all duration-150 cursor-pointer"
            >
              <i className="fa-solid fa-floppy-disk"></i>
              <span>Guardar Parámetros</span>
            </button>
          </form>
        </div>

        {/* BALANCE DE HORAS (Sustento visual para la entrega) */}
        <div className="lg:col-span-3 bg-[#1e293b]/20 border border-slate-800/60 p-6 rounded-xl flex flex-col justify-between">
          <div>
            <h4 className="text-sm font-bold text-slate-200 mb-2">Cómputo Automático</h4>
            <p className="text-xs text-slate-400 leading-relaxed">
              Las horas netas reales dedicadas a la Universidad de El Salvador se calculan deduciendo tu jornada laboral del tope máximo de tu disponibilidad semanal.
            </p>
          </div>
          
          <div className="p-6 bg-[#111827]/60 border border-slate-800 rounded-xl text-center my-auto">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider block">Tiempo neto disponible para estudio</span>
            <span className="text-5xl font-black text-cyan-400 block my-2">
              {horasDisponibles - horasTrabajo} hrs
            </span>
            <span className="text-[10px] text-slate-500 block">
              {trabaja ? `(${horasDisponibles} Disponibles - ${horasTrabajo} Laborales)` : "Sin deducciones por trabajo"}
            </span>
          </div>
        </div>

      </div>
    </div>
  );
}

export default GestionDisponibilidad;