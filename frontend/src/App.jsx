import { useEffect, useRef, useState } from "react";
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
import Boton from "./components/ui/Boton";
import { unirClases } from "./components/ui/unirClases";
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
  const [menuAbierto, setMenuAbierto] = useState(false);
  const botonMenuRef = useRef(null);

  const cerrarSesion = () => {

    borrarSesion();

    setEstudiante(null);
    setVistaActiva("dashboard");
    setMenuAbierto(false);
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
      setMenuAbierto(false);
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

  // En móvil el menú se pliega: elegir un módulo lo cierra y devuelve al contenido
  const navegarDesdeMenu = (vista) => {
    setVistaActiva(vista);
    setMenuAbierto(false);
  };

  const cerrarMenuConEscape = (evento) => {
    if (evento.key === "Escape" && menuAbierto) {
      setMenuAbierto(false);
      botonMenuRef.current?.focus();
    }
  };

  return (
    <div className="min-h-screen bg-canvas flex flex-col md:flex-row w-full overflow-x-hidden font-sans text-ink-900">

      <div className="border-b border-line bg-surface p-4 md:hidden">
        <Boton
          ref={botonMenuRef}
          variante="secundario"
          icono={menuAbierto ? "fa-solid fa-xmark" : "fa-solid fa-bars"}
          aria-expanded={menuAbierto}
          aria-controls="menu-principal"
          onClick={() => setMenuAbierto((abierto) => !abierto)}
          className="min-h-11 w-full"
        >
          {menuAbierto ? "Cerrar menú" : "Menú"}
        </Boton>
      </div>

      <div
        id="menu-principal"
        className={unirClases(menuAbierto ? "block" : "hidden", "md:flex")}
        onKeyDown={cerrarMenuConEscape}
      >
        <Sidebar
          vistaActiva={vistaActiva}
          setVistaActiva={navegarDesdeMenu}
          cerrarSesion={cerrarSesion}
        />
      </div>

      <main className="relative z-20 min-w-0 flex-1">

        <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:py-8">

          <header className="mb-6 flex flex-col-reverse gap-4 sm:flex-row sm:items-start sm:justify-between">

            <div className="min-w-0">

              <span className="mb-4 block h-1.5 w-16 rounded-full bg-brand-700" aria-hidden="true"></span>

              <p className="text-sm font-bold uppercase tracking-widest text-brand-700">
                SACA UES
              </p>

              <h1 className="mt-1 text-3xl font-extrabold uppercase leading-tight tracking-tight text-ink-900 sm:text-4xl">
                {vistaActiva === "dashboard" && "Panel de control"}
                {vistaActiva === "academico" && "Gestión académica"}
                {vistaActiva === "perfil" && "Perfil académico"}
                {vistaActiva === "analisis" && "Análisis de carga"}
                {vistaActiva === "disponibilidad" && "Gestión de disponibilidad"}
                {vistaActiva === "jornada" && "Gestión de jornada laboral"}
                {vistaActiva === "calendario" && "Calendario académico"}
                {vistaActiva === "notificaciones" && "Notificaciones"}
              </h1>

            </div>

            <div className="flex shrink-0 items-center gap-3 self-end rounded-lg border border-line bg-surface px-4 py-2 text-right shadow-sm sm:self-start">

              <i className="fa-solid fa-user-graduate text-brand-700" aria-hidden="true"></i>

              <div className="min-w-0">
                <p className="text-xs font-bold uppercase tracking-widest text-ink-600">
                  Estudiante
                </p>

                <p className="max-w-44 truncate text-sm font-bold text-ink-900">
                  {estudiante?.nombre || "Sin estudiante"}
                </p>
              </div>

              <NotificacionesProximas
                estudiante={estudiante}
                onConfigurar={() => setVistaActiva("notificaciones")}
                actualizacion={actualizacionNotificaciones}
              />

            </div>

          </header>

          <div className="w-full">

            {vistaActiva === "dashboard" && (
              <PanelControlAcademico
                estudiante={estudiante}
                onNavegar={setVistaActiva}
              />
            )}

            {vistaActiva === "academico" && (
              <GestionAcademica
                estudiante={estudiante}
                onActividadesActualizadas={() => setActualizacionNotificaciones((actual) => actual + 1)}
              />
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

      </main>

    </div>
  );
}

export default App;
