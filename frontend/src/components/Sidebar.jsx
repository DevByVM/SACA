import React from 'react';

// Subcomponente reutilizable para los botones del menú
function SidebarItem({ id, label, icon, vistaActiva, setVistaActiva }) {
  const isActive = vistaActiva === id;

  return (
    <button
      onClick={() => setVistaActiva(id)}
      className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-150 cursor-pointer ${
        isActive
          ? "bg-[#1F2937] text-cyan-400 font-semibold shadow-lg shadow-black/20"
          : "hover:bg-[#1F2937]/50 hover:text-slate-200"
      }`}
    >
      <i className={`fa-solid ${icon} text-base ${isActive ? "text-cyan-400" : "text-slate-500"}`}></i>
      <span>{label}</span>
    </button>
  );
}

function Sidebar({ vistaActiva, setVistaActiva }) {
  return (
    <aside className="w-full md:w-64 bg-[#111827] text-slate-400 flex flex-col justify-between md:min-h-screen border-b md:border-b-0 md:border-r border-slate-800 font-sans select-none shadow-2xl relative flex-shrink-0">
      
      {/* LÍNEA DECORATIVA MORADA IZQUIERDA */}
      <div className="absolute left-0 top-0 bottom-0 w-[4px] bg-[#6366F1] hidden md:block"></div>

      <div>
        {/* LOGO Y ENCABEZADO */}
        <div className="p-5 flex items-center space-x-3 border-b border-gray-800/40 bg-[#1F2937]/30 pl-5 md:pl-7">
          <div className="text-cyan-400 text-2xl flex items-center justify-center filter drop-shadow-[0_2px_8px_rgba(34,211,238,0.3)]">
            <i className="fa-solid fa-graduation-cap"></i>
          </div>
          <h1 className="text-xl font-extrabold text-white tracking-tight">
            SACA<span className="text-white opacity-90"></span>
          </h1>
        </div>

        {/* NAVEGACIÓN PRINCIPAL */}
        <nav className="p-4 space-y-1 pl-4 md:pl-6">
          
          {/* Dashboard */}
          <SidebarItem 
            id="dashboard" 
            label="Dashboard" 
            icon="fa-table-cells-large" 
            vistaActiva={vistaActiva} 
            setVistaActiva={setVistaActiva} 
          />

          {/* Gestión de Materias */}
          <SidebarItem 
            id="materias" 
            label="Gestión de Materias" 
            icon="fa-box-archive" 
            vistaActiva={vistaActiva} 
            setVistaActiva={setVistaActiva} 
          />

          <div className="pt-6 pb-2 px-4 text-[11px] font-bold tracking-widest text-slate-500 uppercase">
            Módulos del Sistema
          </div>

          {/* Análisis de Carga */}
          <SidebarItem 
            id="analisis" 
            label="Análisis de Carga" 
            icon="fa-gauge-high" 
            vistaActiva={vistaActiva} 
            setVistaActiva={setVistaActiva} 
          />

        </nav>
      </div>

      {/* TARJETA INFERIOR */}
      <div className="p-4 m-4 md:mx-5 bg-gradient-to-br from-cyan-950/40 via-[#111827] to-[#111827] border border-cyan-800/40 rounded-2xl shadow-xl">
        <h3 className="text-white font-bold text-base tracking-tight">Carga Académica UES</h3>
        <p className="text-xs text-slate-400 mt-1 leading-relaxed">Monitoreo automático de horas.</p>
        
        {/* Botón inferior en aqua sólido */}
        <button 
          onClick={() => alert("¡MVP Grupo 03 Completo!")}
          className="w-full mt-4 py-2.5 px-4 bg-cyan-600 hover:bg-cyan-500 active:bg-cyan-700 text-white text-xs font-bold rounded-xl shadow-lg shadow-cyan-600/20 hover:scale-[1.02] active:scale-[0.98] transition-all cursor-pointer"
        >
          Ciclo I-2026 Activo
        </button>
      </div>

    </aside>
  );
}

export default Sidebar;