import { useState } from "react";
import Login from "./components/Login";

import Sidebar from "./components/Sidebar.jsx";
import ListaMaterias from "./components/ListaMaterias.jsx";
import TableroCargaSemanal from "./components/TableroCargaSemanal.jsx";

function App() {

  const [logueado, setLogueado] = useState(false);
  const [vistaActiva, setVistaActiva] = useState("dashboard");

  //  SI NO ESTÁ LOGUEADO → LOGIN
  if (!logueado) {
    return (
      <Login
        onLoginSuccess={() => setLogueado(true)}
      />
    );
  }

  //  SI ESTÁ LOGUEADO  DASHBOARD COMPLETO
  return (
    <div className="min-h-screen bg-[#111827] flex flex-col md:flex-row w-full overflow-x-hidden font-sans text-slate-300">

      <Sidebar
        vistaActiva={vistaActiva}
        setVistaActiva={setVistaActiva}
      />

      <div className="flex-1 flex relative z-20 items-stretch overflow-hidden min-w-0 bg-[#131B2E]">

        <div className="container mx-auto px-6 flex flex-col xl:flex-row relative py-8 gap-8 items-stretch w-full">

          <div className={`w-full ${vistaActiva === "dashboard" ? "xl:w-2/5" : "w-full"} flex flex-col justify-start`}>

            <span className="w-20 h-2 bg-cyan-400 mb-6 block rounded-full shadow-lg shadow-cyan-400/20"></span>

            <h1 className="font-extrabold uppercase text-4xl sm:text-5xl flex flex-col leading-none text-white tracking-tight mb-2">
              SACA UES
              <span className="text-3xl sm:text-4xl text-cyan-400 font-black mt-1">
                {vistaActiva === "dashboard" && "Control Global"}
                {vistaActiva === "materias" && "Inscripción"}
                {vistaActiva === "analisis" && "Semáforo Horario"}
              </span>
            </h1>

            <p className="text-xs text-slate-400 mb-6 leading-relaxed max-w-2xl">
              Sistema de Análisis de Carga Académica.
            </p>

            <div className="w-full flex-1">
              {(vistaActiva === "dashboard" || vistaActiva === "materias") && (
                <ListaMaterias vistaActiva={vistaActiva} />
              )}

              {vistaActiva === "analisis" && (
                <TableroCargaSemanal />
              )}
            </div>
          </div>

          {vistaActiva === "dashboard" && (
            <div className="w-full xl:w-3/5 flex flex-col gap-6 relative justify-start">

              <div className="bg-[#1e293b]/40 p-6 rounded-2xl border border-slate-800 shadow-xl backdrop-blur-sm">
                <TableroCargaSemanal />
              </div>

            </div>
          )}

        </div>
      </div>
    </div>
  );
}

export default App;