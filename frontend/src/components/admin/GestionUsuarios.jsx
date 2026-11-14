import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  cambiarEstadoUsuarioAdmin,
  crearUsuarioAdmin,
  listarUsuariosAdmin,
  actualizarUsuarioAdmin,
  solicitarRestablecimientoAdmin,
} from "../../api/adminApi";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import Tarjeta from "../ui/Tarjeta";
import Tabla from "../ui/Tabla";
import Aviso from "../ui/Aviso";

function GestionUsuarios() {
  const [usuarios, setUsuarios] = useState([]);
  const [carnet, setCarnet] = useState("");
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);
  const [editando, setEditando] = useState(null);
  const [restableciendo, setRestableciendo] = useState(null);
  const [errorEdicion, setErrorEdicion] = useState("");

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
      setProcesando(true);
      const actualizado = await cambiarEstadoUsuarioAdmin(usuario.id, !usuario.habilitada);
      setUsuarios((actuales) => actuales.map((item) => item.id === usuario.id ? actualizado : item));
      toast.success(actualizado.habilitada ? "Cuenta habilitada." : "Cuenta inhabilitada.");
    } catch (error) {
      toast.error(error.message || "No se pudo actualizar la cuenta.");
    } finally {
      setProcesando(false);
    }
  }

  async function guardarEdicion(evento) {
    evento.preventDefault();
    setProcesando(true);
    setErrorEdicion("");
    try {
      const actualizado = await actualizarUsuarioAdmin(editando.id, {
        nombre: editando.nombre.trim(),
        carnet: editando.carnet.trim(),
        correoInstitucional: editando.correoInstitucional.trim(),
        habilitada: editando.habilitada,
      });
      setUsuarios((actuales) => actuales.map((item) => item.id === actualizado.id ? actualizado : item));
      setEditando(null);
      toast.success("Datos del estudiante guardados.");
    } catch (error) {
      setErrorEdicion(error.message || "No se pudieron guardar los datos.");
    } finally {
      setProcesando(false);
    }
  }

  async function restablecer() {
    setProcesando(true);
    try {
      const resultado = await solicitarRestablecimientoAdmin(restableciendo.id);
      toast.success(resultado.mensaje);
      setRestableciendo(null);
    } catch (error) {
      toast.error(error.message || "No se pudo enviar el enlace.");
    } finally {
      setProcesando(false);
    }
  }

  const cambiarCampo = (campo) => (valor) => setEditando((anterior) => ({ ...anterior, [campo]: valor }));

  const filas = usuarios.map((usuario) => ({
    id: usuario.id,
    celdas: [
      usuario.nombre || "Pendiente de activación",
      usuario.carnet,
      usuario.correoInstitucional,
      usuario.estadoCorreo,
      usuario.habilitada ? "Habilitada" : "Inhabilitada",
      <div key="acciones" className="flex flex-wrap gap-2">
      <Boton variante="secundario" disabled={procesando} onClick={() => {
        setErrorEdicion("");
        setRestableciendo(null);
        setEditando({ ...usuario, nombre: usuario.nombre || "" });
      }}>Editar</Boton>
      <Boton
        disabled={procesando}
        variante={usuario.habilitada ? "secundario" : "primario"}
        onClick={() => cambiarEstado(usuario)}
      >
        {usuario.habilitada ? "Inhabilitar" : "Habilitar"}
      </Boton>
      <Boton variante="secundario" disabled={procesando || usuario.estadoCorreo === "PENDIENTE"}
        onClick={() => { setEditando(null); setRestableciendo(usuario); }}>
        Restablecer contraseña
      </Boton>
      </div>,
    ],
  }));

  return (
    <div className="space-y-6">
      {editando && <Tarjeta titulo="Editar estudiante">
        <form onSubmit={guardarEdicion} className="space-y-4">
          <Campo label="Nombre completo" value={editando.nombre} maxLength={120}
            required={editando.estadoCorreo !== "PENDIENTE"} onValorChange={cambiarCampo("nombre")} disabled={procesando} />
          <Campo label="Carnet del estudiante" value={editando.carnet} maxLength={7}
            pattern="[A-Za-z]{2}[0-9]{5}" onValorChange={cambiarCampo("carnet")} disabled={procesando} />
          <Campo label="Correo institucional" type="email" value={editando.correoInstitucional} maxLength={120}
            onValorChange={cambiarCampo("correoInstitucional")} disabled={procesando}
            ayuda="Usa un correo @ues.edu.sv. Al cambiarlo, se invalidan los enlaces de restablecimiento anteriores. Si la cuenta está pendiente, se envía una nueva activación." />
          <label className="flex items-center gap-2"><input type="checkbox" checked={editando.habilitada}
            disabled={procesando} onChange={(e) => cambiarCampo("habilitada")(e.target.checked)} />Cuenta habilitada</label>
          {errorEdicion && <Aviso tono="peligro">{errorEdicion}</Aviso>}
          <div className="flex gap-2">
            <Boton type="submit" disabled={procesando}>{procesando ? "Guardando..." : "Guardar cambios"}</Boton>
            <Boton variante="secundario" disabled={procesando} onClick={() => setEditando(null)}>Cancelar</Boton>
          </div>
        </form>
      </Tarjeta>}
      {restableciendo && <Tarjeta titulo="Solicitar restablecimiento">
        <p className="mb-4 text-sm">Se enviará un enlace de un solo uso, válido durante una hora, a {restableciendo.correoInstitucional}.
          El estudiante elegirá la nueva contraseña. La contraseña actual y el estado de la cuenta se conservan hasta completar el proceso.</p>
        <div className="flex gap-2">
          <Boton disabled={procesando} onClick={restablecer}>{procesando ? "Enviando..." : "Enviar enlace"}</Boton>
          <Boton variante="secundario" disabled={procesando} onClick={() => setRestableciendo(null)}>Cancelar</Boton>
        </div>
      </Tarjeta>}
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
