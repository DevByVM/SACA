import { useCallback, useEffect, useState } from "react";
import {
  actualizarCiclo,
  crearCiclo,
  eliminarCiclo,
  listarCiclos,
} from "../api/ciclosApi";
import {
  actualizarHorario,
  crearHorario,
  eliminarHorario,
  listarHorarios,
} from "../api/horariosApi";
import {
  actualizarMateria,
  crearMateria,
  eliminarMateria,
  listarMaterias,
} from "../api/materiasApi";

const cicloInicial = {
  nombre: "",
  anio: new Date().getFullYear(),
  fechaInicio: "",
  fechaFin: "",
  estado: "ACTIVO",
};

const materiaInicial = {
  cicloAcademicoId: "",
  nombre: "",
  codigo: "",
  grupoTeorico: "",
};

const horarioInicial = {
  materiaInscritaId: "",
  diaSemana: "LUNES",
  horaInicio: "",
  horaFin: "",
  modalidad: "PRESENCIAL",
  docenteTutor: "",
};

function GestionAcademica() {
  const [estudianteId, setEstudianteId] = useState("1");
  const [vista, setVista] = useState("ciclos");
  const [mensaje, setMensaje] = useState(null);
  const [cargando, setCargando] = useState(false);
  const [ciclos, setCiclos] = useState([]);
  const [materias, setMaterias] = useState([]);
  const [horarios, setHorarios] = useState([]);
  const [cicloForm, setCicloForm] = useState(cicloInicial);
  const [materiaForm, setMateriaForm] = useState(materiaInicial);
  const [horarioForm, setHorarioForm] = useState(horarioInicial);
  const [editando, setEditando] = useState({ tipo: null, id: null });

  const cargarDatos = useCallback(async () => {
    if (!estudianteId.trim()) return;

    try {
      setCargando(true);
      setMensaje(null);
      const [ciclosData, materiasData, horariosData] = await Promise.all([
        listarCiclos(estudianteId),
        listarMaterias(estudianteId),
        listarHorarios(estudianteId),
      ]);
      setCiclos(ciclosData);
      setMaterias(materiasData);
      setHorarios(horariosData);
    } catch (error) {
      setMensaje({ tipo: "error", texto: error.message });
    } finally {
      setCargando(false);
    }
  }, [estudianteId]);

  useEffect(() => {
    cargarDatos();
  }, [cargarDatos]);

  async function guardarCiclo(event) {
    event.preventDefault();
    const payload = {
      ...cicloForm,
      estudianteId: Number(estudianteId),
      anio: Number(cicloForm.anio),
    };

    await guardar(async () => {
      if (editando.tipo === "ciclo") {
        await actualizarCiclo(editando.id, estudianteId, payload);
      } else {
        await crearCiclo(payload);
      }
      setCicloForm(cicloInicial);
    }, "Ciclo academico guardado");
  }

  async function guardarMateria(event) {
    event.preventDefault();
    const payload = {
      ...materiaForm,
      cicloAcademicoId: Number(materiaForm.cicloAcademicoId),
    };

    await guardar(async () => {
      if (editando.tipo === "materia") {
        await actualizarMateria(editando.id, estudianteId, payload);
      } else {
        await crearMateria(estudianteId, payload);
      }
      setMateriaForm(materiaInicial);
    }, "Materia guardada");
  }

  async function guardarHorario(event) {
    event.preventDefault();
    const payload = {
      ...horarioForm,
      materiaInscritaId: Number(horarioForm.materiaInscritaId),
    };

    await guardar(async () => {
      if (editando.tipo === "horario") {
        await actualizarHorario(editando.id, estudianteId, payload);
      } else {
        await crearHorario(estudianteId, payload);
      }
      setHorarioForm(horarioInicial);
    }, "Horario guardado");
  }

  async function guardar(accion, texto) {
    try {
      setMensaje(null);
      await accion();
      setEditando({ tipo: null, id: null });
      setMensaje({ tipo: "exito", texto });
      await cargarDatos();
    } catch (error) {
      setMensaje({ tipo: "error", texto: error.message });
    }
  }

  async function eliminar(tipo, id) {
    await guardar(async () => {
      if (tipo === "ciclo") await eliminarCiclo(id, estudianteId);
      if (tipo === "materia") await eliminarMateria(id, estudianteId);
      if (tipo === "horario") await eliminarHorario(id, estudianteId);
    }, "Registro eliminado");
  }

  function editarCiclo(ciclo) {
    setVista("ciclos");
    setEditando({ tipo: "ciclo", id: ciclo.id });
    setCicloForm({
      nombre: ciclo.nombre,
      anio: ciclo.anio,
      fechaInicio: ciclo.fechaInicio,
      fechaFin: ciclo.fechaFin,
      estado: ciclo.estado,
    });
  }

  function editarMateria(materia) {
    setVista("materias");
    setEditando({ tipo: "materia", id: materia.id });
    setMateriaForm({
      cicloAcademicoId: materia.cicloAcademicoId,
      nombre: materia.nombre,
      codigo: materia.codigo,
      grupoTeorico: materia.grupoTeorico,
    });
  }

  function editarHorario(horario) {
    setVista("horarios");
    setEditando({ tipo: "horario", id: horario.id });
    setHorarioForm({
      materiaInscritaId: horario.materiaInscritaId,
      diaSemana: horario.diaSemana,
      horaInicio: horario.horaInicio,
      horaFin: horario.horaFin,
      modalidad: horario.modalidad,
      docenteTutor: horario.docenteTutor ?? "",
    });
  }

  return (
    <section className="space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="text-2xl font-black text-[#430000]">Gestion academica</h2>
          <p className="text-sm text-[#430000]/70">
            Ciclos, materias inscritas y horarios de clase.
          </p>
        </div>
        <label className="text-sm font-semibold text-[#430000]">
          Estudiante ID
          <input
            className="mt-1 block w-32 rounded-lg border border-[#430000]/20 px-3 py-2"
            min="1"
            type="number"
            value={estudianteId}
            onChange={(event) => setEstudianteId(event.target.value)}
          />
        </label>
      </div>

      <div className="flex flex-wrap gap-2">
        {["ciclos", "materias", "horarios"].map((item) => (
          <button
            key={item}
            className={`rounded-lg px-4 py-2 text-sm font-bold ${
              vista === item ? "bg-[#960000] text-white" : "bg-[#430000]/10 text-[#430000]"
            }`}
            type="button"
            onClick={() => setVista(item)}
          >
            {item[0].toUpperCase() + item.slice(1)}
          </button>
        ))}
      </div>

      {mensaje && (
        <div
          className={`rounded-lg border px-4 py-3 text-sm font-semibold ${
            mensaje.tipo === "error"
              ? "border-red-200 bg-red-50 text-red-800"
              : "border-green-200 bg-green-50 text-green-800"
          }`}
        >
          {mensaje.texto}
        </div>
      )}

      {cargando && <p className="text-sm text-[#430000]/70">Cargando informacion...</p>}

      {vista === "ciclos" && (
        <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
          <FormularioCiclo form={cicloForm} setForm={setCicloForm} onSubmit={guardarCiclo} />
          <TablaCiclos ciclos={ciclos} onEdit={editarCiclo} onDelete={(id) => eliminar("ciclo", id)} />
        </div>
      )}

      {vista === "materias" && (
        <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
          <FormularioMateria
            ciclos={ciclos}
            form={materiaForm}
            setForm={setMateriaForm}
            onSubmit={guardarMateria}
          />
          <TablaMaterias
            materias={materias}
            onEdit={editarMateria}
            onDelete={(id) => eliminar("materia", id)}
          />
        </div>
      )}

      {vista === "horarios" && (
        <div className="grid gap-5 xl:grid-cols-[360px_1fr]">
          <FormularioHorario
            materias={materias}
            form={horarioForm}
            setForm={setHorarioForm}
            onSubmit={guardarHorario}
          />
          <TablaHorarios
            horarios={horarios}
            onEdit={editarHorario}
            onDelete={(id) => eliminar("horario", id)}
          />
        </div>
      )}
    </section>
  );
}

function FormularioCiclo({ form, setForm, onSubmit }) {
  return (
    <Panel titulo="Ciclo academico">
      <form className="space-y-3" onSubmit={onSubmit}>
        <Campo label="Nombre" value={form.nombre} onChange={(nombre) => setForm({ ...form, nombre })} />
        <Campo label="Anio" type="number" value={form.anio} onChange={(anio) => setForm({ ...form, anio })} />
        <Campo label="Fecha inicio" type="date" value={form.fechaInicio} onChange={(fechaInicio) => setForm({ ...form, fechaInicio })} />
        <Campo label="Fecha fin" type="date" value={form.fechaFin} onChange={(fechaFin) => setForm({ ...form, fechaFin })} />
        <Select label="Estado" value={form.estado} onChange={(estado) => setForm({ ...form, estado })} options={["PLANIFICADO", "ACTIVO", "FINALIZADO"]} />
        <BotonGuardar />
      </form>
    </Panel>
  );
}

function FormularioMateria({ ciclos, form, setForm, onSubmit }) {
  return (
    <Panel titulo="Materia inscrita">
      <form className="space-y-3" onSubmit={onSubmit}>
        <Select
          label="Ciclo"
          value={form.cicloAcademicoId}
          onChange={(cicloAcademicoId) => setForm({ ...form, cicloAcademicoId })}
          options={ciclos.map((ciclo) => ({ value: ciclo.id, label: `${ciclo.nombre} - ${ciclo.anio}` }))}
        />
        <Campo label="Nombre" value={form.nombre} onChange={(nombre) => setForm({ ...form, nombre })} />
        <Campo label="Codigo" value={form.codigo} onChange={(codigo) => setForm({ ...form, codigo })} />
        <Campo label="Grupo teorico" value={form.grupoTeorico} onChange={(grupoTeorico) => setForm({ ...form, grupoTeorico })} />
        <BotonGuardar />
      </form>
    </Panel>
  );
}

function FormularioHorario({ materias, form, setForm, onSubmit }) {
  return (
    <Panel titulo="Horario de clase">
      <form className="space-y-3" onSubmit={onSubmit}>
        <Select
          label="Materia"
          value={form.materiaInscritaId}
          onChange={(materiaInscritaId) => setForm({ ...form, materiaInscritaId })}
          options={materias.map((materia) => ({ value: materia.id, label: `${materia.codigo} - ${materia.nombre}` }))}
        />
        <Select label="Dia" value={form.diaSemana} onChange={(diaSemana) => setForm({ ...form, diaSemana })} options={["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"]} />
        <Select label="Modalidad" value={form.modalidad} onChange={(modalidad) => setForm({ ...form, modalidad })} options={["PRESENCIAL", "VIRTUAL", "HIBRIDA"]} />
        <Campo label="Hora inicio" type="time" value={form.horaInicio} onChange={(horaInicio) => setForm({ ...form, horaInicio })} />
        <Campo label="Hora fin" type="time" value={form.horaFin} onChange={(horaFin) => setForm({ ...form, horaFin })} />
        <Campo label="Docente o tutor" required={false} value={form.docenteTutor} onChange={(docenteTutor) => setForm({ ...form, docenteTutor })} />
        <BotonGuardar />
      </form>
    </Panel>
  );
}

function TablaCiclos({ ciclos, onEdit, onDelete }) {
  return (
    <Tabla
      columnas={["Nombre", "Anio", "Periodo", "Estado"]}
      filas={ciclos.map((ciclo) => ({
        id: ciclo.id,
        celdas: [ciclo.nombre, ciclo.anio, `${ciclo.fechaInicio} a ${ciclo.fechaFin}`, ciclo.estado],
        item: ciclo,
      }))}
      onEdit={onEdit}
      onDelete={onDelete}
    />
  );
}

function TablaMaterias({ materias, onEdit, onDelete }) {
  return (
    <Tabla
      columnas={["Codigo", "Materia", "Ciclo", "Grupo"]}
      filas={materias.map((materia) => ({
        id: materia.id,
        celdas: [materia.codigo, materia.nombre, materia.cicloAcademicoNombre, materia.grupoTeorico],
        item: materia,
      }))}
      onEdit={onEdit}
      onDelete={onDelete}
    />
  );
}

function TablaHorarios({ horarios, onEdit, onDelete }) {
  return (
    <Tabla
      columnas={["Dia", "Horario", "Materia", "Modalidad"]}
      filas={horarios.map((horario) => ({
        id: horario.id,
        celdas: [horario.diaSemana, `${horario.horaInicio} - ${horario.horaFin}`, horario.materiaNombre, horario.modalidad],
        item: horario,
      }))}
      onEdit={onEdit}
      onDelete={onDelete}
    />
  );
}

function Tabla({ columnas, filas, onEdit, onDelete }) {
  return (
    <Panel titulo="Registros">
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
                  <button className="rounded bg-[#430000]/10 px-3 py-1 font-semibold" type="button" onClick={() => onEdit(fila.item)}>Editar</button>
                  <button className="rounded bg-[#960000] px-3 py-1 font-semibold text-white" type="button" onClick={() => onDelete(fila.id)}>Eliminar</button>
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
  return (
    <label className="block text-sm font-semibold text-[#430000]">
      {label}
      <input
        required={required}
        className="mt-1 block w-full rounded-lg border border-[#430000]/20 px-3 py-2"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function Select({ label, value, onChange, options }) {
  return (
    <label className="block text-sm font-semibold text-[#430000]">
      {label}
      <select
        required
        className="mt-1 block w-full rounded-lg border border-[#430000]/20 px-3 py-2"
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
    <button className="w-full rounded-lg bg-[#960000] px-4 py-2 font-bold text-white" type="submit">
      Guardar
    </button>
  );
}

export default GestionAcademica;
