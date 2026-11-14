import { useEffect, useState } from "react";

import { activarCuenta, consultarActivacion, consultarRestablecimiento, restablecerContrasenia } from "../../services/authService";
import {
  AYUDA_CONTRASENIA,
  ESTADOS_ENLACE,
  MENSAJES_ENLACE,
  MENSAJE_CONFIRMACION,
  validarContrasenia,
  validarNombre,
} from "../../utils/cuenta";
import Aviso from "../ui/Aviso";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import PantallaAcceso from "./PantallaAcceso";

/**
 * Activación de la cuenta desde el enlace del correo. Primero se comprueba el enlace (sin consumirlo) y, si es
 * válido, se piden el nombre y la contraseña. Activar no inicia sesión: se vuelve al inicio de sesión con el correo.
 *
 * El token solo vive en el estado de la aplicación: no se guarda en el navegador ni se muestra.
 *
 * Props:
 * - token: el del enlace.
 * - onActivada(correoInstitucional): la cuenta quedó activada.
 * - onSolicitarEnlace(): abre el reenvío del enlace.
 * - onIrAlInicio(): vuelve al inicio de sesión.
 * - restablecimiento: reutiliza el formulario para elegir una nueva contraseña, sin activar la cuenta.
 */
function ActivacionCuenta({ token, onActivada, onSolicitarEnlace, onIrAlInicio, restablecimiento = false }) {
    const titulo = restablecimiento ? "Restablecer contraseña" : "Activar cuenta";
    const [consulta, setConsulta] = useState({ cargando: true, datos: null, error: "" });
    const [intentoConsulta, setIntentoConsulta] = useState(0);
    const [form, setForm] = useState({ nombre: "", contrasenia: "", confirmacion: "" });
    const [errores, setErrores] = useState({});
    const [errorServidor, setErrorServidor] = useState("");
    const [enviando, setEnviando] = useState(false);

    useEffect(() => {
        let vigente = true;
        setConsulta({ cargando: true, datos: null, error: "" });
        const consultar = restablecimiento ? consultarRestablecimiento : consultarActivacion;
        consultar(token)
            .then((datos) => vigente && setConsulta({ cargando: false, datos, error: "" }))
            .catch((fallo) => vigente && setConsulta({ cargando: false, datos: null, error: fallo.message }));
        return () => {
            vigente = false;
        };
    }, [token, intentoConsulta, restablecimiento]);

    const cambiar = (campo) => (valor) => setForm((anterior) => ({ ...anterior, [campo]: valor }));

    const activar = async (evento) => {
        evento.preventDefault();
        const nuevosErrores = {
            nombre: restablecimiento ? null : validarNombre(form.nombre),
            contrasenia: validarContrasenia(form.contrasenia),
            confirmacion: form.contrasenia === form.confirmacion ? null : MENSAJE_CONFIRMACION,
        };
        setErrores(nuevosErrores);
        if (Object.values(nuevosErrores).some(Boolean)) {
            return;
        }

        setErrorServidor("");
        setEnviando(true);
        try {
            const guardar = restablecimiento ? restablecerContrasenia : activarCuenta;
            const respuesta = await guardar({
                token,
                nombre: form.nombre.trim(),
                contrasenia: form.contrasenia,
            });
            onActivada(respuesta.correoInstitucional);
        } catch (fallo) {
            setErrorServidor(fallo.message);
            setEnviando(false);
        }
    };

    if (consulta.cargando) {
        return (
            <PantallaAcceso titulo={titulo}>
                <p className="text-center text-sm text-ink-600" role="status">
                    <i className="fa-solid fa-spinner fa-spin mr-2" aria-hidden="true"></i>
                    Comprobando el enlace…
                </p>
            </PantallaAcceso>
        );
    }

    if (consulta.error) {
        return (
            <PantallaAcceso titulo={titulo}>
                <div className="flex flex-col gap-4">
                    <Aviso tono="peligro">{consulta.error}</Aviso>
                    <Boton onClick={() => setIntentoConsulta((intento) => intento + 1)} className="min-h-11 w-full">
                        Reintentar
                    </Boton>
                    <Boton variante="texto" onClick={onIrAlInicio} className="w-full">
                        Volver al inicio de sesión
                    </Boton>
                </div>
            </PantallaAcceso>
        );
    }

    const { estado, carnet, correoInstitucional } = consulta.datos;

    if (estado !== ESTADOS_ENLACE.VALIDO) {
        const usado = estado === ESTADOS_ENLACE.USADO;
        return (
            <PantallaAcceso titulo={titulo}>
                <div className="flex flex-col gap-4">
                    <Aviso tono={usado ? "info" : "advertencia"}>
                        {restablecimiento
                            ? (usado ? "Este enlace ya se utilizó. Inicia sesión con tu nueva contraseña."
                                : "El enlace no es válido o venció. Solicita otro a administración.")
                            : (MENSAJES_ENLACE[estado] ?? MENSAJES_ENLACE.INVALIDO)}
                    </Aviso>
                    {!usado && !restablecimiento && (
                        <Boton onClick={onSolicitarEnlace} className="min-h-11 w-full">
                            Solicitar un enlace nuevo
                        </Boton>
                    )}
                    <Boton variante={usado ? "primario" : "texto"} onClick={onIrAlInicio} className="min-h-11 w-full">
                        Ir al inicio de sesión
                    </Boton>
                </div>
            </PantallaAcceso>
        );
    }

    return (
        <PantallaAcceso titulo={titulo}>
            {restablecimiento ? <p className="mb-5 text-sm text-ink-600">Elige tu nueva contraseña. El cambio no modifica el estado de tu cuenta.</p> : <dl className="mb-5 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 rounded-lg bg-surface-muted p-3 text-sm">
                <dt className="font-semibold text-ink-900">Carnet</dt>
                <dd className="text-ink-600">{carnet}</dd>
                <dt className="font-semibold text-ink-900">Correo</dt>
                <dd className="break-all text-ink-600">{correoInstitucional}</dd>
            </dl>}

            <form onSubmit={activar} className="flex flex-col gap-4" noValidate>
                {!restablecimiento && <Campo
                    label="Nombre completo"
                    name="nombre"
                    value={form.nombre}
                    onValorChange={cambiar("nombre")}
                    error={errores.nombre || undefined}
                    autoComplete="name"
                    disabled={enviando}
                />}
                <Campo
                    label="Contraseña"
                    name="contrasenia"
                    type="password"
                    value={form.contrasenia}
                    onValorChange={cambiar("contrasenia")}
                    ayuda={AYUDA_CONTRASENIA}
                    error={errores.contrasenia || undefined}
                    autoComplete="new-password"
                    disabled={enviando}
                />
                <Campo
                    label="Repite la contraseña"
                    name="confirmacion"
                    type="password"
                    value={form.confirmacion}
                    onValorChange={cambiar("confirmacion")}
                    error={errores.confirmacion || undefined}
                    autoComplete="new-password"
                    disabled={enviando}
                />

                {errorServidor && <Aviso tono="peligro">{errorServidor}</Aviso>}

                <Boton type="submit" className="mt-2 min-h-11 w-full" disabled={enviando}>
                    {enviando ? "Guardando…" : titulo}
                </Boton>
            </form>

            <Boton variante="texto" onClick={onIrAlInicio} className="mt-5 w-full" disabled={enviando}>
                Volver al inicio de sesión
            </Boton>
        </PantallaAcceso>
    );
}

export default ActivacionCuenta;
