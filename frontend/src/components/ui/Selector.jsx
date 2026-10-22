import { useId } from "react";
import { unirClases } from "./unirClases";

const CLASES_CONTROL =
  "block w-full rounded-lg border bg-surface px-3 py-2 text-sm text-ink-900 outline-none transition focus:ring-2 disabled:cursor-not-allowed disabled:bg-surface-muted";

const CLASES_ESTADO = {
  normal: "border-line focus:border-brand-700 focus:ring-brand-700/20",
  error: "border-danger-700 focus:border-danger-700 focus:ring-danger-700/20",
};

// Mismo contrato que Campo: onChange recibe el evento y onValorChange el valor.
// El valor llega como texto, tal como lo entrega el DOM; los formularios ya lo
// convierten con Number() antes de armar el payload.
// placeholder={null} omite la opción vacía inicial.
function Selector({
  label,
  options = [],
  placeholder = "Seleccione",
  required = true,
  onChange,
  onValorChange,
  ayuda,
  error,
  id,
  className,
  "aria-describedby": describedBy,
  ...props
}) {
  const idGenerado = useId();
  const idCampo = id || idGenerado;
  const idAyuda = `${idCampo}-ayuda`;
  const idError = `${idCampo}-error`;

  function manejarCambio(event) {
    onChange?.(event);
    if (onValorChange && onValorChange !== onChange) {
      onValorChange(event.target.value);
    }
  }

  return (
    <div className={className}>
      {label && (
        <label htmlFor={idCampo} className="block text-sm font-semibold text-ink-900">
          {label}
        </label>
      )}
      <select
        {...props}
        id={idCampo}
        required={required}
        onChange={manejarCambio}
        aria-invalid={error ? true : undefined}
        aria-describedby={unirClases(describedBy, ayuda && idAyuda, error && idError) || undefined}
        className={unirClases(
          CLASES_CONTROL,
          error ? CLASES_ESTADO.error : CLASES_ESTADO.normal,
          label && "mt-1"
        )}
      >
        {placeholder !== null && <option value="">{placeholder}</option>}
        {options.map((option) => {
          const valueOption = typeof option === "string" ? option : option.value;
          const labelOption = typeof option === "string" ? option : option.label;
          return <option key={valueOption} value={valueOption}>{labelOption}</option>;
        })}
      </select>
      {ayuda && <p id={idAyuda} className="mt-1 text-xs text-ink-600">{ayuda}</p>}
      {error && <p id={idError} className="mt-1 text-xs font-semibold text-danger-700">{error}</p>}
    </div>
  );
}

export default Selector;
