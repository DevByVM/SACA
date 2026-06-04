import { useCallback, useEffect, useRef, useState } from "react";
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
  const [mensaje, setMensaje] = useState(null);
  const [cargando, setCargando] = useState(false);

  const [disponibilidades, setDisponibilidades] = useState([]);
  const [ciclos, setCiclos] = useState([]);

  const [form, setForm] = useState(disponibilidadInicial);
  const [editando, setEditando] = useState({ tipo: null, id: null });

  const cargarDatos = useCallback(async () => {
    if (!estudianteId.trim()) return;

    try {
      setCargando(true);
      setMensaje(null);
      const [disponibilidadesData, ciclosData] = await Promise.all([
        listarDisponibilidades(estudianteId),
        listarCiclos(estudianteId),
      ]);
      setDisponibilidades(disponibilidadesData);
      setCiclos(ciclosData);
    } catch (error) {
      setMensaje({ tipo: "error", texto: error.message });
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
      setMensaje(null);
      if (editando.tipo === "disponibilidad") {
        await actualizarDisponibilidad(editando.id, estudianteId, payload);
      } else {
        await crearDisponibilidad(estudianteId, payload);
      }

      setForm(disponibilidadInicial);
      setEditando({ tipo: null, id: null });
      setMensaje({ tipo: "exito", texto: "Disponibilidad guardada correctamente" });
      await cargarDatos();
    } catch (error) {
      setMensaje({ tipo: "error", texto: error.message });
    }
  }

  async function eliminar(id) {
    try {
      setMensaje(null);
      await eliminarDisponibilidad(id, estudianteId);
      setMensaje({ tipo: "exito", texto: "Registro eliminado" });
      await cargarDatos();
    } catch (error) {
      setMensaje({ tipo: "error", texto: error.message });
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
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="text-2xl font-black text-[#430000]">Disponibilidad Semanal</h2>
          <p className="text-sm text-[#430000]/70">
            Gestiona tus bloques de tiempo disponibles por cada ciclo académico.
          </p>
        </div>
      </div>

      {mensaje && (
        <div
          className={`rounded-lg border px-4 py-3 text-sm font-semibold ${mensaje.tipo === "error"
            ? "border-red-200 bg-red-50 text-red-800"
            : "border-green-200 bg-green-50 text-green-800"
            }`}
        >
          {mensaje.texto}
        </div>
      )}

      {cargando && <p className="text-sm text-[#430000]/70">Cargando información...</p>}

      <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
        {/* Formulario */}
        <Panel titulo={editando.tipo ? "Editar Disponibilidad" : "Nueva Disponibilidad"}>
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
              options={["CLASE", "ESTUDIO", "TRABAJO", "LIBRE"]}
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
            <BotonGuardar />
            {editando.tipo && (
              <button
                type="button"
                className="w-full text-sm font-semibold text-[#430000]/70 hover:underline mt-2"
                onClick={() => {
                  setEditando({ tipo: null, id: null });
                  setForm(disponibilidadInicial);
                }}
              >
                Cancelar Edición
              </button>
            )}
          </form>
        </Panel>

        {/* Tabla de Registros */}
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
      </div>
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

function BotonGuardar() {
  return (
    <button className="w-full rounded-lg bg-[#960000] px-4 py-2 font-bold text-white hover:bg-[#7a0000] transition" type="submit">
      Guardar Disponibilidad
    </button>
  );
}

export default GestionDisponibilidad;