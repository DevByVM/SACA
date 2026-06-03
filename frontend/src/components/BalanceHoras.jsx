// src/components/BalanceHoras.jsx
import React from 'react';

function BalanceHoras({ horasDisponibles, horasTrabajo, trabaja }) {
  return (
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
  );
}

export default BalanceHoras;