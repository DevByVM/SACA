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
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import Campo from "./ui/Campo";
import DialogoConfirmacion from "./ui/DialogoConfirmacion";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Insignia from "./ui/Insignia";
import Modal from "./ui/Modal";
import Selector from "./ui/Selector";
import Tabla from "./ui/Tabla";
import Tarjeta from "./ui/Tarjeta";

// El input time entrega "HH:mm" y el backend puede devolver "HH:mm:ss";
// se completan los segundos para que la comparación de texto sea válida.
const normalizarHora = (hora) => (String(hora).length === 5 ? `${hora}:00` : String(hora));

const minutosDelDia = (hora) => {
  const [horas, minutos] = String(hora).split(":").map(Number);
  return horas * 60 + minutos;
};

const DURACION_MINIMA_MINUTOS = 30;

// Tipos que se ofrecen para registrar bloques (SDACAS-7 y SDACAS-32). Las clases universitarias se
// registran en Gestión Académica → Horarios, no como bloques de disponibilidad.
const TIPOS_VIGENTES = [
  { value: "NO_DISPONIBLE", label: "No disponible" },
  { value: "ESTUDIO", label: "Tiempo de estudio" },
];

// Tipos anteriores que pueden existir en registros guardados. Se conservan y se muestran, pero no se
// ofrecen para bloques nuevos; cualquier otro valor desconocido también se trata como tipo anterior.
const NOMBRES_TIPOS_ANTERIORES = { CLASE: "Clase", LIBRE: "Libre", TRABAJO: "Trabajo" };

const esTipoVigente = (tipo) => TIPOS_VIGENTES.some((t) => t.value === tipo);

const nombreTipoAnterior = (tipo) => NOMBRES_TIPOS_ANTERIORES[tipo] ?? (tipo || "Sin tipo");

const etiquetaTipoBloque = (tipo) =>
  TIPOS_VIGENTES.find((t) => t.value === tipo)?.label ?? `${nombreTipoAnterior(tipo)} (tipo anterior)`;

const AYUDA_TIPO_BLOQUE =
  "«Tiempo de estudio»: tiempo que reservas para estudiar. «No disponible»: tiempo personal en el que no puedes estudiar. " +
  "Registra tus clases universitarias en Gestión Académica → Horarios.";

// El tipo empieza vacío para que el estudiante lo elija; el selector es obligatorio
const disponibilidadInicial = {
  diaSemana: "LUNES",
  horaInicio: "",
  horaFin: "",
  tipoBloque: "",
  cicloId: "",
};

function GestionDisponibilidad({ estudiante }) {
  const estudianteId = estudiante?.id ? String(estudiante.id) : "";
  const [cargando, setCargando] = useState(false);

  const [disponibilidades, setDisponibilidades] = useState([]);
  const [ciclos, setCiclos] = useState([]);

  const [form, setForm] = useState(disponibilidadInicial);
  // tipoOriginal permite conservar un tipo anterior al editar y avisar de la conversión
  const [editando, setEditando] = useState({ tipo: null, id: null, tipoOriginal: null });
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [bloqueAEliminar, setBloqueAEliminar] = useState(null);
  const [eliminando, setEliminando] = useState(false);

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

    // Un bloque nuevo solo puede ser de un tipo vigente; al editar se admite conservar su tipo anterior
    const conservaTipoAnterior = editando.tipoOriginal !== null && form.tipoBloque === editando.tipoOriginal;
    if (!form.tipoBloque || (!esTipoVigente(form.tipoBloque) && !conservaTipoAnterior)) {
      toast.warning("Selecciona el tipo de bloque: «Tiempo de estudio» o «No disponible»");
      return;
    }

    if (normalizarHora(form.horaInicio) >= normalizarHora(form.horaFin)) {
      toast.warning("La hora de inicio debe ser menor que la hora de fin");
      return;
    }

    if (minutosDelDia(form.horaFin) - minutosDelDia(form.horaInicio) < DURACION_MINIMA_MINUTOS) {
      toast.warning(`El bloque debe durar al menos ${DURACION_MINIMA_MINUTOS} minutos`);
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
      setEditando({ tipo: null, id: null, tipoOriginal: null });
      setFormularioAbierto(false);
      toast.success("Disponibilidad guardada correctamente");
      await cargarDatos();
    } catch (error) {
      const mensajeBackend = error.response?.data?.message || error.message || "Error al procesar la solicitud";
      toast.error(mensajeBackend);
    }
  }

  // Se elimina solo después de confirmar en el diálogo
  async function confirmarEliminacion() {
    if (!bloqueAEliminar) return;
    try {
      setEliminando(true);
      await eliminarDisponibilidad(bloqueAEliminar.id, estudianteId);
      toast.success("Registro eliminado");
      setBloqueAEliminar(null);
      await cargarDatos();
    } catch (error) {
      const mensajeBackend = error.response?.data?.message || error.message || "No se pudo eliminar el registro";
      toast.error(mensajeBackend);
    } finally {
      setEliminando(false);
    }
  }

  function editarDisponibilidad(disponibilidad) {
    setEditando({ tipo: "disponibilidad", id: disponibilidad.id, tipoOriginal: disponibilidad.tipoBloque });
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
    setEditando({ tipo: null, id: null, tipoOriginal: null });
    setForm(disponibilidadInicial);
    setFormularioAbierto(true);
  }

  function cerrarFormulario() {
    setFormularioAbierto(false);
    setEditando({ tipo: null, id: null, tipoOriginal: null });
    setForm(disponibilidadInicial);
  }

  // Al editar un bloque de tipo anterior, su tipo se ofrece como opción para conservarlo sin cambios
  const tipoAnteriorEditado =
    editando.tipoOriginal !== null && !esTipoVigente(editando.tipoOriginal) ? editando.tipoOriginal : null;
  const opcionesTipo = tipoAnteriorEditado
    ? [...TIPOS_VIGENTES, { value: tipoAnteriorEditado, label: `${nombreTipoAnterior(tipoAnteriorEditado)} (tipo anterior, conservar)` }]
    : TIPOS_VIGENTES;
  const convierteTipoAnterior = tipoAnteriorEditado !== null && esTipoVigente(form.tipoBloque);
  const bloquesAnteriores = disponibilidades.filter((disp) => !esTipoVigente(disp.tipoBloque));

  const filas = disponibilidades.map((disp) => {
    const cicloAsociado = ciclos.find((c) => Number(c.id) === Number(disp.cicloId));
    const cicloLabel = cicloAsociado ? `${cicloAsociado.nombre} (${cicloAsociado.anio})` : "No asignado";
    const horario = `${disp.horaInicio} - ${disp.horaFin}`;

    return {
      id: disp.id,
      celdas: [
        disp.diaSemana,
        horario,
        esTipoVigente(disp.tipoBloque) ? (
          <Insignia>{etiquetaTipoBloque(disp.tipoBloque)}</Insignia>
        ) : (
          <Insignia icono="fa-solid fa-clock-rotate-left">{etiquetaTipoBloque(disp.tipoBloque)}</Insignia>
        ),
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
            onClick={() => setBloqueAEliminar(disp)}
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
        descripcion="Gestiona tus bloques de tiempo disponibles por cada ciclo académico."
        acciones={
          <Boton icono="fa-solid fa-plus" onClick={nuevoRegistro}>
            Nueva disponibilidad
          </Boton>
        }
      />

      <Tarjeta
        titulo="Bloques de disponibilidad registrados"
        nivelTitulo={2}
        acciones={
          cargando && !cargaInicial && (
            <span className="text-sm text-ink-600">Cargando información...</span>
          )
        }
      >
        {bloquesAnteriores.length > 0 && (
          <Aviso tono="info" className="mb-4" titulo="Bloques de tipos anteriores">
            <p>
              {bloquesAnteriores.length === 1
                ? "Tienes 1 bloque de un tipo anterior"
                : `Tienes ${bloquesAnteriores.length} bloques de tipos anteriores`}{" "}
              ({[...new Set(bloquesAnteriores.map((disp) => nombreTipoAnterior(disp.tipoBloque)))].join(", ")}). No intervienen en el
              tiempo de estudio ni en el calendario, pero siguen ocupando su franja: no puedes registrar otro bloque que se
              cruce con ellos. Edítalos para convertirlos en «Tiempo de estudio» o «No disponible», o elimínalos. Las clases
              universitarias se registran en Gestión Académica → Horarios.
            </p>
          </Aviso>
        )}
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
            options={opcionesTipo}
            ayuda={AYUDA_TIPO_BLOQUE}
          />
          {tipoAnteriorEditado && (
            <Aviso tono="info">
              {convierteTipoAnterior ? (
                <p>
                  Al guardar, el bloque pasará de «{nombreTipoAnterior(tipoAnteriorEditado)}» a «
                  {etiquetaTipoBloque(form.tipoBloque)}».
                </p>
              ) : (
                <p>
                  Este bloque usa el tipo anterior «{nombreTipoAnterior(tipoAnteriorEditado)}», que se conserva si no lo
                  cambias. Para convertirlo, elige «Tiempo de estudio» o «No disponible». Si eran clases universitarias,
                  regístralas en Gestión Académica → Horarios y elimina este bloque.
                </p>
              )}
            </Aviso>
          )}
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

      <DialogoConfirmacion
        abierto={bloqueAEliminar !== null}
        titulo="¿Eliminar bloque de disponibilidad?"
        mensaje={
          bloqueAEliminar && (
            <>
              Se eliminará el bloque del{" "}
              <span className="font-bold text-ink-900">{bloqueAEliminar.diaSemana}</span> de{" "}
              {bloqueAEliminar.horaInicio} a {bloqueAEliminar.horaFin} ({etiquetaTipoBloque(bloqueAEliminar.tipoBloque)}). Esta
              acción no se puede deshacer.
            </>
          )
        }
        textoConfirmar="Eliminar"
        procesando={eliminando}
        onConfirmar={confirmarEliminacion}
        onCancelar={() => setBloqueAEliminar(null)}
      />
    </section>
  );
}

export default GestionDisponibilidad;
