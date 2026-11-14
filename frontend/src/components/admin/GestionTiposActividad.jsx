import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  eliminarTipoActividadAdmin,
  guardarTipoActividadAdmin,
  listarTiposActividadAdmin,
} from "../../api/adminApi";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import DialogoConfirmacion from "../ui/DialogoConfirmacion";
import Tarjeta from "../ui/Tarjeta";
import Tabla from "../ui/Tabla";

function GestionTiposActividad() {
  const [tipos, setTipos] = useState([]);
  const [nombre, setNombre] = useState("");
  const [editandoId, setEditandoId] = useState(null);
  const [tipoAEliminar, setTipoAEliminar] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);

  const cargarTipos = useCallback(async () => {
    try {
      setCargando(true);
      setTipos(await listarTiposActividadAdmin());
    } catch (error) {
      toast.error(error.message || "No se pudo cargar el catálogo de tipos.");
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargarTipos();
  }, [cargarTipos]);

  async function guardar(event) {
    event.preventDefault();
    try {
      setProcesando(true);
      const tipo = await guardarTipoActividadAdmin({ nombre: nombre.trim() }, editandoId);
      setTipos((actuales) => {
        const siguientes = editandoId
          ? actuales.map((item) => item.id === editandoId ? tipo : item)
          : [...actuales, tipo];
        return siguientes.sort((a, b) => a.nombre.localeCompare(b.nombre));
      });
      setNombre("");
      setEditandoId(null);
      toast.success("Tipo de actividad guardado.");
    } catch (error) {
      toast.error(error.message || "No se pudo guardar el tipo de actividad.");
    } finally {
      setProcesando(false);
    }
  }

  async function eliminar() {
    if (!tipoAEliminar) return;
    try {
      setProcesando(true);
      await eliminarTipoActividadAdmin(tipoAEliminar.id);
      setTipos((actuales) => actuales.filter((tipo) => tipo.id !== tipoAEliminar.id));
      toast.success("Tipo de actividad eliminado.");
      setTipoAEliminar(null);
    } catch (error) {
      toast.error(error.message || "No se pudo eliminar el tipo de actividad.");
    } finally {
      setProcesando(false);
    }
  }

  function editar(tipo) {
    setEditandoId(tipo.id);
    setNombre(tipo.nombre);
  }

  function cancelarEdicion() {
    setEditandoId(null);
    setNombre("");
  }

  const filas = tipos.map((tipo) => ({
    id: tipo.id,
    celdas: [
      tipo.nombre,
      tipo.activo ? "Activo" : "Inactivo",
      <div key="acciones" className="flex flex-wrap gap-2">
        <Boton variante="secundario" onClick={() => editar(tipo)}>Editar</Boton>
        <Boton variante="peligro" onClick={() => setTipoAEliminar(tipo)}>Eliminar</Boton>
      </div>,
    ],
  }));

  return (
    <div className="space-y-6">
      <Tarjeta titulo={editandoId ? "Editar tipo de actividad" : "Agregar tipo de actividad"}>
        <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={guardar}>
          <Campo
            className="flex-1"
            label="Nombre"
            value={nombre}
            onValorChange={setNombre}
            maxLength={120}
            required
          />
          <Boton type="submit" disabled={procesando || !nombre.trim()}>
            {procesando ? "Guardando..." : editandoId ? "Guardar cambios" : "Agregar tipo"}
          </Boton>
          {editandoId && (
            <Boton variante="secundario" onClick={cancelarEdicion}>Cancelar</Boton>
          )}
        </form>
      </Tarjeta>

      <Tarjeta titulo="Catálogo de tipos de actividad">
        <Tabla
          columnas={["Nombre", "Estado", "Acciones"]}
          filas={filas}
          cargando={cargando}
          mensajeCarga="Cargando tipos..."
          mensajeVacio="Todavía no hay tipos de actividad."
        />
      </Tarjeta>

      <DialogoConfirmacion
        abierto={Boolean(tipoAEliminar)}
        titulo="¿Eliminar tipo de actividad?"
        mensaje={`Se eliminará "${tipoAEliminar?.nombre}". No se puede eliminar si está asociado a actividades o preferencias de notificación.`}
        textoConfirmar="Eliminar"
        onConfirmar={eliminar}
        onCancelar={() => setTipoAEliminar(null)}
        procesando={procesando}
      />
    </div>
  );
}

export default GestionTiposActividad;
