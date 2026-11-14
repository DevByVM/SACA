import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import {
  guardarPlantillaAdmin,
  listarPlantillasAdmin,
} from "../../api/adminPlantillasApi";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import Tarjeta from "../ui/Tarjeta";
import Tabla from "../ui/Tabla";

const PERFILES = [
  ["CON_JORNADA_LABORAL", "Con jornada laboral"],
  ["SIN_JORNADA_LABORAL", "Sin jornada laboral"],
];
const DIAS = [
  ["LUNES", "Lunes"],
  ["MARTES", "Martes"],
  ["MIERCOLES", "Miércoles"],
  ["JUEVES", "Jueves"],
  ["VIERNES", "Viernes"],
  ["SABADO", "Sábado"],
  ["DOMINGO", "Domingo"],
];
const TIPOS_BLOQUE = [
  ["ESTUDIO", "Tiempo de estudio"],
  ["TRABAJO", "Tiempo de trabajo"],
  ["CLASES", "Tiempo de clases"],
  ["DESCANSO", "Tiempo de descanso"],
];
const NUEVA_FRANJA = { diaSemana: "LUNES", horaInicio: "08:00", horaFin: "09:00", tipoBloque: "ESTUDIO" };
const FORMULARIO_VACIO = {
  nombre: "",
  perfil: "SIN_JORNADA_LABORAL",
  activa: false,
  franjas: [],
};

function GestionPlantillas() {
  const [plantillas, setPlantillas] = useState([]);
  const [formulario, setFormulario] = useState(FORMULARIO_VACIO);
  const [editandoId, setEditandoId] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);

  const cargarPlantillas = useCallback(async () => {
    try {
      setCargando(true);
      setPlantillas(await listarPlantillasAdmin());
    } catch (error) {
      toast.error(error.message || "No se pudieron cargar las plantillas.");
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargarPlantillas();
  }, [cargarPlantillas]);

  async function guardar(event) {
    event.preventDefault();
    try {
      setGuardando(true);
      await guardarPlantillaAdmin(formulario, editandoId);
      setFormulario(FORMULARIO_VACIO);
      setEditandoId(null);
      await cargarPlantillas();
      toast.success("Plantilla de planificación guardada.");
    } catch (error) {
      toast.error(error.message || "No se pudo guardar la plantilla.");
    } finally {
      setGuardando(false);
    }
  }

  function editar(plantilla) {
    setEditandoId(plantilla.id);
    setFormulario({
      nombre: plantilla.nombre,
      perfil: plantilla.perfil,
      activa: plantilla.activa,
      franjas: plantilla.franjas.map((franja) => ({
        diaSemana: franja.diaSemana,
        horaInicio: franja.horaInicio.slice(0, 5),
        horaFin: franja.horaFin.slice(0, 5),
        tipoBloque: franja.tipoBloque,
      })),
    });
  }

  function cancelarEdicion() {
    setEditandoId(null);
    setFormulario(FORMULARIO_VACIO);
  }

  function cambiarFranja(indice, campo, valor) {
    setFormulario((actual) => ({
      ...actual,
      franjas: actual.franjas.map((franja, i) => i === indice ? { ...franja, [campo]: valor } : franja),
    }));
  }

  const filas = plantillas.map((plantilla) => ({
    id: plantilla.id,
    celdas: [
      plantilla.nombre,
      PERFILES.find(([valor]) => valor === plantilla.perfil)?.[1] ?? plantilla.perfil,
      plantilla.activa ? "Activa" : "Inactiva",
      `${plantilla.franjas.length} franjas`,
      <Boton key="editar" variante="secundario" onClick={() => editar(plantilla)}>Editar</Boton>,
    ],
  }));

  return (
    <div className="space-y-6">
      <Tarjeta titulo={editandoId ? "Editar plantilla" : "Crear plantilla de planificación"}>
        <form className="space-y-4" onSubmit={guardar}>
          <div className="grid gap-4 sm:grid-cols-2">
            <Campo
              label="Nombre"
              value={formulario.nombre}
              onValorChange={(nombre) => setFormulario((actual) => ({ ...actual, nombre }))}
              maxLength={120}
            />
            <label className="block text-sm font-semibold text-ink-900">
              Perfil
              <select
                className="mt-1 block w-full rounded-lg border border-ink-500 bg-surface px-3 py-2 text-sm"
                value={formulario.perfil}
                onChange={(event) => setFormulario((actual) => ({ ...actual, perfil: event.target.value }))}
              >
                {PERFILES.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
              </select>
            </label>
          </div>

          <label className="flex items-center gap-2 text-sm font-medium text-ink-900">
            <input
              type="checkbox"
              checked={formulario.activa}
              onChange={(event) => setFormulario((actual) => ({ ...actual, activa: event.target.checked }))}
            />
            Plantilla activa para este perfil
          </label>
          <p className="text-sm text-ink-600">
            Al activar una plantilla, la anterior del mismo perfil se desactiva. Las sugerencias usarán sus franjas de estudio.
          </p>

          <div className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h3 className="font-semibold text-ink-900">Franjas semanales</h3>
              <Boton
                variante="secundario"
                onClick={() => setFormulario((actual) => ({
                  ...actual,
                  franjas: [...actual.franjas, { ...NUEVA_FRANJA }],
                }))}
              >
                Agregar franja
              </Boton>
            </div>
            {formulario.franjas.map((franja, indice) => (
              <div key={indice} className="grid items-end gap-3 rounded-lg border border-line p-3 sm:grid-cols-5">
                <label className="text-sm font-semibold text-ink-900">
                  Día
                  <select
                    className="mt-1 block w-full rounded-lg border border-ink-500 bg-surface px-3 py-2 text-sm"
                    value={franja.diaSemana}
                    onChange={(event) => cambiarFranja(indice, "diaSemana", event.target.value)}
                  >
                    {DIAS.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
                  </select>
                </label>
                <Campo label="Desde" type="time" value={franja.horaInicio}
                  onValorChange={(horaInicio) => cambiarFranja(indice, "horaInicio", horaInicio)} />
                <Campo label="Hasta" type="time" value={franja.horaFin}
                  onValorChange={(horaFin) => cambiarFranja(indice, "horaFin", horaFin)} />
                <label className="text-sm font-semibold text-ink-900">
                  Tipo de bloque
                  <select
                    className="mt-1 block w-full rounded-lg border border-ink-500 bg-surface px-3 py-2 text-sm"
                    value={franja.tipoBloque}
                    onChange={(event) => cambiarFranja(indice, "tipoBloque", event.target.value)}
                  >
                    {TIPOS_BLOQUE.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
                  </select>
                </label>
                <Boton variante="peligro" onClick={() => setFormulario((actual) => ({
                  ...actual,
                  franjas: actual.franjas.filter((_, i) => i !== indice),
                }))}>
                  Quitar
                </Boton>
              </div>
            ))}
          </div>

          <div className="flex flex-wrap gap-2">
            <Boton type="submit" disabled={guardando}>
              {guardando ? "Guardando..." : editandoId ? "Guardar cambios" : "Crear plantilla"}
            </Boton>
            {editandoId && <Boton variante="secundario" onClick={cancelarEdicion}>Cancelar</Boton>}
          </div>
        </form>
      </Tarjeta>

      <Tarjeta titulo="Plantillas de planificación">
        <Tabla
          columnas={["Nombre", "Perfil", "Estado", "Bloques", "Acciones"]}
          filas={filas}
          cargando={cargando}
          mensajeCarga="Cargando plantillas..."
          mensajeVacio="Todavía no hay plantillas."
        />
      </Tarjeta>
    </div>
  );
}

export default GestionPlantillas;
