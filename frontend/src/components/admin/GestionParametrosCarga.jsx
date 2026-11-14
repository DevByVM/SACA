import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { guardarParametrosCargaAdmin, obtenerParametrosCargaAdmin } from "../../api/adminApi";
import { validarUmbrales } from "../../utils/parametrosCarga";
import Aviso from "../ui/Aviso";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import DialogoConfirmacion from "../ui/DialogoConfirmacion";
import Tarjeta from "../ui/Tarjeta";

function GestionParametrosCarga() {
  const [guardados, setGuardados] = useState(null);
  const [form, setForm] = useState({ umbralElevada: "", umbralCritica: "" });
  const [cargando, setCargando] = useState(true);
  const [procesando, setProcesando] = useState(false);
  const [confirmando, setConfirmando] = useState(false);
  const [error, setError] = useState("");

  const cargar = useCallback(async () => {
    setCargando(true);
    setError("");
    try {
      const datos = await obtenerParametrosCargaAdmin();
      setGuardados(datos);
      setForm(datos);
    } catch (fallo) {
      setError(fallo instanceof TypeError
        ? "No se pudo conectar con el servidor. Revisa la conexión e inténtalo de nuevo."
        : fallo.message || "No se pudieron cargar los parámetros.");
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  function cancelar() {
    setForm(guardados);
    setError("");
    setConfirmando(false);
  }

  function solicitarGuardar(evento) {
    evento.preventDefault();
    const mensaje = validarUmbrales(form.umbralElevada, form.umbralCritica);
    setError(mensaje || "");
    if (!mensaje) setConfirmando(true);
  }

  async function guardar() {
    setProcesando(true);
    setError("");
    try {
      const datos = await guardarParametrosCargaAdmin({
        umbralElevada: Number(form.umbralElevada),
        umbralCritica: Number(form.umbralCritica),
      });
      setGuardados(datos);
      setForm(datos);
      toast.success("Parámetros de carga guardados.");
    } catch (fallo) {
      setError(fallo instanceof TypeError
        ? "No se pudo confirmar el guardado. Revisa la conexión e inténtalo de nuevo."
        : fallo.message || "No se pudieron guardar los parámetros.");
    } finally {
      setProcesando(false);
      setConfirmando(false);
    }
  }

  if (cargando) return <p role="status">Cargando parámetros de carga...</p>;
  if (!guardados) return <Tarjeta titulo="Parámetros de carga académica">
    <Aviso tono="peligro">{error}</Aviso>
    <Boton onClick={cargar} className="mt-4">Reintentar</Boton>
  </Tarjeta>;

  const cambiado = Number(form.umbralElevada) !== guardados.umbralElevada
    || Number(form.umbralCritica) !== guardados.umbralCritica;

  return (
    <Tarjeta titulo="Umbrales globales de horas">
      <p className="mb-4 text-sm text-ink-600">
        Una sola configuración para todos los estudiantes y todas las semanas.
        Los parámetros de porcentajes y cantidad de evaluaciones se conservan.
      </p>
      <p className="mb-4 text-sm">Valores guardados: {guardados.umbralElevada} y {guardados.umbralCritica} horas.</p>
      <form onSubmit={solicitarGuardar} className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo label="Primer umbral (horas)" type="number" step="any"
            value={form.umbralElevada} disabled={procesando}
            onValorChange={(valor) => setForm((anterior) => ({ ...anterior, umbralElevada: valor }))}
            ayuda="Debe ser positivo y menor que el segundo umbral." />
          <Campo label="Segundo umbral (horas)" type="number" step="any"
            value={form.umbralCritica} disabled={procesando}
            onValorChange={(valor) => setForm((anterior) => ({ ...anterior, umbralCritica: valor }))}
            ayuda="Debe ser mayor que el primer umbral." />
        </div>
        {error && <Aviso tono="peligro">{error}</Aviso>}
        <div className="flex flex-wrap gap-2">
          <Boton type="submit" disabled={procesando || !cambiado}>Guardar cambios</Boton>
          <Boton variante="secundario" disabled={procesando || !cambiado} onClick={cancelar}>Cancelar</Boton>
        </div>
      </form>
      <DialogoConfirmacion
        abierto={confirmando}
        titulo="¿Guardar los umbrales globales?"
        mensaje={`Vas a guardar ${form.umbralElevada} y ${form.umbralCritica} horas. Esta configuración es global para todos los estudiantes y todas las semanas. No modifica las reglas de porcentajes ni la cantidad de evaluaciones.`}
        textoConfirmar="Confirmar y guardar"
        tono="normal"
        onConfirmar={guardar}
        onCancelar={cancelar}
        procesando={procesando}
      />
    </Tarjeta>
  );
}

export default GestionParametrosCarga;
