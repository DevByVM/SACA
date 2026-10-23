import Boton from "./Boton";
import Modal from "./Modal";

const ICONOS = {
  peligro: "fa-solid fa-triangle-exclamation",
  normal: "fa-solid fa-circle-question",
};

// Componente controlado: no se cierra solo. Quien lo usa decide en onConfirmar
// y onCancelar cuándo poner abierto en false. Escape y el fondo equivalen a
// cancelar y se pueden desactivar; mientras procesando es true no se cancela.
function DialogoConfirmacion({
  abierto,
  titulo,
  mensaje,
  textoConfirmar = "Confirmar",
  textoCancelar = "Cancelar",
  onConfirmar,
  onCancelar,
  tono = "peligro",
  procesando = false,
  cerrarConEscape = true,
  cerrarAlPulsarFondo = true,
}) {
  const esPeligro = tono !== "normal";

  return (
    <Modal
      abierto={abierto}
      titulo={titulo}
      descripcion={mensaje}
      icono={esPeligro ? ICONOS.peligro : ICONOS.normal}
      tonoIcono={esPeligro ? "peligro" : "marca"}
      onCerrar={onCancelar}
      ancho="md"
      rol="alertdialog"
      mostrarCerrar={false}
      cerrarConEscape={cerrarConEscape && !procesando}
      cerrarAlPulsarFondo={cerrarAlPulsarFondo && !procesando}
    >
      <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:flex-row sm:justify-end">
        <Boton variante="secundario" disabled={procesando} onClick={onCancelar}>
          {textoCancelar}
        </Boton>
        <Boton variante={esPeligro ? "peligro" : "primario"} disabled={procesando} onClick={onConfirmar}>
          {textoConfirmar}
        </Boton>
      </div>
    </Modal>
  );
}

export default DialogoConfirmacion;
