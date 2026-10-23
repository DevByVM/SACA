import { useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  alExpirarSesion,
  borrarSesion,
  MENSAJE_SESION_EXPIRADA,
  milisegundosHastaExpirar,
  notificarSesionExpirada,
  obtenerSesion,
} from "./api/sesion";
import Login from "./components/Login";
import PerfilAcademico from "./components/PerfilAcademico";
import Sidebar from "./components/Sidebar.jsx";
import TableroCargaSemanal from "./components/TableroCargaSemanal.jsx";
import PanelControlAcademico from "./components/PanelControlAcademico.jsx";
import GestionAcademica from "./components/GestionAcademica.jsx";
import GestionDisponiblidad from "./components/GestionDisponibilidad.jsx";
import { RegistroJornada } from "./components/GestionJornadaLaboral.jsx";
import CalendarioAcademico from "./components/CalendarioAcademico.jsx";
import ConfiguracionNotificaciones from "./components/ConfiguracionNotificaciones.jsx";
import NotificacionesProximas from "./components/NotificacionesProximas.jsx";
function App() {

  const [logueado, setLogueado] = useState(
    () => !!obtenerSesion()
  );

  const [estudiante, setEstudiante] = useState(
    () => obtenerSesion()?.estudiante ?? null
  );

  const [vistaActiva, setVistaActiva] =
    useState("dashboard");
  const [actualizacionNotificaciones, setActualizacionNotificaciones] = useState(0);

  const cerrarSesion = () => {

    borrarSesion();

    setEstudiante(null);
    setVistaActiva("dashboard");
    setLogueado(false);
  };

  // Vuelve al login si el backend responde 401 o cuando vence el token
  useEffect(() => {
    if (!logueado) return undefined;

    const dejarDeEscuchar = alExpirarSesion((evento) => {
      // Indica a client.js que App gestiona el cierre y el aviso
      evento.preventDefault();
      setEstudiante(null);
      setVistaActiva("dashboard");
      setLogueado(false);
      toast.info(MENSAJE_SESION_EXPIRADA, { toastId: "sesion-expirada" });
    });

    const sesion = obtenerSesion();
    if (!sesion) {
      notificarSesionExpirada();
      return dejarDeEscuchar;
    }
    const temporizador = setTimeout(notificarSesionExpirada, milisegundosHastaExpirar(sesion));

    return () => {
      clearTimeout(temporizador);
      dejarDeEscuchar();
    };
  }, [logueado]);

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

      <Sidebar
        vistaActiva={vistaActiva}
        setVistaActiva={setVistaActiva}
        cerrarSesion={cerrarSesion}
      />

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

            <NotificacionesProximas
              estudiante={estudiante}
              onConfigurar={() => setVistaActiva("notificaciones")}
              actualizacion={actualizacionNotificaciones}
            />

          </div>

          <div className="w-full min-w-0 pt-16 sm:pt-12 flex flex-col justify-start">

            <span className="w-20 h-2 bg-[#960000] mb-6 block rounded-full"></span>

            <h1 className="font-extrabold uppercase text-4xl sm:text-5xl flex flex-col leading-none text-[#430000] tracking-tight mb-2">

              SACA UES

              <span className="text-3xl sm:text-4xl text-[#960000] font-black mt-1">
                {vistaActiva === "dashboard" && "Panel de Control"}
                {vistaActiva === "academico" && "Gestión Académica"}
                {vistaActiva === "perfil" && "Perfil Académico"}
                {vistaActiva === "analisis" && "Análisis de Carga"}
                 {vistaActiva === "disponibilidad" && "Gestión disponibilidad"}
                  {vistaActiva === "jornada" && "Gestión de Jornada Laboral"}
                  {vistaActiva === "calendario" && "Calendario Académico"}
                  {vistaActiva === "notificaciones" && "Notificaciones"}
              </span>

            </h1>

            <p className="text-xs text-[#430000]/60 mb-6 leading-relaxed max-w-2xl">
              Sistema de Análisis de Carga Académica.
            </p>

            <div className="w-full flex-1">

              {vistaActiva === "dashboard" && (
                <PanelControlAcademico
                  estudiante={estudiante}
                  onNavegar={setVistaActiva}
                />
              )}

              {vistaActiva === "academico" && (
                <GestionAcademica estudiante={estudiante} />
              )}

              {vistaActiva === "perfil" && (
                <PerfilAcademico estudiante={estudiante} />
              )}

              {vistaActiva === "analisis" && (
                <TableroCargaSemanal estudiante={estudiante} onNavegar={setVistaActiva} />
              )}
              {vistaActiva === "disponibilidad" && <GestionDisponiblidad estudiante={estudiante} />}

          
               {vistaActiva === "jornada" && (
   <RegistroJornada estudiante={estudiante} />
)}

               {vistaActiva === "calendario" && (
  <CalendarioAcademico
    estudiante={estudiante}
  />
)}

              {vistaActiva === "notificaciones" && (
                <ConfiguracionNotificaciones
                  estudiante={estudiante}
                  onPreferenciasGuardadas={() => setActualizacionNotificaciones((actual) => actual + 1)}
                />
              )}
               </div>

             </div>

        </div>

      </div>

    </div>
  );
}

export default App;
