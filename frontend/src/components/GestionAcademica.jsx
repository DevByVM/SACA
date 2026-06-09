import { useCallback, useEffect, useRef, useState } from "react";
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

import {
  getActividadesByEstudianteIdAndCicloActivo,
  crearActividad,
  eliminarActividad,
  actualizarActividad
} from "../api/actividadesApi";

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

const actividadInicial = {
  nombre: "",
  fechaInicio: "",
  fechaEntrega: "",
  tiempoEstimadoHoras: "",
  tipoActividad: "",
  materiaInscritaId: "",
  porcentajeEvaluacion: "5",
  notaObtenida: "",
  estado: "ACTIVO",
  fechaCompletada: null,
};

function obtenerFechaHoraLocalActual() {
  const ahora = new Date();

  const anio = ahora.getFullYear();
  const mes = String(ahora.getMonth() + 1).padStart(2, "0");
  const dia = String(ahora.getDate()).padStart(2, "0");
  const horas = String(ahora.getHours()).padStart(2, "0");
  const minutos = String(ahora.getMinutes()).padStart(2, "0");

  return `${anio}-${mes}-${dia} ${horas}:${minutos}`;
}

function formatFechaLocalDateTime(value) {
  if (!value) return null;

  const fecha = String(value).replace("T", " ");

  return fecha.substring(0, 16);
}


function GestionAcademica({ estudiante }) {
  const estudianteId = estudiante?.id ? String(estudiante.id) : "";
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

  //agregado erick
  const [actividades, setActividades] = useState([]);
  const [actividadForm, setActividadForm] = useState(actividadInicial);

  const cargarDatos = useCallback(async () => {
    if (!estudianteId.trim()) return;

    try {
      setCargando(true);
      setMensaje(null);
      const [ciclosData, materiasData, horariosData, actividadesData] = await Promise.all([
        listarCiclos(estudianteId),
        listarMaterias(estudianteId),
        listarHorarios(estudianteId),
        getActividadesByEstudianteIdAndCicloActivo(estudianteId)
      ]);
      setCiclos(ciclosData);
      setMaterias(materiasData);
      setHorarios(horariosData);
      setActividades(actividadesData);
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

  async function guardarActividad(event) {
    event.preventDefault();
    const payload = {
      ...actividadForm,
      fechaInicio: formatFechaLocalDateTime(actividadForm.fechaInicio),
      fechaEntrega: formatFechaLocalDateTime(actividadForm.fechaEntrega),
      tiempoEstimadoHoras: actividadForm.tiempoEstimadoHoras ? Number(actividadForm.tiempoEstimadoHoras) : null,
      materiaInscritaId: Number(actividadForm.materiaInscritaId),
      fechaCompletada:
        editando.tipo === "actividad" && actividadForm.fechaCompletada
          ? formatFechaLocalDateTime(actividadForm.fechaCompletada)
          : null,

      estado:
        editando.tipo === "actividad"
          ? actividadForm.estado || "ACTIVO"
          : "ACTIVO",

      notaObtenida:
        actividadForm.notaObtenida !== "" &&
        actividadForm.notaObtenida !== null &&
        actividadForm.notaObtenida !== undefined
          ? Number(actividadForm.notaObtenida)
          : null,
    };

    await guardar(async () => {
      if (editando.tipo === "actividad") {
        await actualizarActividad(editando.id, payload);
      } else {
        await crearActividad(payload);
      }
      setActividadForm(actividadInicial);
    }, "Actividad academica guardada");
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
      if (tipo === "actividad") await eliminarActividad(id);
    }, "Registro eliminado");
  }

  async function completarActividad(actividad) {
    const actividadId = actividad.id ?? actividad.idActividad;

    const payload = {
      nombre: actividad.nombre,
      fechaInicio: formatFechaLocalDateTime(actividad.fechaInicio),
      fechaEntrega: formatFechaLocalDateTime(actividad.fechaEntrega),
      tiempoEstimadoHoras: actividad.tiempoEstimadoHoras
        ? Number(actividad.tiempoEstimadoHoras)
        : null,
      tipoActividad: actividad.tipoActividad,
      materiaInscritaId: Number(actividad.materiaInscritaId),
      porcentajeEvaluacion: Number(actividad.porcentajeEvaluacion || 0),
      estado: "COMPLETADA",
      fechaCompletada: obtenerFechaHoraLocalActual(),
      notaObtenida:
        actividad.notaObtenida !== null &&
        actividad.notaObtenida !== undefined &&
        actividad.notaObtenida !== ""
          ? Number(actividad.notaObtenida)
          : null,
    };

    await guardar(async () => {
      await actualizarActividad(actividadId, payload);
    }, "Actividad marcada como completada");
  }

  async function reactivarActividad(actividad) {
    const actividadId = actividad.id ?? actividad.idActividad;

    const payload = {
      nombre: actividad.nombre,
      fechaInicio: formatFechaLocalDateTime(actividad.fechaInicio),
      fechaEntrega: formatFechaLocalDateTime(actividad.fechaEntrega),
      tiempoEstimadoHoras: actividad.tiempoEstimadoHoras
        ? Number(actividad.tiempoEstimadoHoras)
        : null,
      tipoActividad: actividad.tipoActividad,
      materiaInscritaId: Number(actividad.materiaInscritaId),
      porcentajeEvaluacion: Number(actividad.porcentajeEvaluacion || 0),
      estado: "ACTIVO",
      fechaCompletada: null,
      notaObtenida:
        actividad.notaObtenida !== null &&
        actividad.notaObtenida !== undefined &&
        actividad.notaObtenida !== ""
          ? Number(actividad.notaObtenida)
          : null,
    };

    await guardar(async () => {
      await actualizarActividad(actividadId, payload);
    }, "Actividad reactivada");
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

  function editarActividad(actividad) {
    setVista("actividades");
    setEditando({ tipo: "actividad", id: actividad.idActividad });
    setActividadForm({
      nombre: actividad.nombre,
      fechaInicio: actividad.fechaInicio,
      fechaEntrega: actividad.fechaEntrega,
      tiempoEstimadoHoras: actividad.tiempoEstimadoHoras,
      tipoActividad: actividad.tipoActividad,
      materiaInscritaId: actividad.materiaInscritaId,
      porcentajeEvaluacion: actividad.porcentajeEvaluacion,
      notaObtenida: actividad.notaObtenida ?? "",
      estado: actividad.estado || "ACTIVO",
      fechaCompletada: actividad.fechaCompletada || null,
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
      </div>

      <div className="flex flex-wrap gap-2">
        {["ciclos", "materias", "horarios", "actividades"].map((item) => (
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

      {vista === "actividades" && (
        <div className="space-y-5">
          <div className="grid gap-5 xl:grid-cols-[360px_minmax(0,1fr)]">
            <FormularioActividades
              materias={materias}
              form={actividadForm}
              setForm={setActividadForm}
              onSubmit={guardarActividad}
            />

            <div className="min-w-0">
              <TablaActividades 
                materias={materias}
                actividades={actividades} 
                onEdit={editarActividad} 
                onDelete={(id) => eliminar("actividad", id)}
                onComplete={completarActividad}
                onReactivate={reactivarActividad}
              />
            </div>
          </div>

          <ReporteRendimientoPorMateria
            actividades={actividades}
            materias={materias}
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

//form de actividades
function FormularioActividades({ materias, form, setForm, onSubmit }) {
  return (
    <Panel titulo="Actividades Academicas">
      <form className="space-y-3" onSubmit={onSubmit}>
        <Select
          label="Materia inscrita"
          value={form.materiaInscritaId}
          onChange={(materiaInscritaId) => setForm({ ...form, materiaInscritaId })}
          options={materias.map((materia) => ({ value: materia.id, label: `${materia.codigo} - ${materia.nombre}` }))}
        />
        <Select label="Tipo de Actividad" value={form.tipoActividad} onChange={(tipoActividad) => setForm({ ...form, tipoActividad })} options={["PARCIAL", "LABORATORIO", "PROYECTO", "INVESTIGACION"]} />
        <Campo label="Nombre" value={form.nombre} onChange={(nombre) => setForm({ ...form, nombre })} />
        <Campo label="Porcentaje de evaluacion (ej. 10%)" type="number" value={form.porcentajeEvaluacion} onChange={(porcentajeEvaluacion) => setForm({ ...form, porcentajeEvaluacion })} />
        <Campo label="Fecha inicio" type="datetime-local" value={form.fechaInicio} onChange={(fechaInicio) => setForm({ ...form, fechaInicio })} />
        <Campo label="Fecha Entrega" type="datetime-local" value={form.fechaEntrega} onChange={(fechaEntrega) => setForm({ ...form, fechaEntrega })} />
        <Campo label="Tiempo estimado (horas)" type="number" value={form.tiempoEstimadoHoras} onChange={(tiempoEstimadoHoras) => setForm({ ...form, tiempoEstimadoHoras })} />
        <Campo label="Nota obtenida" type="number" required={false} value={form.notaObtenida} onChange={(notaObtenida) => setForm({ ...form, notaObtenida })} />
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

function TablaActividades({ actividades, materias, onEdit, onDelete, onComplete, onReactivate }) {
  const obtenerNombreMateria = (materiaId) => {
    const materiaSeleccionada = materias.find((materia) => String(materia.id) === String(materiaId));
    return materiaSeleccionada ? materiaSeleccionada.codigo : "Sin materia";
  };

  const obtenerPrioridad = (actividad) => {
    const fechaEntrega = actividad.fechaEntrega ? new Date(actividad.fechaEntrega) : null;
    const hoy = new Date();

    if (!fechaEntrega || Number.isNaN(fechaEntrega.getTime())) {
      return "Sin fecha";
    }

    const diferenciaMs = fechaEntrega.getTime() - hoy.getTime();
    const diasRestantes = Math.ceil(diferenciaMs / (1000 * 60 * 60 * 24));
    const porcentaje = Number(actividad.porcentajeEvaluacion || 0);

    if (diasRestantes <= 2 || porcentaje >= 30) {
      return "Alta";
    }

    if (diasRestantes <= 7 || porcentaje >= 15) {
      return "Media";
    }

    return "Baja";
  };

  const actividadesPriorizadas = [...actividades].sort((a, b) => {
    const fechaA = a.fechaEntrega ? new Date(a.fechaEntrega).getTime() : Infinity;
    const fechaB = b.fechaEntrega ? new Date(b.fechaEntrega).getTime() : Infinity;

    if (fechaA !== fechaB) {
      return fechaA - fechaB;
    }

    const porcentajeA = Number(a.porcentajeEvaluacion || 0);
    const porcentajeB = Number(b.porcentajeEvaluacion || 0);

    if (porcentajeA !== porcentajeB) {
      return porcentajeB - porcentajeA;
    }

    const tiempoA = Number(a.tiempoEstimadoHoras || 0);
    const tiempoB = Number(b.tiempoEstimadoHoras || 0);

    return tiempoB - tiempoA;
  });

  return (
    <Tabla
      columnas={["Prioridad", "Tipo", "Materia", "%", "Nombre", "Entrega", "Horas", "Nota", "Estado"]}
      filas={actividadesPriorizadas.map((actividad) => ({
        id: actividad.id ?? actividad.idActividad,
        celdas: [
          obtenerPrioridad(actividad),
          actividad.tipoActividad,
          obtenerNombreMateria(actividad.materiaInscritaId),
          `${actividad.porcentajeEvaluacion}%`,
          actividad.nombre,
          actividad.fechaEntrega,
          actividad.tiempoEstimadoHoras ? `${actividad.tiempoEstimadoHoras} hrs` : "Sin dato",
          actividad.notaObtenida !== null && actividad.notaObtenida !== undefined
            ? actividad.notaObtenida
            : "Sin nota",
          actividad.estado || "ACTIVO",
        ],
        item: actividad,
      }))}
      onEdit={onEdit}
      onDelete={onDelete}
      onComplete={onComplete}
      onReactivate={onReactivate}
    />
  );
}

function ReporteRendimientoPorMateria({ actividades, materias }) {
  const reportes = materias.map((materia) => {
    const actividadesMateria = actividades.filter(
      (actividad) => String(actividad.materiaInscritaId) === String(materia.id)
    );

    const porcentajeEvaluado = actividadesMateria.reduce(
      (total, actividad) => total + Number(actividad.porcentajeEvaluacion || 0),
      0
    );

    const notasRegistradas = actividadesMateria
      .map((actividad) => actividad.notaObtenida)
      .filter((nota) => nota !== null && nota !== undefined && nota !== "");

    const promedioNotas =
      notasRegistradas.length > 0
        ? notasRegistradas.reduce((total, nota) => total + Number(nota), 0) / notasRegistradas.length
        : null;

    const actividadesCompletadas = actividadesMateria.filter(
      (actividad) => actividad.estado === "COMPLETADA"
    ).length;

    return {
      id: materia.id,
      codigo: materia.codigo,
      nombre: materia.nombre,
      totalActividades: actividadesMateria.length,
      actividadesCompletadas,
      porcentajeEvaluado,
      promedioNotas,
    };
  });

  return (
    <Panel titulo="Reporte de rendimiento por materia">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[680px] text-left text-sm">
          <thead className="bg-[#430000]/10 text-[#430000]">
            <tr>
              <th className="px-3 py-2">Materia</th>
              <th className="px-3 py-2">Actividades</th>
              <th className="px-3 py-2">Completadas</th>
              <th className="px-3 py-2">Porcentaje evaluado</th>
              <th className="px-3 py-2">Promedio de notas</th>
              <th className="px-3 py-2">Estado</th>
            </tr>
          </thead>
          <tbody>
            {reportes.map((reporte) => (
              <tr key={reporte.id} className="border-b border-[#430000]/10">
                <td className="px-3 py-2">
                  {reporte.codigo} - {reporte.nombre}
                </td>
                <td className="px-3 py-2">{reporte.totalActividades}</td>
                <td className="px-3 py-2">{reporte.actividadesCompletadas}</td>
                <td className="px-3 py-2">{reporte.porcentajeEvaluado}%</td>
                <td className="px-3 py-2">
                  {reporte.promedioNotas !== null
                    ? reporte.promedioNotas.toFixed(2)
                    : "Sin notas"}
                </td>
                <td className="px-3 py-2">
                  {reporte.totalActividades === 0
                    ? "Sin actividades"
                    : reporte.promedioNotas !== null
                    ? "Con rendimiento registrado"
                    : "Pendiente de notas"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {reportes.length === 0 && (
          <p className="p-4 text-sm text-[#430000]/60">
            No hay materias registradas para generar el reporte.
          </p>
        )}
      </div>
    </Panel>
  );
}

function Tabla({ columnas, filas, onEdit, onDelete, onComplete, onReactivate }) {
  return (
    <Panel titulo="Registros">
      <div className="w-full max-w-full overflow-x-auto">
        <table className="w-max min-w-full text-left text-xs">
          <thead className="bg-[#430000]/10 text-[#430000]">
            <tr>
              {columnas.map((columna) => (
                <th key={columna} className="px-2 py-2">{columna}</th>
              ))}
              <th className="px-2 py-2">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {filas.map((fila) => (
              <tr key={fila.id} className="border-b border-[#430000]/10">
                {fila.celdas.map((celda, index) => (
                  <td key={`${fila.id}-${index}`} className="px-2 py-2">{celda || "Sin dato"}</td>
                ))}
                <td className="whitespace-nowrap px-3 py-2">
                  <div className="flex gap-2">
                    <button
                      className="rounded bg-[#430000]/10 px-3 py-1 font-semibold"
                      type="button"
                      onClick={() => onEdit(fila.item)}
                    >
                      Editar
                    </button>

                    {onComplete && (fila.item.estado || "ACTIVO") !== "COMPLETADA" && (
                      <button
                        className="rounded bg-green-700 px-3 py-1 font-semibold text-white"
                        type="button"
                        onClick={() => onComplete(fila.item)}
                      >
                        Completar
                      </button>
                    )}

                    {onReactivate && (fila.item.estado || "ACTIVO") === "COMPLETADA" && (
                      <button
                        className="rounded bg-amber-600 px-3 py-1 font-semibold text-white"
                        type="button"
                        onClick={() => onReactivate(fila.item)}
                      >
                        Reactivar
                      </button>
                    )}

                    <button
                      className="rounded bg-[#960000] px-3 py-1 font-semibold text-white"
                      type="button"
                      onClick={() => onDelete(fila.id)}
                    >
                      Eliminar
                    </button>
                  </div>
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
          className={`block w-full rounded-lg border border-[#430000]/20 bg-white px-3 py-2 text-[#430000] outline-none transition focus:border-[#960000] focus:ring-2 focus:ring-[#960000]/20 ${
            hasPicker ? "pr-11" : ""
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
