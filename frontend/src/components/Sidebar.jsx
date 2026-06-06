import React from 'react';

function SidebarItem({
  id,
  label,
  icon,
  vistaActiva,
  setVistaActiva
}) {
  const isActive = vistaActiva === id;

  return (
    <button
      onClick={() => setVistaActiva(id)}
      className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all duration-150 cursor-pointer ${
        isActive
          ? "bg-[#960000] text-[#eeeeee] font-semibold shadow-md"
          : "text-[#430000] hover:bg-[#430000]/10"
      }`}
    >
      <i
        className={`fa-solid ${icon} text-base ${
          isActive
            ? "text-[#eeeeee]"
            : "text-[#430000]"
        }`}
      ></i>

      <span>{label}</span>
    </button>
  );
}

function Sidebar({
  vistaActiva,
  setVistaActiva,
  cerrarSesion
}) {
  return (
    <aside className="w-full md:w-64 bg-[#ffffff] flex flex-col justify-between md:min-h-screen border-r border-[#430000]/20 shadow-lg">

      <div className="absolute left-0 top-0 bottom-0 w-[4px] bg-[#960000] hidden md:block"></div>

      <div>

        {/* HEADER */}
        <div className="p-5 flex items-center space-x-3 border-b border-[#430000]/20 bg-[#960000]/10">

          <div className="text-[#960000] text-2xl">
            <i className="fa-solid fa-graduation-cap"></i>
          </div>

          <h1 className="text-xl font-extrabold text-[#430000]">
            SACA
          </h1>

        </div>

        {/* NAVEGACIÓN */}
        <nav className="p-4 space-y-1">

          <div className="pb-2 px-4 text-[11px] font-bold tracking-widest text-[#430000]/60 uppercase">
            Módulos del Sistema
          </div>

          <SidebarItem
            id="dashboard"
            label="Dashboard"
            icon="fa-table-cells-large"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <SidebarItem
            id="academico"
            label="Gestión Académica"
            icon="fa-calendar-days"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <SidebarItem
            id="perfil"
            label="Perfil Académico"
            icon="fa-user-graduate"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <SidebarItem
            id="analisis"
            label="Análisis de Carga"
            icon="fa-gauge-high"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />
          <SidebarItem
            id="disponibilidad"
            label="Gestión disponibilidad"
            icon="fa-calendar-days"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

           <SidebarItem
            id="jornada"
            label="Horario Laboral"
            icon="fa-calendar-days"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />
          <hr className="my-4 border-[#430000]/20" />

          <button
            onClick={cerrarSesion}
      className="w-full mt-3 py-2.5 px-4 bg-[#F4E5E5] hover:bg-[#960000] text-[#430000] text-xs font-bold rounded-xl transition-all">
            <i className="fa-solid fa-right-from-bracket"></i>
 <span>  Cerrar Sesión</span>
          </button>

        </nav>

      </div>

      {/* TARJETA INFERIOR */}
      <div className="p-4 m-4 md:mx-5 bg-[#960000]/10 border border-[#430000]/20 rounded-2xl shadow-sm">

        <h3 className="text-[#430000] font-bold text-base">
          Carga Académica UES
        </h3>

        <p className="text-xs text-[#430000]/70 mt-1">
          Monitoreo automático de horas.
        </p>

        <button
          onClick={() => alert("¡MVP Grupo 03 Completo!")}
          className="w-full mt-4 py-2.5 px-4 bg-[#960000] hover:bg-[#430000] text-[#eeeeee] text-xs font-bold rounded-xl transition-all"
        >
          Ciclo I-2026 Activo
        </button>

      </div>

    </aside>
  );
}

export default Sidebar;
