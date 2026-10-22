import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
// Importación de las funciones del archivo de servicio independiente
import {
  listarDisponibilidades,
  crearDisponibilidad,
  actualizarDisponibilidad,
  eliminarDisponibilidad,
  listarCiclos
} from "../services/disponibilidadService"; // <- Ajusta esta ruta según tu estructura
import Boton from "./ui/Boton";
import Campo from "./ui/Campo";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Insignia from "./ui/Insignia";
import Modal from "./ui/Modal";
import Selector from "./ui/Selector";
import Tabla from "./ui/Tabla";
import Tarjeta from "./ui/Tarjeta";

// El input time entrega "HH:mm" y el backend puede devolver "HH:mm:ss";
// se completan los segundos para que la comparación de texto sea válida.
const normalizarHora = (hora) => (String(hora).length === 5 ? `${hora}:00` : String(hora));

const disponibilidadInicial = {
  diaSemana: "LUNES",
  horaInicio: "",
  horaFin: "",
  tipoBloque: "CLASE",
  cicloId: "",
};

function GestionDisponibilidad({ estudiante }) {
  const estudianteId = estudiante?.id ? String(estudiante.id) : "";
  const [cargando, setCargando] = useState(false);

  const [disponibilidades, setDisponibilidades] = useState([]);
  const [ciclos, setCiclos] = useState([]);

  const [form, setForm] = useState(disponibilidadInicial);
  const [editando, setEditando] = useState({ tipo: null, id: null });
  const [formularioAbierto, setFormularioAbierto] = useState(false);

  const cargarDatos = useCallback(async () => {
    if (!estudianteId.trim()) return;

    try {
      setCargando(true);
      const [disponibilidadesData, ciclosData] = await Promise.all([
        listarDisponibilidades(estudianteId),
        listarCiclos(estudianteId),
      ]);
      setDisponibilidades(disponibilidadesData);
      setCiclos(ciclosData);
    } catch (error) {
      toast.error(error.message || "No se pudo cargar la disponibilidad.");
    } finally {
      setCargando(false);
    }
  }, [estudianteId]);

  useEffect(() => {
    cargarDatos();
  }, [cargarDatos]);

  async function guardarDisponibilidad(event) {
    event.preventDefault();

    if (normalizarHora(form.horaInicio) >= normalizarHora(form.horaFin)) {
      toast.warning("La hora de inicio debe ser menor que la hora de fin");
      return;
    }

    const payload = {
      ...form,
      cicloId: Number(form.cicloId),
      estudianteId: Number(estudianteId),
    };

    try {
      if (editando.tipo === "disponibilidad") {
        await actualizarDisponibilidad(editando.id, estudianteId, payload);
      } else {
        await crearDisponibilidad(estudianteId, payload);
      }

      setForm(disponibilidadInicial);
      setEditando({ tipo: null, id: null });
      setFormularioAbierto(false);
      toast.success("Disponibilidad guardada correctamente");
      await cargarDatos();
    } catch (error) {
      const mensajeBackend = error.response?.data?.message || error.message || "Error al procesar la solicitud";
      toast.error(mensajeBackend);
    }
  }

  async function eliminar(id) {
    try {
      await eliminarDisponibilidad(id, estudianteId);
      toast.success("Registro eliminado");
      await cargarDatos();
    } catch (error) {
      const mensajeBackend = error.response?.data?.message || error.message || "No se pudo eliminar el registro";
      toast.error(mensajeBackend);
    }
  }

  function editarDisponibilidad(disponibilidad) {
    setEditando({ tipo: "disponibilidad", id: disponibilidad.id });
    setForm({
      diaSemana: disponibilidad.diaSemana,
      horaInicio: disponibilidad.horaInicio,
      horaFin: disponibilidad.horaFin,
      tipoBloque: disponibilidad.tipoBloque,
      cicloId: disponibilidad.cicloId,
    });
    setFormularioAbierto(true);
  }

  function nuevoRegistro() {
    setEditando({ tipo: null, id: null });
    setForm(disponibilidadInicial);
    setFormularioAbierto(true);
  }

  function cerrarFormulario() {
    setFormularioAbierto(false);
    setEditando({ tipo: null, id: null });
    setForm(disponibilidadInicial);
  }

  const filas = disponibilidades.map((disp) => {
    const cicloAsociado = ciclos.find((c) => Number(c.id) === Number(disp.cicloId));
    const cicloLabel = cicloAsociado ? `${cicloAsociado.nombre} (${cicloAsociado.anio})` : "No asignado";
    const horario = `${disp.horaInicio} - ${disp.horaFin}`;

    return {
      id: disp.id,
      celdas: [
        disp.diaSemana,
        horario,
        disp.tipoBloque ? <Insignia>{disp.tipoBloque}</Insignia> : disp.tipoBloque,
        cicloLabel,
        <div className="flex gap-2">
          <Boton
            variante="secundario"
            tamano="sm"
            aria-label={`Editar bloque del ${disp.diaSemana}, ${horario}`}
            onClick={() => editarDisponibilidad(disp)}
          >
            Editar
          </Boton>
          <Boton
            variante="peligro"
            tamano="sm"
            aria-label={`Eliminar bloque del ${disp.diaSemana}, ${horario}`}
            onClick={() => eliminar(disp.id)}
          >
            Eliminar
          </Boton>
        </div>,
      ],
    };
  });

  // La tabla solo muestra la carga mientras aún no hay filas; en las recargas
  // posteriores (tras guardar o eliminar) se mantienen visibles para no parpadear.
  const cargaInicial = cargando && disponibilidades.length === 0;

  return (
    <section className="space-y-5">
      <EncabezadoModulo
        titulo="Disponibilidad Semanal"
        descripcion="Gestiona tus bloques de tiempo disponibles por cada ciclo académico."
        acciones={
          <Boton icono="fa-solid fa-plus" onClick={nuevoRegistro}>
            Nueva disponibilidad
          </Boton>
        }
      />

      <Tarjeta
        titulo="Bloques de Disponibilidad Registrados"
        acciones={
          cargando && !cargaInicial && (
            <span className="text-sm text-ink-600">Cargando información...</span>
          )
        }
      >
        <Tabla
          columnas={["Día", "Horario", "Tipo de Bloque", "Ciclo", { titulo: "Acciones", className: "whitespace-nowrap" }]}
          filas={filas}
          anchoTabla="w-full min-w-[680px]"
          cargando={cargaInicial}
        />
      </Tarjeta>

      <Modal
        abierto={formularioAbierto}
        titulo={editando.tipo ? "Editar disponibilidad" : "Nueva disponibilidad"}
        descripcion="Completa los datos del bloque."
        etiquetaCerrar="Cerrar formulario"
        onCerrar={cerrarFormulario}
      >
        <form className="space-y-3" onSubmit={guardarDisponibilidad}>
          <Selector
            label="Ciclo Académico"
            value={form.cicloId}
            onValorChange={(cicloId) => setForm({ ...form, cicloId })}
            options={ciclos.map((ciclo) => ({
              value: ciclo.id,
              label: `${ciclo.nombre} - ${ciclo.anio}`,
            }))}
          />
          <Selector
            label="Día de la Semana"
            value={form.diaSemana}
            onValorChange={(diaSemana) => setForm({ ...form, diaSemana })}
            options={["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"]}
          />
          <Selector
            label="Tipo de Bloque"
            value={form.tipoBloque}
            onValorChange={(tipoBloque) => setForm({ ...form, tipoBloque })}
            options={["CLASE", "ESTUDIO", "LIBRE"]}
          />
          <Campo
            label="Hora Inicio"
            type="time"
            value={form.horaInicio}
            onValorChange={(horaInicio) => setForm({ ...form, horaInicio })}
          />
          <Campo
            label="Hora Fin"
            type="time"
            value={form.horaFin}
            onValorChange={(horaFin) => setForm({ ...form, horaFin })}
          />
          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <Boton variante="secundario" onClick={cerrarFormulario}>
              Cancelar
            </Boton>
            <Boton type="submit">Guardar disponibilidad</Boton>
          </div>
        </form>
      </Modal>
    </section>
  );
}

export default GestionDisponibilidad;
