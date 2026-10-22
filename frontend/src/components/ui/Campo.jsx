import { useId, useRef } from "react";
import { unirClases } from "./unirClases";

const CLASES_CONTROL =
  "block w-full rounded-lg border bg-surface px-3 py-2 text-sm text-ink-900 outline-none transition focus:ring-2 disabled:cursor-not-allowed disabled:bg-surface-muted";

const CLASES_ESTADO = {
  normal: "border-ink-500 focus:border-brand-700 focus:ring-brand-700/20",
  error: "border-danger-700 focus:border-danger-700 focus:ring-danger-700/20",
};

// onChange recibe el evento nativo, como un <input>; onValorChange recibe solo el valor.
// Si se pasan ambos se llama primero a onChange y después a onValorChange, una vez cada uno;
// si son la misma función, solo se llama como onChange.
function Campo({
  label,
  type = "text",
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
  const inputRef = useRef(null);

  const hasPicker = type === "date" || type === "time";
  const pickerIcon = type === "date" ? "fa-calendar-days" : "fa-clock";
  const pickerLabel = typeof label === "string"
    ? `Abrir selector de ${label.toLowerCase()}`
    : `Abrir selector de ${type === "date" ? "fecha" : "hora"}`;

  function manejarCambio(event) {
    onChange?.(event);
    if (onValorChange && onValorChange !== onChange) {
      onValorChange(event.target.value);
    }
  }

  // El botón se deshabilita junto con el input porque showPicker lanza una
  // excepción si el input está deshabilitado o es de solo lectura.
  function abrirPicker() {
    if (inputRef.current?.showPicker) {
      inputRef.current.showPicker();
      return;
    }

    inputRef.current?.focus();
  }

  return (
    <div className={className}>
      {label && (
        <label htmlFor={idCampo} className="block text-sm font-semibold text-ink-900">
          {label}
        </label>
      )}
      <div className={unirClases("relative", label && "mt-1")}>
        <input
          {...props}
          ref={inputRef}
          id={idCampo}
          type={type}
          required={required}
          onChange={manejarCambio}
          aria-invalid={error ? true : undefined}
          aria-describedby={unirClases(describedBy, ayuda && idAyuda, error && idError) || undefined}
          className={unirClases(
            CLASES_CONTROL,
            error ? CLASES_ESTADO.error : CLASES_ESTADO.normal,
            hasPicker && "pr-11"
          )}
        />

        {hasPicker && (
          <button
            aria-label={pickerLabel}
            className="absolute inset-y-1 right-1 flex w-9 items-center justify-center rounded-md text-brand-700 transition hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-60"
            disabled={props.disabled || props.readOnly}
            type="button"
            onClick={abrirPicker}
          >
            <i className={`fa-solid ${pickerIcon}`} aria-hidden="true"></i>
          </button>
        )}
      </div>
      {ayuda && <p id={idAyuda} className="mt-1 text-xs text-ink-600">{ayuda}</p>}
      {error && <p id={idError} className="mt-1 text-xs font-semibold text-danger-700">{error}</p>}
    </div>
  );
}

export default Campo;
