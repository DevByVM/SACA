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
import DialogoConfirmacion from "./ui/DialogoConfirmacion";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Insignia from "./ui/Insignia";
import Modal from "./ui/Modal";
import ReporteRendimientoPorMateria from "./ReporteRendimientoPorMateria";
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
  actividadAcademicaId: "",
  actividadNombre: "",
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

function mensajeCambioMateria(pendiente) {
  if (!pendiente) return "";
  const { cantidad, materia } = pendiente;
  return cantidad === 1
    ? `Esta actividad tiene 1 horario de clase vinculado de la materia ${materia}. Si continúas, el horario se conservará pero quedará sin actividad asociada.`
    : `Esta actividad tiene ${cantidad} horarios de clase vinculados de la materia ${materia}. Si continúas, los horarios se conservarán pero quedarán sin actividad asociada.`;
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
  // Cambio de materia pendiente de confirmar: { payload, cantidad, materia }
  const [cambioMateriaPendiente, setCambioMateriaPendiente] = useState(null);
  const [procesandoCambioMateria, setProcesandoCambioMateria] = useState(false);

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
        // Para el selector opcional de actividad del formulario de horarios
        await cargarColeccion(
          "actividades",
          () => getActividadesByEstudianteIdAndCicloActivo(estudianteId),
          setActividades
        );
      }

      if (vistaObjetivo === "actividades") {
        // El reporte de rendimiento permite elegir entre los ciclos del estudiante
        await cargarColeccion("ciclos", () => listarCiclos(estudianteId), setCiclos);
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
    // actividadNombre solo sirve para mostrar el vínculo actual en el formulario; no se envía
    const payload = {
      materiaInscritaId: Number(horarioForm.materiaInscritaId),
      diaSemana: horarioForm.diaSemana,
      horaInicio: horarioForm.horaInicio,
      horaFin: horarioForm.horaFin,
      modalidad: horarioForm.modalidad,
      docenteTutor: horarioForm.docenteTutor,
      // Sin actividad elegida se envía null, que también quita un vínculo existente
      actividadAcademicaId: horarioForm.actividadAcademicaId ? Number(horarioForm.actividadAcademicaId) : null,
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

    if (editando.tipo === "actividad") {
      const actividadOriginal = actividades.find((actividad) => actividad.idActividad === editando.id);
      const materiaOriginalId = Number(actividadOriginal?.materiaInscritaId);

      if (actividadOriginal && materiaOriginalId !== materiaSeleccionadaId) {
        let horariosVinculados;
        try {
          horariosVinculados = (await listarHorarios(estudianteId, { materiaInscritaId: materiaOriginalId }))
            .filter((horario) => horario.actividadAcademicaId === editando.id);
        } catch (error) {
          // Sin saber si hay horarios vinculados no se guarda: el cambio podría desvincularlos sin aviso
          toast.error(error.message || "No se pudieron consultar los horarios vinculados a la actividad.");
          return;
        }

        if (horariosVinculados.length > 0) {
          const materiaOriginal = materias.find((materia) => Number(materia.id) === materiaOriginalId);
          setCambioMateriaPendiente({
            payload,
            cantidad: horariosVinculados.length,
            materia: materiaOriginal
              ? `${materiaOriginal.codigo} - ${materiaOriginal.nombre}`
              : horariosVinculados[0].materiaNombre,
          });
          return;
        }
      }
    }

    await guardarActividadConfirmada(payload);
  }

  async function guardarActividadConfirmada(payload, { desvincularHorarios = false } = {}) {
    await guardar(async () => {
      const actividadGuardada = editando.tipo === "actividad"
        ? await actualizarActividad(editando.id, payload, { desvincularHorarios })
        : await crearActividad(payload);

      setActividades((actuales) => editando.tipo === "actividad"
        ? actuales.map((actividad) => actividad.idActividad === editando.id ? actividadGuardada : actividad)
        : [actividadGuardada, ...actuales]);
      if (editando.tipo !== "actividad") onActividadesActualizadas?.();
      coleccionesCargadas.current.add("actividades");
      // Los horarios muestran el nombre de su actividad y pueden haber perdido el vínculo
      invalidarHorarios();
      setActividadForm(actividadInicial);
    }, "Actividad academica guardada");
  }

  async function confirmarCambioMateria() {
    setProcesandoCambioMateria(true);
    try {
      await guardarActividadConfirmada(cambioMateriaPendiente.payload, { desvincularHorarios: true });
    } finally {
      setProcesandoCambioMateria(false);
      setCambioMateriaPendiente(null);
    }
  }

  function invalidarHorarios() {
    coleccionesCargadas.current.delete("horarios");
    setHorarios([]);
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
        // El backend desvincula los horarios de la actividad eliminada
        invalidarHorarios();
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
      actividadAcademicaId: horario.actividadAcademicaId ?? "",
      actividadNombre: horario.actividadNombre ?? "",
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
    // Con la confirmación de cambio de materia abierta, Escape solo debe cancelar esa confirmación
    if (!formularioAbierto || cambioMateriaPendiente) return undefined;

    function cerrarConEscape(event) {
      if (event.key === "Escape") {
        setFormularioAbierto(false);
        setEditando({ tipo: null, id: null });
      }
    }

    window.addEventListener("keydown", cerrarConEscape);
    return () => window.removeEventListener("keydown", cerrarConEscape);
  }, [formularioAbierto, cambioMateriaPendiente]);

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
          <BarraAvanceActividades actividades={actividades} />

          <TablaActividades
            materias={materias}
            actividades={actividades}
            onEdit={editarActividad}
            onDelete={(id) => eliminar("actividad", id)}
            onComplete={completarActividad}
            onReactivate={reactivarActividad}
          />

          <ReporteRendimientoPorMateria
            ciclos={ciclos}
            actividades={actividades}
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
            actividades={actividades}
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

      {/* Va después del formulario para quedar encima; cancelar deja el formulario abierto y sin guardar */}
      <DialogoConfirmacion
        abierto={Boolean(cambioMateriaPendiente)}
        titulo="¿Cambiar la materia de la actividad?"
        mensaje={mensajeCambioMateria(cambioMateriaPendiente)}
        textoConfirmar="Cambiar materia y desvincular"
        onConfirmar={confirmarCambioMateria}
        onCancelar={() => setCambioMateriaPendiente(null)}
        procesando={procesandoCambioMateria}
      />
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

function FormularioHorario({ materias, actividades, form, setForm, onSubmit, onCancel }) {
  const opcionesActividad = actividades
    .filter((actividad) => form.materiaInscritaId && String(actividad.materiaInscritaId) === String(form.materiaInscritaId))
    .map((actividad) => ({ value: actividad.idActividad, label: actividad.nombre }));

  // Solo se cargan las actividades del ciclo activo: un vínculo con otra actividad se sigue mostrando
  if (form.actividadAcademicaId && !opcionesActividad.some((opcion) => String(opcion.value) === String(form.actividadAcademicaId))) {
    opcionesActividad.unshift({ value: form.actividadAcademicaId, label: form.actividadNombre || "Actividad vinculada" });
  }

  const ayudaActividad = !form.materiaInscritaId
    ? "Selecciona primero la materia."
    : opcionesActividad.length === 0
      ? "La materia no tiene actividades registradas en el ciclo activo."
      : "Elige «Sin actividad asociada» para quitar el vínculo.";

  return (
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
      <Selector
        className="sm:col-span-2"
        label="Materia"
        value={form.materiaInscritaId}
        // La actividad debe ser de la misma materia: al cambiarla se quita la elegida
        onValorChange={(materiaInscritaId) => setForm({ ...form, materiaInscritaId, actividadAcademicaId: "", actividadNombre: "" })}
        options={materias.map((materia) => ({ value: materia.id, label: `${materia.codigo} - ${materia.nombre}` }))}
      />
      <Selector
        className="sm:col-span-2"
        label="Actividad asociada (opcional)"
        required={false}
        placeholder="Sin actividad asociada"
        value={form.actividadAcademicaId}
        disabled={opcionesActividad.length === 0}
        onValorChange={(actividadAcademicaId) => setForm({ ...form, actividadAcademicaId })}
        options={opcionesActividad}
        ayuda={ayudaActividad}
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
      <CampoUi label="Nota obtenida" type="number" step="0.01" min="0" max="10" required={false} value={form.notaObtenida} onValorChange={(notaObtenida) => setForm({ ...form, notaObtenida })} />
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
          { titulo: "Actividad", className: "min-w-40" },
          { titulo: "Modalidad", className: "whitespace-nowrap" },
          "Acciones",
        ]}
        filas={horarios.map((horario) => ({
          id: horario.id,
          celdas: [
            horario.diaSemana,
            `${horario.horaInicio} - ${horario.horaFin}`,
            horario.materiaNombre,
            horario.actividadNombre || <span className="text-ink-600">Sin actividad</span>,
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
              // Una nota 0 es una calificación registrada y se muestra como tal
              actividad.notaObtenida !== null && actividad.notaObtenida !== undefined
                ? String(actividad.notaObtenida)
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

const esCompletada = (actividad) => actividad.estado === "COMPLETADA";

// SDACAS-23: avance = completadas / total de la misma lista que muestra la tabla (ciclo activo del estudiante)
function BarraAvanceActividades({ actividades }) {
  const total = actividades.length;
  const completadas = actividades.filter(esCompletada).length;
  const porcentaje = total > 0 ? Math.round((completadas / total) * 100) : 0;

  return (
    <Tarjeta titulo="Avance general" nivelTitulo={2}>
      <div
        role="progressbar"
        aria-label="Avance general de actividades"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={porcentaje}
        className="h-3 w-full overflow-hidden rounded-full bg-brand-50"
      >
        <div className="h-full rounded-full bg-success-700 transition-all" style={{ width: `${porcentaje}%` }}></div>
      </div>
      <p className="mt-2 text-sm text-ink-600">
        {total > 0
          ? `${completadas} de ${total} actividades completadas (${porcentaje}\u00A0%)`
          : "Aún no hay actividades registradas en el ciclo activo."}
      </p>
    </Tarjeta>
  );
}

export default GestionAcademica;
