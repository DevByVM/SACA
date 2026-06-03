import React from 'react';

function Navbar({ vistaActiva, setVistaActiva }) {
  return (
    <header className="h-24 sm:h-32 flex items-center z-30 w-full bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 transition-colors">
      <div className="container mx-auto px-6 flex items-center justify-between">
        {/* LOGO */}
        <div className="uppercase text-gray-800 dark:text-white font-black text-3xl tracking-wider flex items-center space-x-2">
          <span className="text-pink-500">SACA</span><span>OS</span>
        </div>
        
        <div className="flex items-center">
          {/* MENÚ DE NAVEGACIÓN INTERACTIVO */}
          <nav className="font-sen text-gray-800 dark:text-white uppercase text-sm lg:flex items-center hidden space-x-2">
            <button 
              onClick={() => setVistaActiva("dashboard")}
              className={`py-2 px-6 flex rounded-lg transition-colors ${vistaActiva === "dashboard" ? "bg-pink-500 text-white font-bold" : "hover:bg-gray-100 dark:hover:bg-gray-800"}`}
            >
              Dashboard
            </button>
            <button 
              onClick={() => setVistaActiva("materias")}
              className={`py-2 px-6 flex rounded-lg transition-colors ${vistaActiva === "materias" ? "bg-pink-500 text-white font-bold" : "hover:bg-gray-100 dark:hover:bg-gray-800"}`}
            >
              Materias
            </button>
            <button 
              onClick={() => setVistaActiva("analisis")}
              className={`py-2 px-6 flex rounded-lg transition-colors ${vistaActiva === "analisis" ? "bg-pink-500 text-white font-bold" : "hover:bg-gray-100 dark:hover:bg-gray-800"}`}
            >
              Análisis
            </button>
          </nav>

          {/* Menú móvil  */}
          <button className="lg:hidden flex flex-col ml-4">
            <span className="w-6 h-1 bg-gray-800 dark:bg-white mb-1"></span>
            <span className="w-6 h-1 bg-gray-800 dark:bg-white mb-1"></span>
            <span className="w-6 h-1 bg-gray-800 dark:bg-white mb-1"></span>
          </button>
        </div>
      </div>
    </header>
  );
}

export default Navbar;