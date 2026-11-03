import { useEffect, useRef } from "react";
import { unirClases } from "../ui/unirClases";
import { DIAS_CORTOS, DIAS_SEMANA, cuadriculaMes, formatearFechaLarga, formatearHoras, formatearMes, mesDe, sumarDias } from "./fechas";

// Desplazamiento de la selección con las flechas: un día a los lados, una semana arriba y abajo
const MOVIMIENTOS = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };

function describirDia(fecha, conteo, resumen) {
  const partes = [formatearFechaLarga(fecha)];
  partes.push(conteo.clases === 1 ? "1 clase" : `${conteo.clases} clases`);
  partes.push(conteo.actividades === 1 ? "1 actividad" : `${conteo.actividades} actividades`);
  if (resumen && Number(resumen.horasPendientes) > 0) partes.push(`${formatearHoras(resumen.horasPendientes)} pendientes`);
  if (resumen?.concentracion) partes.push("concentración de evaluaciones");
  return partes.join(", ");
}

// Cuadrícula mensual de 6 semanas. Los puntos cuentan los eventos visibles con los filtros; las horas
// pendientes y la concentración vienen del resumen del backend y no dependen de los filtros.
function VistaMensual({ seleccionada, hoy, conteosPorFecha, resumenPorFecha, onSeleccionar }) {
  const mes = mesDe(seleccionada);
  const { desde } = cuadriculaMes(mes);
  const dias = Array.from({ length: 42 }, (_, indice) => sumarDias(desde, indice));
  const cuadriculaRef = useRef(null);
  const enfocarSeleccion = useRef(false);

  // Tras moverse con el teclado, el foco sigue a la fecha seleccionada (también al cambiar de mes)
  useEffect(() => {
    if (!enfocarSeleccion.current) return;
    enfocarSeleccion.current = false;
    cuadriculaRef.current?.querySelector(`[data-fecha="${seleccionada}"]`)?.focus();
  }, [seleccionada]);

  function manejarTecla(evento) {
    const desplazamiento = MOVIMIENTOS[evento.key];
    if (desplazamiento === undefined) return;
    evento.preventDefault();
    enfocarSeleccion.current = true;
    onSeleccionar(sumarDias(seleccionada, desplazamiento));
  }

  return (
    <div role="group" aria-label={`Calendario de ${formatearMes(mes)}`} ref={cuadriculaRef} onKeyDown={manejarTecla}>
      <div className="grid grid-cols-7 gap-1 pb-1 text-center text-xs font-bold uppercase text-ink-600" aria-hidden="true">
        {DIAS_CORTOS.map((dia, indice) => (
          <span key={dia} title={DIAS_SEMANA[indice]}>{dia}</span>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1">
        {dias.map((fecha) => {
          const conteo = conteosPorFecha.get(fecha) ?? { clases: 0, actividades: 0 };
          const resumen = resumenPorFecha.get(fecha);
          const delMes = mesDe(fecha) === mes;
          const esSeleccionada = fecha === seleccionada;
          const horas = Number(resumen?.horasPendientes ?? 0);

          return (
            <button
              key={fecha}
              type="button"
              data-fecha={fecha}
              tabIndex={esSeleccionada ? 0 : -1}
              aria-pressed={esSeleccionada}
              aria-label={describirDia(fecha, conteo, resumen)}
              onClick={() => onSeleccionar(fecha)}
              className={unirClases(
                "flex min-h-11 min-w-0 flex-col items-center gap-0.5 rounded-lg border p-1 text-xs transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-700 sm:min-h-20 sm:items-stretch sm:p-1.5",
                esSeleccionada ? "border-brand-700 bg-brand-50" : "border-line bg-surface hover:bg-surface-muted",
                !delMes && "opacity-50",
                resumen?.concentracion && !esSeleccionada && "border-danger-200"
              )}
            >
              <span className="flex w-full items-center justify-center gap-1 sm:justify-between">
                <span
                  className={unirClases(
                    "flex h-6 w-6 items-center justify-center rounded-full font-semibold",
                    fecha === hoy ? "bg-brand-700 text-white" : "text-ink-900"
                  )}
                >
                  {Number(fecha.slice(8))}
                </span>
                {resumen?.concentracion && (
                  <i className="fa-solid fa-triangle-exclamation hidden text-danger-700 sm:inline" aria-hidden="true"></i>
                )}
              </span>

              <span className="flex flex-wrap items-center justify-center gap-1 sm:justify-start" aria-hidden="true">
                {conteo.clases > 0 && (
                  <span className="flex items-center gap-0.5 text-brand-700">
                    <span className="h-2 w-2 rounded-full bg-brand-700"></span>
                    <span className="hidden sm:inline">{conteo.clases}</span>
                  </span>
                )}
                {conteo.actividades > 0 && (
                  <span className="flex items-center gap-0.5 text-info-700">
                    <span className="h-2 w-2 rounded-full bg-info-700"></span>
                    <span className="hidden sm:inline">{conteo.actividades}</span>
                  </span>
                )}
                {resumen?.concentracion && <span className="h-2 w-2 rounded-full bg-danger-700 sm:hidden"></span>}
              </span>

              {horas > 0 && (
                <span className="hidden text-ink-600 sm:block" aria-hidden="true">{formatearHoras(horas)}</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default VistaMensual;
