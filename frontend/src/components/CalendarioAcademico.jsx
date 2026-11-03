import { useEffect, useMemo, useState } from "react";
import { obtenerCalendarioPeriodo, obtenerFranjasCalendario } from "../services/calendarioService";
import AgendaEventos from "./calendario/AgendaEventos";
import { ESTILOS_EVENTO, FILTROS_INICIALES, FRANJAS_INICIALES, TIPOS_FRANJA, eventoVisible } from "./calendario/constantes";
import FiltroFranjas from "./calendario/FiltroFranjas";
import FiltrosCalendario from "./calendario/FiltrosCalendario";
import VistaMensual from "./calendario/VistaMensual";
import VistaSemanal from "./calendario/VistaSemanal";
import {
  cuadriculaMes,
  diasDeLaSemana,
  fechaLocalHoy,
  formatearMes,
  formatearRango,
  mesDe,
  primerDiaMes,
  sumarDias,
  sumarMeses,
} from "./calendario/fechas";
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Tarjeta from "./ui/Tarjeta";
import { unirClases } from "./ui/unirClases";

const VISTAS = [
  { valor: "mes", etiqueta: "Mes" },
  { valor: "semana", etiqueta: "Semana" },
  { valor: "agenda", etiqueta: "Agenda" },
];

function Segmentado({ etiqueta, opciones, valor, onCambiar }) {
  return (
    <div role="group" aria-label={etiqueta} className="inline-grid auto-cols-fr grid-flow-col gap-1 rounded-xl border border-line bg-surface p-1">
      {opciones.map((opcion) => (
        <button
          key={opcion.valor}
          type="button"
          aria-pressed={valor === opcion.valor}
          onClick={() => onCambiar(opcion.valor)}
          className={unirClases(
            "min-h-11 rounded-lg px-3 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700",
            valor === opcion.valor ? "bg-brand-700 text-white" : "text-ink-900 hover:bg-surface-muted"
          )}
        >
          {opcion.etiqueta}
        </button>
      ))}
    </div>
  );
}

// SDACAS-21: calendario académico con vistas mensual, semanal y de agenda. Se pide una cuadrícula
// mensual completa (6 semanas) por mes visible; la semana y la agenda de cualquier día seleccionado ya
// están dentro de esos datos, así que filtrar o cambiar de vista no vuelve a consultar.
function CalendarioAcademico() {
  const hoy = fechaLocalHoy();
  const [seleccionada, setSeleccionada] = useState(hoy);
  const [vista, setVista] = useState("mes");
  const [modoAgenda, setModoAgenda] = useState("dia");
  const [filtros, setFiltros] = useState(FILTROS_INICIALES);
  const [periodo, setPeriodo] = useState(null);
  const [mesCargado, setMesCargado] = useState(null);
  const [error, setError] = useState("");
  // SDACAS-32: franjas de jornada laboral y disponibilidad, con su propio filtro y su propio error
  const [franjas, setFranjas] = useState(null);
  const [errorFranjas, setErrorFranjas] = useState(false);
  const [filtroFranjas, setFiltroFranjas] = useState(FRANJAS_INICIALES);

  const mes = mesDe(seleccionada);

  useEffect(() => {
    let vigente = true;
    const { desde, hasta } = cuadriculaMes(mes);
    setError("");

    obtenerCalendarioPeriodo(desde, hasta)
      .then((datos) => {
        if (!vigente) return;
        setPeriodo(datos);
        setMesCargado(mes);
      })
      .catch((errorConsulta) => {
        if (vigente) setError(errorConsulta.response?.data?.message || "No se pudo cargar el calendario.");
      });

    // Petición aparte: si falla, las clases y actividades se siguen mostrando
    setErrorFranjas(false);
    obtenerFranjasCalendario(desde, hasta)
      .then((datos) => {
        if (vigente) setFranjas({ mes, lista: datos.franjas });
      })
      .catch(() => {
        if (vigente) setErrorFranjas(true);
      });

    // Al cambiar de mes rápido, una respuesta lenta anterior no debe sobrescribir la del mes actual
    return () => {
      vigente = false;
    };
  }, [mes]);

  const listo = periodo !== null && mesCargado === mes;

  const { eventosPorFecha, conteosPorFecha, resumenPorFecha, totalVisibles } = useMemo(() => {
    const eventos = new Map();
    const conteos = new Map();
    const resumen = new Map();
    let total = 0;
    if (listo) {
      for (const evento of periodo.eventos) {
        if (!eventoVisible(evento, filtros)) continue;
        total++;
        if (!eventos.has(evento.fecha)) eventos.set(evento.fecha, []);
        eventos.get(evento.fecha).push(evento);
        const conteo = conteos.get(evento.fecha) ?? { clases: 0, actividades: 0 };
        if (evento.tipo === "CLASE") conteo.clases++;
        else conteo.actividades++;
        conteos.set(evento.fecha, conteo);
      }
      for (const dia of periodo.dias) resumen.set(dia.fecha, dia);
    }
    return { eventosPorFecha: eventos, conteosPorFecha: conteos, resumenPorFecha: resumen, totalVisibles: total };
  }, [listo, periodo, filtros]);

  const franjasListas = franjas !== null && franjas.mes === mes;
  const franjasPorFecha = useMemo(() => {
    const porFecha = new Map();
    if (franjasListas) {
      for (const franja of franjas.lista) {
        if (!filtroFranjas[franja.tipo]) continue;
        if (!porFecha.has(franja.fecha)) porFecha.set(franja.fecha, []);
        porFecha.get(franja.fecha).push(franja);
      }
    }
    return porFecha;
  }, [franjasListas, franjas, filtroFranjas]);

  // Al cambiar de mes se selecciona hoy si es el mes actual y, si no, el día 1
  const irAMes = (meses) => {
    const nuevoMes = sumarMeses(mes, meses);
    setSeleccionada(nuevoMes === mesDe(hoy) ? hoy : primerDiaMes(nuevoMes));
  };
  const navegaPorMes = vista === "mes";
  const anterior = () => (navegaPorMes ? irAMes(-1) : setSeleccionada(sumarDias(seleccionada, -7)));
  const siguiente = () => (navegaPorMes ? irAMes(1) : setSeleccionada(sumarDias(seleccionada, 7)));

  const semana = diasDeLaSemana(seleccionada);
  const titulo = navegaPorMes ? formatearMes(mes) : formatearRango(semana[0], semana[6]);
  const unidad = navegaPorMes ? "Mes" : "Semana";
  const mesSinEventos = listo && periodo.eventos.every((evento) => mesDe(evento.fecha) !== mes);
  // Franjas registradas en la semana mostrada, sin aplicar el filtro, para el estado vacío
  const franjasDeLaSemana = franjasListas ? franjas.lista.filter((franja) => semana.includes(franja.fecha)).length : null;

  return (
    <section className="space-y-4">
      <EncabezadoModulo
        descripcion="Clases del ciclo activo y actividades académicas por mes, semana o agenda."
        icono="fa-regular fa-calendar"
      />

      <div className="flex flex-col gap-3 rounded-xl border border-line bg-surface p-3 shadow-sm lg:flex-row lg:items-center lg:justify-between">
        <p className="text-lg font-bold text-ink-900" aria-live="polite">{titulo}</p>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
          <div className="grid grid-cols-3 gap-2 sm:flex">
            <Boton variante="secundario" icono="fa-solid fa-chevron-left" onClick={anterior} aria-label={`${unidad} anterior`}>
              <span className="hidden sm:inline">Anterior</span>
            </Boton>
            <Boton variante="secundario" onClick={() => setSeleccionada(hoy)}>Hoy</Boton>
            <Boton variante="secundario" onClick={siguiente} aria-label={`${unidad} siguiente`}>
              <span className="hidden sm:inline">Siguiente</span>
              <i className="fa-solid fa-chevron-right" aria-hidden="true"></i>
            </Boton>
          </div>
          <Segmentado etiqueta="Vista del calendario" opciones={VISTAS} valor={vista} onCambiar={setVista} />
        </div>
      </div>

      <FiltrosCalendario filtros={filtros} onCambiar={setFiltros} />

      {vista === "semana" && (
        <FiltroFranjas valores={filtroFranjas} onCambiar={setFiltroFranjas} franjasDeLaSemana={franjasDeLaSemana} />
      )}

      <div className="flex flex-wrap items-center gap-4 text-sm text-ink-600" aria-label="Leyenda del calendario">
        <span className="flex items-center gap-2">
          <span className={unirClases("h-4 w-4 rounded border-l-4", ESTILOS_EVENTO.CLASE)} aria-hidden="true"></span>
          Clase
        </span>
        <span className="flex items-center gap-2">
          <span className={unirClases("h-4 w-4 rounded border-l-4", ESTILOS_EVENTO.ACTIVIDAD)} aria-hidden="true"></span>
          Actividad académica
        </span>
        <span className="flex items-center gap-2">
          <span className={unirClases("h-4 w-4 rounded border-l-4 opacity-60", ESTILOS_EVENTO.ACTIVIDAD)} aria-hidden="true"></span>
          Completada o calificada
        </span>
        <span className="flex items-center gap-2">
          <i className="fa-solid fa-triangle-exclamation text-danger-700" aria-hidden="true"></i>
          Concentración de evaluaciones
        </span>
        {vista === "semana" && Object.entries(TIPOS_FRANJA).map(([clave, tipo]) => (
          <span key={clave} className="flex items-center gap-2">
            <span className={unirClases("h-4 w-4 rounded border border-dashed", tipo.estilo)} aria-hidden="true"></span>
            {tipo.etiqueta}
          </span>
        ))}
      </div>

      {error && <Aviso tono="peligro">{error}</Aviso>}

      {vista === "semana" && errorFranjas && (
        <Aviso tono="advertencia">
          No se pudieron cargar la jornada laboral ni la disponibilidad. Las clases y actividades se muestran igualmente.
        </Aviso>
      )}

      {listo && periodo.actividadesSinFecha > 0 && (
        <Aviso tono="advertencia">
          {periodo.actividadesSinFecha === 1
            ? "1 actividad no tiene fecha de entrega ni de inicio y no aparece en el calendario."
            : `${periodo.actividadesSinFecha} actividades no tienen fecha de entrega ni de inicio y no aparecen en el calendario.`}
        </Aviso>
      )}

      {navegaPorMes && mesSinEventos && (
        <Aviso tono="info">No hay clases ni actividades en {formatearMes(mes).toLowerCase()}.</Aviso>
      )}

      {listo && totalVisibles === 0 && periodo.eventos.length > 0 && (
        <Aviso tono="info">Ningún evento coincide con los filtros seleccionados.</Aviso>
      )}

      {vista === "mes" && (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
          <Tarjeta className="p-3 sm:p-4" aria-busy={!listo}>
            <VistaMensual
              seleccionada={seleccionada}
              hoy={hoy}
              conteosPorFecha={conteosPorFecha}
              resumenPorFecha={resumenPorFecha}
              onSeleccionar={setSeleccionada}
            />
          </Tarjeta>
          <Tarjeta
            titulo="Agenda"
            nivelTitulo={2}
            icono="fa-solid fa-list-ul"
            acciones={
              <Segmentado
                etiqueta="Periodo de la agenda"
                opciones={[{ valor: "dia", etiqueta: "Día" }, { valor: "semana", etiqueta: "Semana" }]}
                valor={modoAgenda}
                onCambiar={setModoAgenda}
              />
            }
          >
            <AgendaEventos
              fechas={modoAgenda === "dia" ? [seleccionada] : semana}
              hoy={hoy}
              eventosPorFecha={eventosPorFecha}
              resumenPorFecha={resumenPorFecha}
              cargando={!listo}
            />
          </Tarjeta>
        </div>
      )}

      {vista === "semana" && (
        <VistaSemanal
          seleccionada={seleccionada}
          hoy={hoy}
          eventosPorFecha={eventosPorFecha}
          franjasPorFecha={franjasPorFecha}
          onSeleccionar={setSeleccionada}
        />
      )}

      {vista === "agenda" && (
        <Tarjeta titulo={`Agenda de la semana · ${formatearRango(semana[0], semana[6])}`} nivelTitulo={2} icono="fa-solid fa-list-ul">
          <AgendaEventos
            fechas={semana}
            hoy={hoy}
            eventosPorFecha={eventosPorFecha}
            resumenPorFecha={resumenPorFecha}
            cargando={!listo}
          />
        </Tarjeta>
      )}
    </section>
  );
}

export default CalendarioAcademico;
