import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "react-toastify";
// Importación de las funciones del archivo de servicio independiente
import {
  listarDisponibilidades,
  crearDisponibilidad,
  actualizarDisponibilidad,
  eliminarDisponibilidad,
  listarCiclos
} from "../services/disponibilidadService"; // <- Ajusta esta ruta según tu estructura

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

  useEffect(() => {
    if (!formularioAbierto) return undefined;

    function cerrarConEscape(event) {
      if (event.key === "Escape") cerrarFormulario();
    }

    window.addEventListener("keydown", cerrarConEscape);
    return () => window.removeEventListener("keydown", cerrarConEscape);
  }, [formularioAbierto]);

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="text-2xl font-black text-[#430000]">Disponibilidad Semanal</h2>
          <p className="text-sm text-[#430000]/70">
            Gestiona tus bloques de tiempo disponibles por cada ciclo académico.
          </p>
        </div>
        <button
          className="inline-flex items-center justify-center gap-2 self-start rounded-lg bg-[#960000] px-4 py-2 text-sm font-bold text-white transition hover:bg-[#750000] md:self-auto"
          type="button"
          onClick={nuevoRegistro}
        >
          <i className="fa-solid fa-plus" aria-hidden="true"></i>
          Nueva disponibilidad
        </button>
      </div>

      {cargando && <p className="text-sm text-[#430000]/70">Cargando información...</p>}

      <Tabla
        columnas={["Día", "Horario", "Tipo de Bloque", "Ciclo"]}
        filas={disponibilidades.map((disp) => {
          const cicloAsociado = ciclos.find((c) => Number(c.id) === Number(disp.cicloId));
          const cicloLabel = cicloAsociado ? `${cicloAsociado.nombre} (${cicloAsociado.anio})` : "No asignado";

          return {
            id: disp.id,
            celdas: [
              disp.diaSemana,
              `${disp.horaInicio} - ${disp.horaFin}`,
              disp.tipoBloque,
              cicloLabel,
            ],
            item: disp,
          };
        })}
        onEdit={editarDisponibilidad}
        onDelete={eliminar}
      />

      {formularioAbierto && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) cerrarFormulario();
          }}
        >
          <div
            aria-labelledby="titulo-formulario-disponibilidad"
            aria-modal="true"
            className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl sm:p-7"
            role="dialog"
          >
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h3 id="titulo-formulario-disponibilidad" className="text-xl font-black text-[#430000]">
                  {editando.tipo ? "Editar disponibilidad" : "Nueva disponibilidad"}
                </h3>
                <p className="mt-1 text-sm text-[#430000]/60">Completa los datos del bloque.</p>
              </div>
              <button
                aria-label="Cerrar formulario"
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[#430000]/70 transition hover:bg-[#430000]/10"
                type="button"
                onClick={cerrarFormulario}
              >
                <i className="fa-solid fa-xmark" aria-hidden="true"></i>
              </button>
            </div>
            <form className="space-y-3" onSubmit={guardarDisponibilidad}>
            <Select
              label="Ciclo Académico"
              value={form.cicloId}
              onChange={(cicloId) => setForm({ ...form, cicloId })}
              options={ciclos.map((ciclo) => ({
                value: ciclo.id,
                label: `${ciclo.nombre} - ${ciclo.anio}`,
              }))}
            />
            <Select
              label="Día de la Semana"
              value={form.diaSemana}
              onChange={(diaSemana) => setForm({ ...form, diaSemana })}
              options={["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"]}
            />
            <Select
              label="Tipo de Bloque"
              value={form.tipoBloque}
              onChange={(tipoBloque) => setForm({ ...form, tipoBloque })}
              options={["CLASE", "ESTUDIO", "LIBRE"]}
            />
            <Campo
              label="Hora Inicio"
              type="time"
              value={form.horaInicio}
              onChange={(horaInicio) => setForm({ ...form, horaInicio })}
            />
            <Campo
              label="Hora Fin"
              type="time"
              value={form.horaFin}
              onChange={(horaFin) => setForm({ ...form, horaFin })}
            />
            <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
              <button
                className="rounded-lg border border-[#430000]/20 px-4 py-2 font-bold text-[#430000] transition hover:bg-[#430000]/5"
                type="button"
                onClick={cerrarFormulario}
              >
                Cancelar
              </button>
              <button className="rounded-lg bg-[#960000] px-4 py-2 font-bold text-white transition hover:bg-[#750000]" type="submit">
                Guardar disponibilidad
              </button>
            </div>
          </form>
          </div>
        </div>
      )}
    </section>
  );
}

/* ==========================================================================
   SUBCOMPONENTES REUTILIZABLES
   ========================================================================== */

function Tabla({ columnas, filas, onEdit, onDelete }) {
  return (
    <Panel titulo="Bloques de Disponibilidad Registrados">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left text-sm">
          <thead className="bg-[#430000]/10 text-[#430000]">
            <tr>
              {columnas.map((columna) => (
                <th key={columna} className="px-3 py-2">{columna}</th>
              ))}
              <th className="px-3 py-2">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {filas.map((fila) => (
              <tr key={fila.id} className="border-b border-[#430000]/10">
                {fila.celdas.map((celda, index) => (
                  <td key={`${fila.id}-${index}`} className="px-3 py-2">{celda || "Sin dato"}</td>
                ))}
                <td className="flex gap-2 px-3 py-2">
                  <button
                    className="rounded bg-[#430000]/10 px-3 py-1 font-semibold text-[#430000] hover:bg-[#430000]/20 transition"
                    type="button"
                    onClick={() => onEdit(fila.item)}
                  >
                    Editar
                  </button>
                  <button
                    className="rounded bg-[#960000] px-3 py-1 font-semibold text-white hover:bg-[#7a0000] transition"
                    type="button"
                    onClick={() => onDelete(fila.id)}
                  >
                    Eliminar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filas.length === 0 && <p className="p-4 text-sm text-[#430000]/60">No hay registros.</p>}
      </div>
    </Panel>
  );
}

function Panel({ titulo, children }) {
  return (
    <div className="rounded-xl border border-[#430000]/20 bg-white p-5 shadow-sm">
      <h3 className="mb-4 text-lg font-black text-[#430000]">{titulo}</h3>
      {children}
    </div>
  );
}

function Campo({ label, value, onChange, type = "text", required = true }) {
  const inputRef = useRef(null);
  const hasPicker = type === "date" || type === "time";
  const pickerIcon = type === "date" ? "fa-calendar-days" : "fa-clock";

  function abrirPicker() {
    if (inputRef.current?.showPicker) {
      inputRef.current.showPicker();
      return;
    }
    inputRef.current?.focus();
  }

  return (
    <label className="block text-sm font-semibold text-[#430000]">
      {label}
      <div className="relative mt-1">
        <input
          ref={inputRef}
          required={required}
          className={`block w-full rounded-lg border border-[#430000]/20 bg-white px-3 py-2 text-[#430000] outline-none transition focus:border-[#960000] focus:ring-2 focus:ring-[#960000]/20 ${hasPicker ? "pr-11" : ""
            }`}
          type={type}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />

        {hasPicker && (
          <button
            aria-label={`Abrir selector de ${label.toLowerCase()}`}
            className="absolute inset-y-1 right-1 flex w-9 items-center justify-center rounded-md text-[#960000] transition hover:bg-[#960000]/10"
            type="button"
            onClick={abrirPicker}
          >
            <i className={`fa-solid ${pickerIcon}`}></i>
          </button>
        )}
      </div>
    </label>
  );
}

function Select({ label, value, onChange, options }) {
  return (
    <label className="block text-sm font-semibold text-[#430000]">
      {label}
      <select
        required
        className="mt-1 block w-full rounded-lg border border-[#430000]/20 px-3 py-2 text-[#430000] bg-white outline-none focus:border-[#960000] focus:ring-2 focus:ring-[#960000]/20"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">Seleccione</option>
        {options.map((option) => {
          const valueOption = typeof option === "string" ? option : option.value;
          const labelOption = typeof option === "string" ? option : option.label;
          return <option key={valueOption} value={valueOption}>{labelOption}</option>;
        })}
      </select>
    </label>
  );
}

export default GestionDisponibilidad;
