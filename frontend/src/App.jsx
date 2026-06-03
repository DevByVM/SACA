import { useState } from "react";
import Sidebar from "./components/Sidebar.jsx"; 
import ListaMaterias from "./components/ListaMaterias.jsx";
import TableroCargaSemanal from "./components/TableroCargaSemanal.jsx";
function App() {
  const [vistaActiva, setVistaActiva] = useState("dashboard");

  return (
    <div className="min-h-screen bg-[#111827] flex flex-col md:flex-row w-full overflow-x-hidden font-sans text-slate-300">
      
      <Sidebar vistaActiva={vistaActiva} setVistaActiva={setVistaActiva} />

      <div className="flex-1 flex relative z-20 items-stretch overflow-hidden min-w-0 bg-[#131B2E]">
        <div className="container mx-auto px-6 flex flex-col xl:flex-row relative py-8 gap-8 items-stretch w-full">
          
          <div className={`w-full ${vistaActiva === "dashboard" ? "xl:w-2/5" : "w-full"} flex flex-col justify-start`}>
            
            {/* CAMBIO: Barra decorativa superior en aqua */}
            <span className="w-20 h-2 bg-cyan-400 mb-6 block rounded-full shadow-lg shadow-cyan-400/20"></span>
            
            <h1 className="font-extrabold uppercase text-4xl sm:text-5xl flex flex-col leading-none text-white tracking-tight mb-2">
              SACA UES
              {/* CAMBIO: Subtítulos en color aqua  */}
              <span className="text-3xl sm:text-4xl text-cyan-400 font-black mt-1">
                {vistaActiva === "dashboard" && "Control Global"}
                {vistaActiva === "materias" && "Inscripción"}
                {vistaActiva === "analisis" && "Semáforo Horario"}
              </span>
            </h1>
            
            <p className="text-xs text-slate-400 mb-6 leading-relaxed max-w-2xl">
              Sistema de Análisis de Carga Académica. Monitoreo inteligente de Unidades Valorativas (UV) 
              y horas de estudio semanales para asegurar tu rendimiento en la Universidad de El Salvador.
            </p>

            <div className="w-full flex-1">
              {(vistaActiva === "dashboard" || vistaActiva === "materias") && <ListaMaterias vistaActiva={vistaActiva} />}
              {vistaActiva === "analisis" && <TableroCargaSemanal />}
            </div>
          </div>

          {/* COLUMNA DERECHA (DASHBOARD) */}
          {vistaActiva === "dashboard" && (
            <div className="w-full xl:w-3/5 flex flex-col gap-6 relative justify-start">
              
              <div className="bg-[#1e293b]/40 p-6 rounded-2xl border border-slate-800 shadow-xl backdrop-blur-sm">
                <TableroCargaSemanal />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {/* Servidor - CAMBIO: Icono en aqua */}
                <div className="bg-[#1e293b]/20 border border-slate-800/80 p-5 rounded-2xl flex items-center space-x-4 shadow-lg">
                  <div className="p-3 bg-cyan-500/10 text-cyan-400 rounded-xl text-lg">
                    <i className="fa-solid fa-server"></i>
                  </div>
                  </div>
                    </div>
              </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;