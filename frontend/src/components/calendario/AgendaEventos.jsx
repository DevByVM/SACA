import EstadoVacio from "../ui/EstadoVacio";
import Insignia from "../ui/Insignia";
import EventoCalendario from "./EventoCalendario";
import { formatearFechaLarga, formatearHoras } from "./fechas";

// Lista cronológica de uno o varios días. Las horas pendientes y la concentración de cada día salen
// del resumen del backend (reglas de la carga semanal) y no dependen de los filtros.
function AgendaEventos({ fechas, hoy, eventosPorFecha, resumenPorFecha, cargando }) {
  if (cargando) {
    return <EstadoVacio cargando mensaje="Cargando agenda..." />;
  }

  const unSoloDia = fechas.length === 1;
  const totalEventos = fechas.reduce((total, fecha) => total + (eventosPorFecha.get(fecha)?.length ?? 0), 0);

  if (unSoloDia && totalEventos === 0) {
    const resumen = resumenPorFecha.get(fechas[0]);
    const ocultosPorFiltro = resumen && resumen.clases + resumen.actividades > 0;
    return (
      <EstadoVacio
        icono="fa-regular fa-calendar"
        mensaje={ocultosPorFiltro
          ? "Los eventos de este día no coinciden con los filtros seleccionados."
          : "No hay clases ni actividades este día."}
      />
    );
  }

  return (
    <div className="space-y-4">
      {!unSoloDia && totalEventos === 0 && (
        <EstadoVacio icono="fa-regular fa-calendar" mensaje="No hay clases ni actividades visibles en esta semana." />
      )}
      {fechas.map((fecha) => {
        const eventos = eventosPorFecha.get(fecha) ?? [];
        const resumen = resumenPorFecha.get(fecha);
        const horas = Number(resumen?.horasPendientes ?? 0);
        return (
          <section key={fecha} aria-label={formatearFechaLarga(fecha)}>
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-bold text-ink-900">{formatearFechaLarga(fecha)}</h3>
              {fecha === hoy && <Insignia tono="marca">Hoy</Insignia>}
              {horas > 0 && <Insignia tono="neutro">{formatearHoras(horas)} pendientes</Insignia>}
              {resumen?.concentracion && (
                <Insignia tono="peligro" icono="fa-solid fa-triangle-exclamation">
                  Concentración: {resumen.evaluacionesAltoPeso} evaluaciones de 30 % o más
                </Insignia>
              )}
            </div>
            {eventos.length === 0 ? (
              <p className="text-xs text-ink-600">Sin eventos</p>
            ) : (
              <ul className="space-y-2">
                {eventos.map((evento) => <EventoCalendario key={evento.id} evento={evento} />)}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}

export default AgendaEventos;
