import { useEffect, useState } from "react";
import { obtenerRendimiento } from "../api/rendimientoApi";
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import EstadoVacio from "./ui/EstadoVacio";
import Insignia from "./ui/Insignia";
import Selector from "./ui/Selector";
import TablaUi from "./ui/Tabla";
import Tarjeta from "./ui/Tarjeta";

const SITUACIONES = {
  SIN_ACTIVIDADES: { texto: "Sin actividades", tono: "neutro" },
  SIN_CALIFICACIONES: { texto: "Sin calificaciones", tono: "neutro" },
  NOTA_MINIMA_NO_CONFIGURADA: { texto: "Nota mínima no configurada", tono: "neutro" },
  APROBADA: { texto: "Aprobada", tono: "exito" },
  RECUPERABLE: { texto: "Recuperable", tono: "advertencia" },
  DEPENDE_DE_EVALUACIONES_SIN_REGISTRAR: { texto: "Depende de evaluaciones sin registrar", tono: "advertencia" },
  IMPOSIBLE_DE_APROBAR: { texto: "Imposible de aprobar", tono: "peligro" },
  REPROBADA: { texto: "Reprobada", tono: "peligro" },
};

const CICLO_ACTIVO = "";

// Los valores llegan del backend ya redondeados (la nota necesaria, hacia arriba); aquí solo se formatean
function formatearNota(valor) {
  if (valor === null || valor === undefined) return null;
  return Number(valor).toFixed(2);
}

function formatearPorcentaje(valor) {
  const numero = Number(valor ?? 0);
  return `${Number.isInteger(numero) ? numero : numero.toFixed(2).replace(/0$/, "")}%`;
}

function explicarNotaNecesaria(materia) {
  if (materia.notaNecesaria !== null && materia.notaNecesaria !== undefined) {
    return Number(materia.notaNecesaria) > 10
      ? `${formatearNota(materia.notaNecesaria)} (supera 10 con lo registrado)`
      : `${formatearNota(materia.notaNecesaria)} de promedio en lo pendiente`;
  }
  if (materia.notaMinima === null || materia.notaMinima === undefined) return "Configura tu nota mínima";
  if (materia.situacion === "APROBADA") return "Ya alcanzaste la nota mínima";
  return "Sin actividades pendientes registradas";
}

// El cálculo vive en el backend (GET /api/rendimiento); este componente solo lo presenta.
// «actividades» se recibe únicamente para volver a consultar cuando cambian en Gestión Académica.
function ReporteRendimientoPorMateria({ ciclos, actividades }) {
  const [cicloSeleccionado, setCicloSeleccionado] = useState(CICLO_ACTIVO);
  const [reporte, setReporte] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");
  const [materiasAbiertas, setMateriasAbiertas] = useState(() => new Set());

  useEffect(() => {
    let vigente = true;
    setCargando(true);
    setError("");

    obtenerRendimiento(cicloSeleccionado)
      .then((datos) => {
        if (vigente) setReporte(datos);
      })
      .catch((errorConsulta) => {
        if (vigente) setError(errorConsulta.message || "No se pudo cargar el reporte de rendimiento.");
      })
      .finally(() => {
        if (vigente) setCargando(false);
      });

    // Evita que una respuesta lenta de un ciclo anterior sobrescriba la del ciclo elegido después
    return () => {
      vigente = false;
    };
  }, [cicloSeleccionado, actividades]);

  function alternarDetalle(materiaId) {
    setMateriasAbiertas((actuales) => {
      const siguientes = new Set(actuales);
      if (siguientes.has(materiaId)) siguientes.delete(materiaId);
      else siguientes.add(materiaId);
      return siguientes;
    });
  }

  const opcionesCiclo = [
    { value: CICLO_ACTIVO, label: "Ciclo activo" },
    ...ciclos.map((ciclo) => ({ value: String(ciclo.id), label: `${ciclo.nombre} (${ciclo.estado?.toLowerCase() ?? ""})` })),
  ];

  const materias = reporte?.materias ?? [];
  const sinNotaMinima = reporte && (reporte.notaMinimaAprobacion === null || reporte.notaMinimaAprobacion === undefined);

  return (
    <Tarjeta
      titulo="Reporte de rendimiento por materia"
      nivelTitulo={2}
      acciones={
        <Selector
          aria-label="Ciclo del reporte"
          placeholder={null}
          required={false}
          value={cicloSeleccionado}
          onValorChange={setCicloSeleccionado}
          options={opcionesCiclo}
        />
      }
    >
      <div className="space-y-4">
        {reporte?.cicloAcademicoNombre && (
          <p className="text-sm text-ink-600">
            Ciclo: <span className="font-semibold text-ink-900">{reporte.cicloAcademicoNombre}</span>
            {!sinNotaMinima && (
              <> · Nota mínima de aprobación: <span className="font-semibold text-ink-900">{formatearNota(reporte.notaMinimaAprobacion)}</span></>
            )}
          </p>
        )}

        {sinNotaMinima && (
          <Aviso tono="info" titulo="Nota mínima de aprobación no configurada">
            Configúrala en «Perfil académico» según tu programa para estimar la nota necesaria y la situación de cada materia.
          </Aviso>
        )}

        {error && <Aviso tono="peligro">{error}</Aviso>}

        {cargando && !reporte ? (
          <EstadoVacio cargando mensaje="Calculando el rendimiento..." />
        ) : !error && reporte && reporte.cicloAcademicoId === null ? (
          <EstadoVacio mensaje="No tienes un ciclo activo. Elige un ciclo para consultar su historial." />
        ) : !error && materias.length === 0 ? (
          <EstadoVacio mensaje="El ciclo no tiene materias registradas." />
        ) : (
          <ul className="space-y-3" aria-busy={cargando}>
            {materias.map((materia) => (
              <MateriaRendimiento
                key={materia.materiaInscritaId}
                materia={materia}
                abierta={materiasAbiertas.has(materia.materiaInscritaId)}
                onAlternar={() => alternarDetalle(materia.materiaInscritaId)}
              />
            ))}
          </ul>
        )}
      </div>
    </Tarjeta>
  );
}

function MateriaRendimiento({ materia, abierta, onAlternar }) {
  const situacion = SITUACIONES[materia.situacion] ?? { texto: materia.situacion, tono: "neutro" };
  const idDetalle = `rendimiento-detalle-${materia.materiaInscritaId}`;

  const indicadores = [
    ["Promedio ponderado", materia.promedioPonderado !== null ? formatearNota(materia.promedioPonderado) : "Sin calificaciones"],
    ["Nota acumulada", `${formatearNota(materia.notaAcumulada)} de 10`],
    ["Calificado", formatearPorcentaje(materia.pesoCalificado)],
    ["Pendiente", formatearPorcentaje(materia.pesoPendiente)],
    ["Sin asignar", formatearPorcentaje(materia.pesoSinAsignar)],
    ["Nota necesaria", explicarNotaNecesaria(materia)],
  ];

  return (
    <li className="rounded-lg border border-line p-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <h3 className="font-semibold text-ink-900">{materia.codigo} - {materia.nombre}</h3>
        <div className="flex flex-wrap gap-2">
          <Insignia tono={situacion.tono}>{situacion.texto}</Insignia>
          {materia.planIncompleto && materia.situacion !== "SIN_ACTIVIDADES" && (
            <Insignia tono="advertencia" icono="fa-solid fa-triangle-exclamation">Plan de evaluación incompleto</Insignia>
          )}
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-3 text-sm md:grid-cols-3 lg:grid-cols-6">
        {indicadores.map(([etiqueta, valor]) => (
          <div key={etiqueta} className="rounded-lg bg-surface-muted p-3">
            <dt className="text-xs font-semibold uppercase tracking-wide text-ink-600">{etiqueta}</dt>
            <dd className="mt-1 font-semibold text-ink-900">{valor}</dd>
          </div>
        ))}
      </dl>

      {materia.planIncompleto && materia.situacion !== "SIN_ACTIVIDADES" && (
        <p className="mt-3 text-sm text-warning-700">
          Plan de evaluación incompleto: falta registrar el {formatearPorcentaje(materia.pesoSinAsignar)} de la evaluación.
          La nota necesaria se calcula solo sobre las actividades pendientes registradas.
        </p>
      )}

      {materia.actividades.length > 0 && (
        <div className="mt-3">
          <Boton
            variante="secundario"
            tamano="sm"
            aria-expanded={abierta}
            aria-controls={idDetalle}
            icono={abierta ? "fa-solid fa-chevron-up" : "fa-solid fa-chevron-down"}
            onClick={onAlternar}
          >
            {abierta ? "Ocultar actividades" : `Ver actividades (${materia.actividades.length})`}
          </Boton>

          {abierta && (
            <div id={idDetalle} className="mt-3">
              <TablaUi
                compacta
                columnas={[
                  { titulo: "Actividad", className: "min-w-40" },
                  { titulo: "Tipo", className: "whitespace-nowrap" },
                  { titulo: "%", alinear: "derecha" },
                  { titulo: "Nota", alinear: "derecha" },
                  { titulo: "Aporte", alinear: "derecha" },
                  "Calificación",
                ]}
                filas={materia.actividades.map((actividad) => ({
                  id: actividad.idActividad,
                  celdas: [
                    actividad.nombre,
                    actividad.tipoActividad,
                    actividad.sinPonderacion ? "—" : formatearPorcentaje(actividad.porcentajeEvaluacion),
                    actividad.calificada ? formatearNota(actividad.nota) : "—",
                    actividad.aporte !== null ? formatearNota(actividad.aporte) : "—",
                    actividad.sinPonderacion
                      ? <Insignia tono="neutro">Sin ponderación</Insignia>
                      : actividad.calificada
                        ? <Insignia tono="exito">Calificada</Insignia>
                        : <Insignia tono="advertencia">Pendiente de calificación</Insignia>,
                  ],
                }))}
              />
            </div>
          )}
        </div>
      )}
    </li>
  );
}

export default ReporteRendimientoPorMateria;
