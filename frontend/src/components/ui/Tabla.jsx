import { unirClases } from "./unirClases";
import EstadoVacio from "./EstadoVacio";

const ALINEACIONES = {
  izquierda: "text-left",
  centro: "text-center",
  derecha: "text-right",
};

function normalizarColumna(columna) {
  return typeof columna === "string" ? { titulo: columna } : columna;
}

// Solo null, undefined y "" se consideran sin dato; 0 y false se muestran.
// React no pinta booleanos, por eso se convierten a texto.
function contenidoCelda(valor, textoSinDato) {
  if (valor === null || valor === undefined || valor === "") {
    return <span className="text-ink-500">{textoSinDato}</span>;
  }

  if (typeof valor === "boolean") return String(valor);

  return valor;
}

// anchoTabla reemplaza el ancho por defecto en lugar de combinarse con él,
// para evitar choques como w-full con w-max.
function Tabla({
  columnas = [],
  filas = [],
  compacta = false,
  anchoTabla = "w-full",
  cargando = false,
  mensajeCarga,
  mensajeVacio,
  textoSinDato = "Sin dato",
  className,
  ...props
}) {
  const columnasNormalizadas = columnas.map(normalizarColumna);
  const relleno = compacta ? "px-2 py-2" : "px-3 py-2";
  const alinear = (columna) => ALINEACIONES[columna?.alinear] || ALINEACIONES.izquierda;
  const mostrarEstado = cargando || filas.length === 0;

  return (
    <div className={unirClases("w-full max-w-full overflow-x-auto", className)}>
      <table className={unirClases(anchoTabla, compacta ? "text-xs" : "text-sm")} {...props}>
        <thead className="bg-surface-muted text-ink-900">
          <tr>
            {columnasNormalizadas.map((columna, indice) => (
              <th key={indice} scope="col" className={unirClases(relleno, "font-semibold", alinear(columna))}>
                {columna.titulo}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {mostrarEstado ? (
            <tr>
              <td colSpan={columnasNormalizadas.length || 1} className="p-3">
                <EstadoVacio cargando={cargando} mensaje={cargando ? mensajeCarga : mensajeVacio} />
              </td>
            </tr>
          ) : (
            filas.map((fila, indiceFila) => (
              <tr
                key={fila.id ?? indiceFila}
                // Una fila con className propio (p. ej. el fondo de un conflicto) no lleva el
                // hover: hover:bg-canvas tiene más especificidad y taparía ese fondo.
                className={unirClases(
                  "border-b border-line text-ink-900 transition-colors",
                  !fila.className && "hover:bg-canvas",
                  fila.className
                )}
              >
                {fila.celdas.map((celda, indice) => {
                  const columna = columnasNormalizadas[indice];
                  return (
                    <td key={indice} className={unirClases(relleno, alinear(columna), columna?.className)}>
                      {contenidoCelda(celda, textoSinDato)}
                    </td>
                  );
                })}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

export default Tabla;
