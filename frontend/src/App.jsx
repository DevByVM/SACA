import { useState } from "react";
import Login from "./components/Login";
import Sidebar from "./components/Sidebar.jsx";
import TableroCargaSemanal from "./components/TableroCargaSemanal.jsx";
import GestionAcademica from "./components/GestionAcademica.jsx";

function App() {
  const [logueado, setLogueado] = useState(false);
  const [estudiante, setEstudiante] = useState(null);
  const [vistaActiva, setVistaActiva] = useState("dashboard");

  if (!logueado) {
    return (
      <Login
        onLoginSuccess={(estudianteAutenticado) => {
          setEstudiante(estudianteAutenticado);
          setLogueado(true);
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#ffffff] flex flex-col md:flex-row w-full overflow-x-hidden font-sans text-[#430000]">
      <Sidebar vistaActiva={vistaActiva} setVistaActiva={setVistaActiva} />

      <div className="flex-1 flex relative z-20 items-stretch overflow-hidden min-w-0 bg-[#ffffff]">
        <div className="container mx-auto px-6 flex flex-col xl:flex-row relative py-8 gap-8 items-stretch w-full">
          <div className="absolute right-6 top-4 sm:top-6 flex items-center gap-3 rounded-lg border border-[#430000]/20 bg-[#ffffff] px-4 py-2 text-right shadow-sm">
            <i className="fa-solid fa-user-graduate text-[#960000]"></i>
            <div>
              <p className="text-[10px] font-bold uppercase tracking-widest text-[#430000]/50">
                Estudiante
              </p>
              <p className="max-w-44 truncate text-sm font-black text-[#430000]">
                {estudiante?.nombre || "Sin estudiante"}
              </p>
            </div>
          </div>

          <div
            className={`w-full pt-16 sm:pt-12 ${
              vistaActiva === "dashboard" ? "xl:w-2/5" : "w-full"
            } flex flex-col justify-start`}
          >
            <span className="w-20 h-2 bg-[#960000] mb-6 block rounded-full"></span>

            <h1 className="font-extrabold uppercase text-4xl sm:text-5xl flex flex-col leading-none text-[#430000] tracking-tight mb-2">
              SACA UES
              <span className="text-3xl sm:text-4xl text-[#960000] font-black mt-1">
                {vistaActiva === "dashboard" && "Control Global"}
                {vistaActiva === "academico" && "Gestión Académica"}
                {vistaActiva === "analisis" && "Semáforo Horario"}
              </span>
            </h1>

            <p className="text-xs text-[#430000]/60 mb-6 leading-relaxed max-w-2xl">
              Sistema de Análisis de Carga Académica.
            </p>

            <div className="w-full flex-1">
              {vistaActiva === "academico" && <GestionAcademica estudiante={estudiante} />}

              {vistaActiva === "analisis" && <TableroCargaSemanal />}
            </div>
          </div>

          {vistaActiva === "dashboard" && (
            <div className="w-full xl:w-3/5 flex flex-col gap-6 relative justify-start">
              <div className="bg-[#ffffff] p-6 rounded-2xl border border-[#430000]/20 shadow-sm">
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
