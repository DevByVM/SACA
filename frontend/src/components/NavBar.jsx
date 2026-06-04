import React from 'react';

function Navbar({ vistaActiva, setVistaActiva }) {
  return (
    <header className="h-24 sm:h-32 flex items-center z-30 w-full bg-[#ffffff] border-b border-[#430000]/20">
      <div className="container mx-auto px-6 flex items-center justify-between">

        {/* LOGO */}
        <div className="uppercase font-black text-3xl tracking-wider flex items-center space-x-2">
          <span className="text-[#960000]">SACA</span>
          <span className="text-[#430000]">OS</span>
        </div>

        <div className="flex items-center">

          {/* MENÚ */}
          <nav className="uppercase text-sm lg:flex items-center hidden space-x-2 text-[#430000]">

            <button
              onClick={() => setVistaActiva("dashboard")}
              className={`py-2 px-6 rounded-lg transition-colors ${
                vistaActiva === "dashboard"
                  ? "bg-[#960000] text-[#eeeeee] font-bold"
                  : "hover:bg-[#960000]/10"
              }`}
            >
              Dashboard
            </button>

            <button
              onClick={() => setVistaActiva("analisis")}
              className={`py-2 px-6 rounded-lg transition-colors ${
                vistaActiva === "analisis"
                  ? "bg-[#960000] text-[#eeeeee] font-bold"
                  : "hover:bg-[#960000]/10"
              }`}
            >
              Análisis
            </button>

          </nav>

          {/* MENÚ MÓVIL */}
          <button className="lg:hidden flex flex-col ml-4">
            <span className="w-6 h-1 bg-[#430000] mb-1 rounded"></span>
            <span className="w-6 h-1 bg-[#430000] mb-1 rounded"></span>
            <span className="w-6 h-1 bg-[#430000] mb-1 rounded"></span>
          </button>

        </div>
      </div>
    </header>
  );
}

export default Navbar;
