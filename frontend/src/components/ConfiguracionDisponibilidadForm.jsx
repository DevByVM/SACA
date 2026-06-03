// src/components/GestionDisponibilidad.jsx
import React, { useState } from 'react';
import ConfiguracionDisponibilidadForm from './ConfiguracionDisponibilidadForm';
import BalanceHoras from './BalanceHoras';

function GestionDisponibilidad() {
  const [horasDisponibles, setHorasDisponibles] = useState(40);
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
        
        {/* FORMULARIO DE RESTRICCIONES */}
        <ConfiguracionDisponibilidadForm 
          horasDisponibles={horasDisponibles}
          setHorasDisponibles={setHorasDisponibles}
          trabaja={trabaja}
          setTrabaja={setTrabaja}
          horasTrabajo={horasTrabajo}
          setHorasTrabajo={setHorasTrabajo}
          guardarConfiguracion={guardarConfiguracion}
        />

        {/* BALANCE DE HORAS */}
        <BalanceHoras 
          horasDisponibles={horasDisponibles} 
          horasTrabajo={horasTrabajo} 
          trabaja={trabaja}
        />

      </div>
    </div>
  );
}

export default GestionDisponibilidad;