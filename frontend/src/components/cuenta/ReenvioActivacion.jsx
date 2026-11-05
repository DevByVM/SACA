import { useState } from "react";

import { reenviarActivacion } from "../../services/authService";
import { validarIdentificador } from "../../utils/cuenta";
import Aviso from "../ui/Aviso";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";

/**
 * Solicitud de otro enlace de activación con el carnet o el correo institucional. La respuesta del backend es la
 * misma haya o no una cuenta pendiente, así que aquí tampoco se distingue.
 *
 * Props:
 * - identificadorInicial: carnet o correo con el que se abre el formulario.
 * - onVolver(): vuelve al inicio de sesión.
 */
function ReenvioActivacion({ identificadorInicial = "", onVolver }) {
    const [identificador, setIdentificador] = useState(identificadorInicial);
    const [error, setError] = useState("");
    const [errorServidor, setErrorServidor] = useState("");
    const [enviando, setEnviando] = useState(false);
    const [respuesta, setRespuesta] = useState(null);

    const enviar = async (evento) => {
        evento.preventDefault();
        const errorIdentificador = validarIdentificador(identificador);
        if (errorIdentificador) {
            setError(errorIdentificador);
            return;
        }

        setError("");
        setErrorServidor("");
        setEnviando(true);
        try {
            setRespuesta(await reenviarActivacion(identificador.trim()));
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
                    Usa siempre el último enlace que recibas: al pedir otro, los anteriores dejan de servir. Puedes
                    pedir uno por minuto y hasta cinco al día.
                </p>
                <Boton variante="texto" onClick={onVolver} className="w-full">
                    Volver al inicio de sesión
                </Boton>
            </div>
        );
    }

    return (
        <>
            <p className="mb-4 text-sm text-ink-600">
                Si tu cuenta aún no está activada, te enviaremos un enlace nuevo a tu correo institucional.
            </p>

            <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
                <Campo
                    label="Carnet o correo institucional"
                    name="identificador"
                    value={identificador}
                    onValorChange={setIdentificador}
                    ayuda="Por ejemplo AP23001 o ap23001@ues.edu.sv."
                    error={error || undefined}
                    autoComplete="off"
                    disabled={enviando}
                />

                {errorServidor && <Aviso tono="peligro">{errorServidor}</Aviso>}

                <Boton type="submit" className="mt-2 min-h-11 w-full" disabled={enviando}>
                    {enviando ? "Enviando…" : "Enviar enlace"}
                </Boton>
            </form>

            <Boton variante="texto" onClick={onVolver} className="mt-5 w-full" disabled={enviando}>
                Volver al inicio de sesión
            </Boton>
        </>
    );
}

export default ReenvioActivacion;
