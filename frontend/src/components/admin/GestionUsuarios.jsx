import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  cambiarEstadoUsuarioAdmin,
  crearUsuarioAdmin,
  listarUsuariosAdmin,
} from "../../api/adminApi";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import Tarjeta from "../ui/Tarjeta";
import Tabla from "../ui/Tabla";

function GestionUsuarios() {
  const [usuarios, setUsuarios] = useState([]);
  const [carnet, setCarnet] = useState("");
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);

  const cargarUsuarios = useCallback(async () => {
    try {
      setCargando(true);
      setUsuarios(await listarUsuariosAdmin());
    } catch (error) {
      toast.error(error.message || "No se pudieron cargar las cuentas.");
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargarUsuarios();
  }, [cargarUsuarios]);

  async function crearUsuario(event) {
    event.preventDefault();
    try {
      setProcesando(true);
      const resultado = await crearUsuarioAdmin(carnet.trim());
      toast.info(`${resultado.mensaje} ${resultado.correoInstitucional}`);
      setCarnet("");
      await cargarUsuarios();
    } catch (error) {
      toast.error(error.message || "No se pudo crear la cuenta.");
    } finally {
      setProcesando(false);
    }
  }

  async function cambiarEstado(usuario) {
    try {
      const actualizado = await cambiarEstadoUsuarioAdmin(usuario.id, !usuario.habilitada);
      setUsuarios((actuales) => actuales.map((item) => item.id === usuario.id ? actualizado : item));
      toast.success(actualizado.habilitada ? "Cuenta habilitada." : "Cuenta inhabilitada.");
    } catch (error) {
      toast.error(error.message || "No se pudo actualizar la cuenta.");
    }
  }

  const filas = usuarios.map((usuario) => ({
    id: usuario.id,
    celdas: [
      usuario.nombre || "Pendiente de activación",
      usuario.carnet,
      usuario.correoInstitucional,
      usuario.estadoCorreo,
      usuario.habilitada ? "Habilitada" : "Inhabilitada",
      <Boton
        key="estado"
        variante={usuario.habilitada ? "secundario" : "primario"}
        onClick={() => cambiarEstado(usuario)}
      >
        {usuario.habilitada ? "Inhabilitar" : "Habilitar"}
      </Boton>,
    ],
  }));

  return (
    <div className="space-y-6">
      <Tarjeta titulo="Crear cuenta de estudiante">
        <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={crearUsuario}>
          <Campo
            className="flex-1"
            label="Carnet"
            value={carnet}
            onValorChange={setCarnet}
            placeholder="AP23001"
            pattern="[A-Za-z]{2}[0-9]{5}"
            maxLength={7}
            ayuda="Se enviará un enlace de activación al correo institucional derivado del carnet."
          />
          <Boton type="submit" disabled={procesando || !carnet.trim()}>
            {procesando ? "Enviando..." : "Crear cuenta"}
          </Boton>
        </form>
      </Tarjeta>

      <Tarjeta titulo="Cuentas de estudiantes">
        <Tabla
          columnas={["Nombre", "Carnet", "Correo", "Activación", "Acceso", "Acciones"]}
          filas={filas}
          cargando={cargando}
          mensajeCarga="Cargando cuentas..."
          mensajeVacio="Todavía no hay cuentas de estudiantes."
        />
      </Tarjeta>
    </div>
  );
}

export default GestionUsuarios;
