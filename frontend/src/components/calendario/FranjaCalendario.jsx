import { unirClases } from "../ui/unirClases";
import { TIPOS_FRANJA } from "./constantes";
import { hora } from "./fechas";

// Franja de jornada laboral o de disponibilidad en la vista semanal
function FranjaCalendario({ franja }) {
  const tipo = TIPOS_FRANJA[franja.tipo];
  return (
    <li className={unirClases("flex items-center gap-2 rounded-lg border border-dashed px-2 py-1.5 text-xs", tipo.estilo)}>
      <i className={unirClases(tipo.icono, "shrink-0")} aria-hidden="true"></i>
      <span className="min-w-0">
        <span className="font-semibold">{tipo.etiqueta}</span>
        <span className="font-mono"> {hora(franja.horaInicio)}–{hora(franja.horaFin)}</span>
      </span>
    </li>
  );
}

export default FranjaCalendario;
