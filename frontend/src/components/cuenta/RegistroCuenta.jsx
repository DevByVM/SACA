import { useState } from "react";

import { registrar } from "../../services/authService";
import { AYUDA_CARNET, MENSAJE_FORMATO_CARNET, carnetValido, normalizarCarnet } from "../../utils/carnet";
import Aviso from "../ui/Aviso";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";

/**
 * Solicitud de cuenta con el carnet. El backend responde lo mismo exista o no la cuenta, así que aquí tampoco se
 * distingue: tras enviarla se muestra su mensaje y el correo al que llegaría el enlace.
 *
 * Props:
 * - onReenviar(carnet): abre el reenvío del enlace con el carnet ya escrito.
 * - onVolver(): vuelve al inicio de sesión.
 */
function RegistroCuenta({ onReenviar, onVolver }) {
    const [carnet, setCarnet] = useState("");
    const [error, setError] = useState("");
    const [errorServidor, setErrorServidor] = useState("");
    const [enviando, setEnviando] = useState(false);
    const [respuesta, setRespuesta] = useState(null);

    const enviar = async (evento) => {
        evento.preventDefault();
        const normalizado = normalizarCarnet(carnet);
        if (!carnetValido(normalizado)) {
            setError(normalizado ? MENSAJE_FORMATO_CARNET : "Carnet obligatorio");
            return;
        }

        setError("");
        setErrorServidor("");
        setEnviando(true);
        try {
            setRespuesta(await registrar(normalizado));
        } catch (fallo) {
            setErrorServidor(fallo.message);
        } finally {
            setEnviando(false);
        }
    };

    if (respuesta) {
        return (
            <div className="flex flex-col gap-4">
                <Aviso tono="exito" titulo="Solicitud recibida">
                    <p>{respuesta.mensaje}</p>
                    <p className="mt-2 break-all font-medium">{respuesta.correoInstitucional}</p>
                </Aviso>
                <p className="text-sm text-ink-600">
                    Abre el enlace del correo para elegir tu nombre y tu contraseña; vence en 24 horas. Si no lo ves,
                    revisa la carpeta de correo no deseado.
                </p>
                <Boton variante="secundario" onClick={() => onReenviar(normalizarCarnet(carnet))} className="min-h-11 w-full">
                    ¿No te llegó? Solicitar otro enlace
                </Boton>
                <Boton variante="texto" onClick={onVolver} className="w-full">
                    Volver al inicio de sesión
                </Boton>
            </div>
        );
    }

    return (
        <>
            <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
                <Campo
                    label="Carnet"
                    name="carnet"
                    value={carnet}
                    onValorChange={setCarnet}
                    ayuda={`${AYUDA_CARNET} Te enviaremos un enlace a tu correo institucional.`}
                    error={error || undefined}
                    autoComplete="off"
                    disabled={enviando}
                />

                {errorServidor && <Aviso tono="peligro">{errorServidor}</Aviso>}

                <Boton type="submit" className="mt-2 min-h-11 w-full" disabled={enviando}>
                    {enviando ? "Enviando…" : "Solicitar cuenta"}
                </Boton>
            </form>

            <Boton variante="texto" onClick={onVolver} className="mt-5 w-full" disabled={enviando}>
                ¿Ya tienes cuenta? Inicia sesión
            </Boton>
        </>
    );
}

export default RegistroCuenta;
