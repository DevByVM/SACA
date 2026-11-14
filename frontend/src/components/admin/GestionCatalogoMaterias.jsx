import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  cambiarEstadoMateriaCatalogo,
  guardarMateriaCatalogo,
  listarCatalogoAdmin,
} from "../../api/adminApi";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import Tarjeta from "../ui/Tarjeta";
import Tabla from "../ui/Tabla";

const FORMULARIO_VACIO = { codigo: "", nombre: "" };

function GestionCatalogoMaterias() {
  const [materias, setMaterias] = useState([]);
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO);
  const [editandoId, setEditandoId] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);

  const cargarMaterias = useCallback(async () => {
    try {
      setCargando(true);
      setMaterias(await listarCatalogoAdmin());
    } catch (error) {
      toast.error(error.message || "No se pudo cargar el catálogo.");
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargarMaterias();
  }, [cargarMaterias]);

  async function guardar(event) {
    event.preventDefault();
    try {
      setGuardando(true);
      const materia = await guardarMateriaCatalogo({
        codigo: formulario.codigo.trim(),
        nombre: formulario.nombre.trim(),
      }, editandoId);
      setMaterias((actuales) => editandoId
        ? actuales.map((item) => item.id === editandoId ? materia : item)
        : [...actuales, materia].sort((a, b) => a.nombre.localeCompare(b.nombre)));
      setFormulario(FORMULARIO_VACIO);
      setEditandoId(null);
      toast.success("Materia del catálogo guardada.");
    } catch (error) {
      toast.error(error.message || "No se pudo guardar la materia.");
    } finally {
      setGuardando(false);
    }
  }

  async function cambiarEstado(materia) {
    try {
      const actualizada = await cambiarEstadoMateriaCatalogo(materia.id, !materia.activa);
      setMaterias((actuales) => actuales.map((item) => item.id === materia.id ? actualizada : item));
      toast.success(actualizada.activa ? "Materia habilitada." : "Materia inhabilitada.");
    } catch (error) {
      toast.error(error.message || "No se pudo cambiar el estado.");
    }
  }

  function editar(materia) {
    setEditandoId(materia.id);
    setFormulario({ codigo: materia.codigo, nombre: materia.nombre });
  }

  function cancelarEdicion() {
    setEditandoId(null);
    setFormulario(FORMULARIO_VACIO);
  }

  const filas = materias.map((materia) => ({
    id: materia.id,
    celdas: [
      materia.codigo,
      materia.nombre,
      materia.activa ? "Activa" : "Inactiva",
      <div key="acciones" className="flex flex-wrap gap-2">
        <Boton variante="secundario" onClick={() => editar(materia)}>Editar</Boton>
        <Boton
          variante={materia.activa ? "secundario" : "primario"}
          onClick={() => cambiarEstado(materia)}
        >
          {materia.activa ? "Inhabilitar" : "Habilitar"}
        </Boton>
      </div>,
    ],
  }));

  return (
    <div className="space-y-6">
      <Tarjeta titulo={editandoId ? "Editar materia" : "Agregar materia al catálogo"}>
        <form className="grid gap-4 sm:grid-cols-2" onSubmit={guardar}>
          <Campo
            label="Código oficial"
            value={formulario.codigo}
            onValorChange={(codigo) => setFormulario((actual) => ({ ...actual, codigo }))}
            maxLength={30}
          />
          <Campo
            label="Nombre oficial"
            value={formulario.nombre}
            onValorChange={(nombre) => setFormulario((actual) => ({ ...actual, nombre }))}
            maxLength={120}
          />
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <Boton type="submit" disabled={guardando}>
              {guardando ? "Guardando..." : editandoId ? "Guardar cambios" : "Agregar materia"}
            </Boton>
            {editandoId && (
              <Boton variante="secundario" onClick={cancelarEdicion}>Cancelar</Boton>
            )}
          </div>
        </form>
      </Tarjeta>

      <Tarjeta titulo="Catálogo de materias">
        <Tabla
          columnas={["Código", "Nombre", "Estado", "Acciones"]}
          filas={filas}
          cargando={cargando}
          mensajeCarga="Cargando catálogo..."
          mensajeVacio="El catálogo todavía no tiene materias."
        />
      </Tarjeta>
    </div>
  );
}

export default GestionCatalogoMaterias;
