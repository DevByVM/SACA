import { useCallback, useEffect, useRef, useState } from "react";
import { toast } from "react-toastify";
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
import Boton from "./ui/Boton";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Insignia from "./ui/Insignia";
import Modal from "./ui/Modal";
import Selector from "./ui/Selector";
import Tarjeta from "./ui/Tarjeta";
import CampoUi from "./ui/Campo";
import TablaUi from "./ui/Tabla";

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

// Fecha local en formato YYYY-MM-DD; toISOString usaría UTC y en la noche devolvería el día siguiente
function fechaLocalHoy() {
  const hoy = new Date();
  const mes = String(hoy.getMonth() + 1).padStart(2, "0");
  const dia = String(hoy.getDate()).padStart(2, "0");
  return `${hoy.getFullYear()}-${mes}-${dia}`;
}

function formatFechaLocalDateTime(value) {
  if (!value) return null;

  const fecha = String(value).replace("T", " ");

  return fecha.substring(0, 16);
}


function GestionAcademica({ estudiante, onActividadesActualizadas }) {
  const estudianteId = estudiante?.id ? String(estudiante.id) : "";
  const [vista, setVista] = useState("ciclos");
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
  const [formularioAbierto, setFormularioAbierto] = useState(false);

  const coleccionesCargadas = useRef(new Set());
  const solicitudesEnCurso = useRef(new Map());
  const estudianteAnterior = useRef(estudianteId);

  const cargarColeccion = useCallback(async (clave, cargar, actualizar) => {
    if (coleccionesCargadas.current.has(clave)) return;

    const solicitudExistente = solicitudesEnCurso.current.get(clave);
    if (solicitudExistente) return solicitudExistente;

    const solicitud = cargar()
      .then((datos) => {
        actualizar(datos);
        coleccionesCargadas.current.add(clave);
        return datos;
      })
      .finally(() => {
        solicitudesEnCurso.current.delete(clave);
      });

    solicitudesEnCurso.current.set(clave, solicitud);
    return solicitud;
  }, []);

  const cargarVista = useCallback(async (vistaObjetivo) => {
    if (!estudianteId.trim()) return;

    try {
      setCargando(true);

      if (vistaObjetivo === "ciclos") {
        await cargarColeccion("ciclos", () => listarCiclos(estudianteId), setCiclos);
      }

      if (vistaObjetivo === "materias") {
        await cargarColeccion("ciclos", () => listarCiclos(estudianteId), setCiclos);
        await cargarColeccion("materias", () => listarMaterias(estudianteId), setMaterias);
      }

      if (vistaObjetivo === "horarios") {
        await cargarColeccion("materias", () => listarMaterias(estudianteId), setMaterias);
        await cargarColeccion("horarios", () => listarHorarios(estudianteId), setHorarios);
      }

      if (vistaObjetivo === "actividades") {
        await cargarColeccion("materias", () => listarMaterias(estudianteId), setMaterias);
        await cargarColeccion(
          "actividades",
          () => getActividadesByEstudianteIdAndCicloActivo(estudianteId),
          setActividades
        );
      }
    } catch (error) {
      toast.error(error.message || "No se pudo cargar la información académica.");
    } finally {
      setCargando(false);
    }
  }, [cargarColeccion, estudianteId]);

  useEffect(() => {
    if (estudianteAnterior.current !== estudianteId) {
      coleccionesCargadas.current.clear();
      solicitudesEnCurso.current.clear();
      setCiclos([]);
      setMaterias([]);
      setHorarios([]);
      setActividades([]);
      estudianteAnterior.current = estudianteId;
    }
    cargarVista("ciclos");
  }, [cargarVista, estudianteId]);

  useEffect(() => {
    cargarVista(vista);
  }, [cargarVista, vista]);

  async function guardarCiclo(event) {
    event.preventDefault();
    const payload = {
      ...cicloForm,
      estudianteId: Number(estudianteId),
      anio: Number(cicloForm.anio),
    };

    await guardar(async () => {
      const cicloGuardado = editando.tipo === "ciclo"
        ? await actualizarCiclo(editando.id, estudianteId, payload)
        : await crearCiclo(payload);

      setCiclos((actuales) => editando.tipo === "ciclo"
        ? actuales.map((ciclo) => ciclo.id === editando.id ? cicloGuardado : ciclo)
        : [cicloGuardado, ...actuales]);
      coleccionesCargadas.current.add("ciclos");
      coleccionesCargadas.current.delete("materias");
      coleccionesCargadas.current.delete("actividades");
      setMaterias([]);
      setActividades([]);
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
      const materiaGuardada = editando.tipo === "materia"
        ? await actualizarMateria(editando.id, estudianteId, payload)
        : await crearMateria(estudianteId, payload);

      setMaterias((actuales) => editando.tipo === "materia"
        ? actuales.map((materia) => materia.id === editando.id ? materiaGuardada : materia)
        : [materiaGuardada, ...actuales]);
      coleccionesCargadas.current.add("materias");
      coleccionesCargadas.current.delete("horarios");
      coleccionesCargadas.current.delete("actividades");
      setHorarios([]);
      setActividades([]);
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
      const horarioGuardado = editando.tipo === "horario"
        ? await actualizarHorario(editando.id, estudianteId, payload)
        : await crearHorario(estudianteId, payload);

      setHorarios((actuales) => editando.tipo === "horario"
        ? actuales.map((horario) => horario.id === editando.id ? horarioGuardado : horario)
        : [horarioGuardado, ...actuales]);
      coleccionesCargadas.current.add("horarios");
      setHorarioForm(horarioInicial);
    }, "Horario guardado");
  }

  async function guardarActividad(event) {
    event.preventDefault();
    //se obtienen los datos a validar por cliente
    const materiaSeleccionadaId = Number(actividadForm.materiaInscritaId);
    const porcentajeNuevo = Number(actividadForm.porcentajeEvaluacion);
    // Solo al registrar: una actividad ya vencida se puede seguir editando o completando
    if (editando.tipo !== "actividad" && actividadForm.fechaEntrega.slice(0, 10) < fechaLocalHoy()) {
      toast.error("La fecha de entrega no puede ser anterior a la fecha actual");
      return;
    }
    //se extraen las actividades de la materia seleccionada
    const actividadesPorMateria = actividades.filter(
      (actividad) => Number(actividad.materiaInscritaId) === materiaSeleccionadaId
    );
    //se estima si esta en edicion exlcuir de la lista de actividades la actual
    const actividadesConsideradas =
      editando.tipo === "actividad"
        ? actividadesPorMateria.filter((actividad) => actividad.idActividad !== editando.id)
        : actividadesPorMateria;
    //se obtiene el porcentaje de los registros validos
    const porcentajeRegistrado = actividadesConsideradas.reduce(
      (suma, actividad) => suma + Number(actividad.porcentajeEvaluacion), 0
    );

    const porcentajeDisponible = 100 - porcentajeRegistrado;
    //se detiene el guardado si no hay disponibilidad de porcentaje
    if (porcentajeNuevo > porcentajeDisponible) {
      toast.error(`Solo cuenta con ${porcentajeDisponible}% disponible para esta materia.`);
      return;
    }

    const payload = {
      ...actividadForm,
      fechaInicio: formatFechaLocalDateTime(actividadForm.fechaInicio),
      fechaEntrega: formatFechaLocalDateTime(actividadForm.fechaEntrega),
      tiempoEstimadoHoras: actividadForm.tiempoEstimadoHoras ? Number(actividadForm.tiempoEstimadoHoras) : null,
      materiaInscritaId: Number(actividadForm.materiaInscritaId),
      porcentajeEvaluacion: porcentajeNuevo,
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
      const actividadGuardada = editando.tipo === "actividad"
        ? await actualizarActividad(editando.id, payload)
        : await crearActividad(payload);

      setActividades((actuales) => editando.tipo === "actividad"
        ? actuales.map((actividad) => actividad.idActividad === editando.id ? actividadGuardada : actividad)
        : [actividadGuardada, ...actuales]);
      if (editando.tipo !== "actividad") onActividadesActualizadas?.();
      coleccionesCargadas.current.add("actividades");
      setActividadForm(actividadInicial);
    }, "Actividad academica guardada");
  }

  async function guardar(accion, texto) {
    try {
      await accion();
      setEditando({ tipo: null, id: null });
      setFormularioAbierto(false);
      toast.success(texto);
    } catch (error) {
      toast.error(error.message || "No se pudo completar la operación.");
    }
  }

  async function eliminar(tipo, id) {
    const confirmado = window.confirm(
      "¿Estás seguro de que deseas eliminar este registro?"
    );

    if (!confirmado) {
      return;
    }

    await guardar(async () => {
      if (tipo === "ciclo") await eliminarCiclo(id, estudianteId);
      if (tipo === "materia") await eliminarMateria(id, estudianteId);
      if (tipo === "horario") await eliminarHorario(id, estudianteId);
      if (tipo === "actividad") await eliminarActividad(id);

      if (tipo === "ciclo") {
        setCiclos((actuales) => actuales.filter((ciclo) => ciclo.id !== id));
        coleccionesCargadas.current.delete("materias");
        coleccionesCargadas.current.delete("horarios");
        coleccionesCargadas.current.delete("actividades");
        setMaterias([]);
        setHorarios([]);
        setActividades([]);
      }
      if (tipo === "materia") {
        setMaterias((actuales) => actuales.filter((materia) => materia.id !== id));
        coleccionesCargadas.current.delete("horarios");
        coleccionesCargadas.current.delete("actividades");
        setHorarios([]);
        setActividades([]);
      }
      if (tipo === "horario") {
        setHorarios((actuales) => actuales.filter((horario) => horario.id !== id));
      }
      if (tipo === "actividad") {
        setActividades((actuales) => actuales.filter((actividad) => actividad.idActividad !== id));
        onActividadesActualizadas?.();
      }
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
      const actividadActualizada = await actualizarActividad(actividadId, payload);
      reemplazarActividad(actividadId, actividadActualizada);
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
      const actividadActualizada = await actualizarActividad(actividadId, payload);
      reemplazarActividad(actividadId, actividadActualizada);
    }, "Actividad reactivada");
  }

  // Sustituye la actividad por la respuesta del backend para que la fila y sus botones cambien sin recargar.
  // La campana vive en App y no se vuelve a montar: se le avisa para que pida de nuevo las próximas entregas
  function reemplazarActividad(actividadId, actividadActualizada) {
    setActividades((actuales) => actuales.map((actividad) =>
      (actividad.id ?? actividad.idActividad) === actividadId ? actividadActualizada : actividad));
    onActividadesActualizadas?.();
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
    setFormularioAbierto(true);
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
    setFormularioAbierto(true);
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
    setFormularioAbierto(true);
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
    setFormularioAbierto(true);
  }

  function nuevoRegistro() {
    setEditando({ tipo: null, id: null });

    if (vista === "ciclos") setCicloForm(cicloInicial);
    if (vista === "materias") setMateriaForm(materiaInicial);
    if (vista === "horarios") setHorarioForm(horarioInicial);
    if (vista === "actividades") setActividadForm(actividadInicial);

    setFormularioAbierto(true);
  }

  function cerrarFormulario() {
    setFormularioAbierto(false);
    setEditando({ tipo: null, id: null });
  }

  useEffect(() => {
    if (!formularioAbierto) return undefined;

    function cerrarConEscape(event) {
      if (event.key === "Escape") {
        setFormularioAbierto(false);
        setEditando({ tipo: null, id: null });
      }
    }

    window.addEventListener("keydown", cerrarConEscape);
    return () => window.removeEventListener("keydown", cerrarConEscape);
  }, [formularioAbierto]);

  const titulosFormulario = {
    ciclos: { nuevo: "Nuevo ciclo académico", editar: "Editar ciclo académico" },
    materias: { nuevo: "Nueva materia inscrita", editar: "Editar materia inscrita" },
    horarios: { nuevo: "Nuevo horario de clase", editar: "Editar horario de clase" },
    actividades: { nuevo: "Nueva actividad académica", editar: "Editar actividad académica" },
  };

  return (
    <section className="space-y-5">
      <EncabezadoModulo
        descripcion="Ciclos, materias inscritas, horarios y actividades académicas."
        acciones={
          <Boton className="whitespace-nowrap" icono="fa-solid fa-plus" onClick={nuevoRegistro}>
            {vista === "ciclos" ? "Nuevo ciclo" : vista === "materias" ? "Nueva materia" : vista === "horarios" ? "Nuevo horario" : "Nueva actividad"}
          </Boton>
        }
      />

      <div
        role="group"
        aria-label="Secciones de gestión académica"
        className="grid grid-cols-2 gap-1 rounded-xl border border-line bg-surface p-1 sm:inline-grid sm:grid-cols-4"
      >
        {["ciclos", "materias", "horarios", "actividades"].map((item) => (
          <button
            key={item}
            aria-pressed={vista === item}
            className={`min-h-11 rounded-lg px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 focus-visible:ring-offset-2 ${
              vista === item ? "bg-brand-700 text-white hover:bg-brand-800" : "text-ink-900 hover:bg-surface-muted"
            }`}
            type="button"
            onClick={() => {
              cerrarFormulario();
              setVista(item);
            }}
          >
            {item[0].toUpperCase() + item.slice(1)}
          </button>
        ))}
      </div>

      {cargando && <p className="text-sm text-ink-600">Cargando información...</p>}

      {vista === "ciclos" && (
        <TablaCiclos ciclos={ciclos} onEdit={editarCiclo} onDelete={(id) => eliminar("ciclo", id)} />
      )}

      {vista === "materias" && (
        <TablaMaterias
          materias={materias}
          onEdit={editarMateria}
          onDelete={(id) => eliminar("materia", id)}
        />
      )}

      {vista === "horarios" && (
        <TablaHorarios
          horarios={horarios}
          onEdit={editarHorario}
          onDelete={(id) => eliminar("horario", id)}
        />
      )}

      {vista === "actividades" && (
        <div className="space-y-5">
          <TablaActividades
            materias={materias}
            actividades={actividades}
            onEdit={editarActividad}
            onDelete={(id) => eliminar("actividad", id)}
            onComplete={completarActividad}
            onReactivate={reactivarActividad}
          />

          <ReporteRendimientoPorMateria
            actividades={actividades}
            materias={materias}
          />
        </div>
      )}

      {/* El Escape lo gestiona el useEffect de este módulo; si Modal también lo escuchara, cerraría dos veces */}
      <Modal
        abierto={formularioAbierto}
        titulo={editando.tipo ? titulosFormulario[vista].editar : titulosFormulario[vista].nuevo}
        descripcion="Completa los datos del registro."
        etiquetaCerrar="Cerrar formulario"
        onCerrar={cerrarFormulario}
        cerrarConEscape={false}
      >
        {vista === "ciclos" && (
          <FormularioCiclo
            form={cicloForm}
            setForm={setCicloForm}
            onSubmit={guardarCiclo}
            onCancel={cerrarFormulario}
          />
        )}
        {vista === "materias" && (
          <FormularioMateria
            ciclos={ciclos}
            form={materiaForm}
            setForm={setMateriaForm}
            onSubmit={guardarMateria}
            onCancel={cerrarFormulario}
          />
        )}
        {vista === "horarios" && (
          <FormularioHorario
            materias={materias}
            form={horarioForm}
            setForm={setHorarioForm}
            onSubmit={guardarHorario}
            onCancel={cerrarFormulario}
          />
        )}
        {vista === "actividades" && (
          <FormularioActividades
            materias={materias}
            form={actividadForm}
            setForm={setActividadForm}
            onSubmit={guardarActividad}
            onCancel={cerrarFormulario}
            fechaEntregaMinima={editando.tipo === "actividad" ? undefined : `${fechaLocalHoy()}T00:00`}
          />
        )}
      </Modal>
    </section>
  );
}

function FormularioCiclo({ form, setForm, onSubmit, onCancel }) {
  return (
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
      <CampoUi label="Nombre" value={form.nombre} onValorChange={(nombre) => setForm({ ...form, nombre })} />
      <CampoUi label="Anio" type="number" value={form.anio} onValorChange={(anio) => setForm({ ...form, anio })} />
      <CampoUi label="Fecha inicio" type="date" value={form.fechaInicio} onValorChange={(fechaInicio) => setForm({ ...form, fechaInicio })} />
      <CampoUi label="Fecha fin" type="date" value={form.fechaFin} onValorChange={(fechaFin) => setForm({ ...form, fechaFin })} />
      <Selector
        className="sm:col-span-2"
        label="Estado"
        value={form.estado}
        onValorChange={(estado) => setForm({ ...form, estado })}
        options={["PLANIFICADO", "ACTIVO", "FINALIZADO"]}
      />
      <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:col-span-2 sm:flex-row sm:justify-end">
        <Boton variante="secundario" onClick={onCancel}>
          Cancelar
        </Boton>
        <Boton type="submit">Guardar</Boton>
      </div>
    </form>
  );
}

function FormularioMateria({ ciclos, form, setForm, onSubmit, onCancel }) {
  return (
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
      <Selector
        className="sm:col-span-2"
        label="Ciclo"
        value={form.cicloAcademicoId}
        onValorChange={(cicloAcademicoId) => setForm({ ...form, cicloAcademicoId })}
        options={ciclos.map((ciclo) => ({ value: ciclo.id, label: `${ciclo.nombre} - ${ciclo.anio}` }))}
      />
      <CampoUi className="sm:col-span-2" label="Nombre" value={form.nombre} onValorChange={(nombre) => setForm({ ...form, nombre })} />
      <CampoUi label="Codigo" value={form.codigo} onValorChange={(codigo) => setForm({ ...form, codigo })} />
      <CampoUi label="Grupo teorico" value={form.grupoTeorico} onValorChange={(grupoTeorico) => setForm({ ...form, grupoTeorico })} />
      <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:col-span-2 sm:flex-row sm:justify-end">
        <Boton variante="secundario" onClick={onCancel}>
          Cancelar
        </Boton>
        <Boton type="submit">Guardar</Boton>
      </div>
    </form>
  );
}

function FormularioHorario({ materias, form, setForm, onSubmit, onCancel }) {
  return (
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
      <Selector
        className="sm:col-span-2"
        label="Materia"
        value={form.materiaInscritaId}
        onValorChange={(materiaInscritaId) => setForm({ ...form, materiaInscritaId })}
        options={materias.map((materia) => ({ value: materia.id, label: `${materia.codigo} - ${materia.nombre}` }))}
      />
      <Selector label="Dia" value={form.diaSemana} onValorChange={(diaSemana) => setForm({ ...form, diaSemana })} options={["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"]} />
      <Selector label="Modalidad" value={form.modalidad} onValorChange={(modalidad) => setForm({ ...form, modalidad })} options={["PRESENCIAL", "VIRTUAL", "HIBRIDA"]} />
      <CampoUi label="Hora inicio" type="time" value={form.horaInicio} onValorChange={(horaInicio) => setForm({ ...form, horaInicio })} />
      <CampoUi label="Hora fin" type="time" value={form.horaFin} onValorChange={(horaFin) => setForm({ ...form, horaFin })} />
      <CampoUi className="sm:col-span-2" label="Docente o tutor" required={false} value={form.docenteTutor} onValorChange={(docenteTutor) => setForm({ ...form, docenteTutor })} />
      <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:col-span-2 sm:flex-row sm:justify-end">
        <Boton variante="secundario" onClick={onCancel}>
          Cancelar
        </Boton>
        <Boton type="submit">Guardar</Boton>
      </div>
    </form>
  );
}

//form de actividades
function FormularioActividades({ materias, form, setForm, onSubmit, onCancel, fechaEntregaMinima }) {
  return (
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
      <Selector
        label="Materia inscrita"
        value={form.materiaInscritaId}
        onValorChange={(materiaInscritaId) => setForm({ ...form, materiaInscritaId })}
        options={materias.map((materia) => ({ value: materia.id, label: `${materia.codigo} - ${materia.nombre}` }))}
      />
      <Selector label="Tipo de Actividad" value={form.tipoActividad} onValorChange={(tipoActividad) => setForm({ ...form, tipoActividad })} options={["PARCIAL", "LABORATORIO", "PROYECTO", "INVESTIGACION"]} />
      <CampoUi className="sm:col-span-2" label="Nombre" value={form.nombre} onValorChange={(nombre) => setForm({ ...form, nombre })} />
      <CampoUi className="sm:col-span-2" label="Porcentaje de evaluacion (ej. 10%)" type="number" min={1} max={100} value={form.porcentajeEvaluacion} onValorChange={(porcentajeEvaluacion) => setForm({ ...form, porcentajeEvaluacion })} />
      <CampoUi label="Fecha inicio" type="datetime-local" value={form.fechaInicio} onValorChange={(fechaInicio) => setForm({ ...form, fechaInicio })} />
      <CampoUi label="Fecha Entrega" type="datetime-local" min={fechaEntregaMinima} value={form.fechaEntrega} onValorChange={(fechaEntrega) => setForm({ ...form, fechaEntrega })} />
      <CampoUi label="Tiempo estimado (horas)" type="number" value={form.tiempoEstimadoHoras} onValorChange={(tiempoEstimadoHoras) => setForm({ ...form, tiempoEstimadoHoras })} />
      <CampoUi label="Nota obtenida" type="number" required={false} value={form.notaObtenida} onValorChange={(notaObtenida) => setForm({ ...form, notaObtenida })} />
      <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:col-span-2 sm:flex-row sm:justify-end">
        <Boton variante="secundario" onClick={onCancel}>
          Cancelar
        </Boton>
        <Boton type="submit">Guardar</Boton>
      </div>
    </form>
  );
}

const TONOS_ESTADO_CICLO = {
  PLANIFICADO: "info",
  ACTIVO: "exito",
  FINALIZADO: "neutro",
};

function TablaCiclos({ ciclos, onEdit, onDelete }) {
  return (
    <Tarjeta titulo="Registros" nivelTitulo={2}>
      <TablaUi
        columnas={[
          { titulo: "Nombre", className: "min-w-40" },
          "Anio",
          { titulo: "Periodo", className: "whitespace-nowrap" },
          "Estado",
          "Acciones",
        ]}
        filas={ciclos.map((ciclo) => ({
          id: ciclo.id,
          celdas: [
            ciclo.nombre,
            ciclo.anio,
            `${ciclo.fechaInicio} a ${ciclo.fechaFin}`,
            ciclo.estado && <Insignia tono={TONOS_ESTADO_CICLO[ciclo.estado]}>{ciclo.estado}</Insignia>,
            <div className="flex gap-2">
              <Boton variante="secundario" tamano="sm" onClick={() => onEdit(ciclo)}>
                Editar
              </Boton>
              <Boton variante="peligro" tamano="sm" onClick={() => onDelete(ciclo.id)}>
                Eliminar
              </Boton>
            </div>,
          ],
        }))}
      />
    </Tarjeta>
  );
}

function TablaMaterias({ materias, onEdit, onDelete }) {
  return (
    <Tarjeta titulo="Registros" nivelTitulo={2}>
      <TablaUi
        columnas={[
          { titulo: "Codigo", className: "whitespace-nowrap font-semibold" },
          { titulo: "Materia", className: "min-w-40" },
          { titulo: "Ciclo", className: "min-w-32" },
          { titulo: "Grupo", className: "whitespace-nowrap" },
          "Acciones",
        ]}
        filas={materias.map((materia) => ({
          id: materia.id,
          celdas: [
            materia.codigo,
            materia.nombre,
            materia.cicloAcademicoNombre,
            materia.grupoTeorico,
            <div className="flex gap-2">
              <Boton variante="secundario" tamano="sm" onClick={() => onEdit(materia)}>
                Editar
              </Boton>
              <Boton variante="peligro" tamano="sm" onClick={() => onDelete(materia.id)}>
                Eliminar
              </Boton>
            </div>,
          ],
        }))}
      />
    </Tarjeta>
  );
}

function TablaHorarios({ horarios, onEdit, onDelete }) {
  return (
    <Tarjeta titulo="Registros" nivelTitulo={2}>
      <TablaUi
        columnas={[
          { titulo: "Dia", className: "whitespace-nowrap font-semibold" },
          { titulo: "Horario", className: "whitespace-nowrap" },
          { titulo: "Materia", className: "min-w-40" },
          { titulo: "Modalidad", className: "whitespace-nowrap" },
          "Acciones",
        ]}
        filas={horarios.map((horario) => ({
          id: horario.id,
          celdas: [
            horario.diaSemana,
            `${horario.horaInicio} - ${horario.horaFin}`,
            horario.materiaNombre,
            horario.modalidad,
            <div className="flex gap-2">
              <Boton variante="secundario" tamano="sm" onClick={() => onEdit(horario)}>
                Editar
              </Boton>
              <Boton variante="peligro" tamano="sm" onClick={() => onDelete(horario.id)}>
                Eliminar
              </Boton>
            </div>,
          ],
        }))}
      />
    </Tarjeta>
  );
}

const TONOS_PRIORIDAD_ACTIVIDAD = {
  Alta: "peligro",
  Media: "advertencia",
  Baja: "neutro",
  "Sin fecha": "neutro",
};

const TONOS_RENDIMIENTO = {
  "Sin actividades": "neutro",
  "Con rendimiento registrado": "exito",
  "Pendiente de notas": "advertencia",
};

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
    <Tarjeta titulo="Registros" nivelTitulo={2}>
      <TablaUi
        compacta
        columnas={[
          "Prioridad",
          { titulo: "Tipo", className: "whitespace-nowrap" },
          { titulo: "Materia", className: "whitespace-nowrap" },
          { titulo: "%", alinear: "derecha" },
          { titulo: "Nombre", className: "min-w-48" },
          { titulo: "Entrega", className: "whitespace-nowrap" },
          { titulo: "Horas", className: "whitespace-nowrap" },
          { titulo: "Nota", className: "whitespace-nowrap" },
          "Estado",
          "Acciones",
        ]}
        filas={actividadesPriorizadas.map((actividad) => {
          const id = actividad.id ?? actividad.idActividad;
          const prioridad = obtenerPrioridad(actividad);
          const estado = actividad.estado || "ACTIVO";

          return {
            id,
            celdas: [
              <Insignia tono={TONOS_PRIORIDAD_ACTIVIDAD[prioridad]}>{prioridad}</Insignia>,
              actividad.tipoActividad,
              obtenerNombreMateria(actividad.materiaInscritaId),
              `${actividad.porcentajeEvaluacion}%`,
              actividad.nombre,
              actividad.fechaEntrega,
              actividad.tiempoEstimadoHoras ? `${actividad.tiempoEstimadoHoras} hrs` : "Sin dato",
              // Como en la tabla anterior, una nota 0 se muestra como «Sin dato»
              actividad.notaObtenida !== null && actividad.notaObtenida !== undefined
                ? actividad.notaObtenida || "Sin dato"
                : "Sin nota",
              <Insignia tono={estado === "COMPLETADA" ? "exito" : "info"}>{estado}</Insignia>,
              <div className="flex gap-2">
                <Boton variante="secundario" tamano="sm" onClick={() => onEdit(actividad)}>
                  Editar
                </Boton>
                {estado !== "COMPLETADA" ? (
                  <Boton tamano="sm" onClick={() => onComplete(actividad)}>
                    Completar
                  </Boton>
                ) : (
                  <Boton variante="secundario" tamano="sm" onClick={() => onReactivate(actividad)}>
                    Reactivar
                  </Boton>
                )}
                <Boton variante="peligro" tamano="sm" onClick={() => onDelete(id)}>
                  Eliminar
                </Boton>
              </div>,
            ],
          };
        })}
      />
    </Tarjeta>
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
    <Tarjeta titulo="Reporte de rendimiento por materia" nivelTitulo={2}>
      <TablaUi
        columnas={[
          { titulo: "Materia", className: "min-w-48" },
          { titulo: "Actividades", alinear: "derecha" },
          { titulo: "Completadas", alinear: "derecha" },
          { titulo: "Porcentaje evaluado", alinear: "derecha" },
          { titulo: "Promedio de notas", alinear: "derecha" },
          "Estado",
        ]}
        mensajeVacio="No hay materias registradas para generar el reporte."
        filas={reportes.map((reporte) => {
          const estado = reporte.totalActividades === 0
            ? "Sin actividades"
            : reporte.promedioNotas !== null
            ? "Con rendimiento registrado"
            : "Pendiente de notas";

          return {
            id: reporte.id,
            celdas: [
              <>{reporte.codigo} - {reporte.nombre}</>,
              reporte.totalActividades,
              reporte.actividadesCompletadas,
              `${reporte.porcentajeEvaluado}%`,
              reporte.promedioNotas !== null
                ? reporte.promedioNotas.toFixed(2)
                : "Sin notas",
              <Insignia tono={TONOS_RENDIMIENTO[estado]}>{estado}</Insignia>,
            ],
          };
        })}
      />
    </Tarjeta>
  );
}

export default GestionAcademica;
