import React from 'react';
import Boton from './ui/Boton';
import { unirClases } from './ui/unirClases';

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
      type="button"
      onClick={() => setVistaActiva(id)}
      aria-current={isActive ? "page" : undefined}
      className={unirClases(
        "flex min-h-11 w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2",
        isActive
          ? "bg-brand-700 font-semibold text-white shadow-sm"
          : "text-ink-900 hover:bg-brand-50"
      )}
    >
      <i
        className={unirClases(
          "fa-solid w-5 shrink-0 text-center text-base",
          icon,
          isActive ? "text-white" : "text-brand-700"
        )}
        aria-hidden="true"
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
    <aside className="flex w-full flex-col justify-between border-b border-line bg-surface md:min-h-screen md:w-64 md:border-b-0 md:border-r">

      <div className="absolute bottom-0 left-0 top-0 hidden w-[4px] bg-brand-700 md:block"></div>

      <div>

        {/* HEADER */}
        <div className="flex items-center gap-3 border-b border-line bg-brand-50 p-5">

          <div className="text-2xl text-brand-700">
            <i className="fa-solid fa-graduation-cap" aria-hidden="true"></i>
          </div>

          {/* La página ya tiene su h1 en App.jsx; aquí es solo la marca */}
          <p className="text-xl font-extrabold text-ink-900">
            SACA
          </p>

        </div>

        {/* NAVEGACIÓN */}
        {/* En móvil las opciones van en dos columnas para no ocupar toda la primera pantalla */}
        <nav className="grid grid-cols-2 gap-1 p-4 md:grid-cols-1" aria-label="Módulos del Sistema">

          <div className="col-span-2 px-3 pb-2 text-xs font-bold uppercase tracking-widest text-ink-600 md:col-span-1">
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
            icon="fa-book-open"
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
            label="Gestión de disponibilidad"
            icon="fa-clock"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <SidebarItem
            id="jornada"
            label="Horario Laboral"
            icon="fa-briefcase"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <SidebarItem
            id="calendario"
            label="Calendario Académico"
            icon="fa-calendar-week"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <SidebarItem
            id="notificaciones"
            label="Notificaciones"
            icon="fa-bell"
            vistaActiva={vistaActiva}
            setVistaActiva={setVistaActiva}
          />

          <hr className="col-span-2 my-3 border-line md:col-span-1" />

          <Boton
            icono="fa-solid fa-power-off"
            onClick={cerrarSesion}
            className="col-span-2 min-h-11 w-full md:col-span-1"
          >
            Cerrar Sesión
          </Boton>

        </nav>

      </div>

    </aside>
  );
}
export default Sidebar;
