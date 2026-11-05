// Importa el hook useState para manejar estados dentro del componente
import { useState } from "react";

// Importa la función para iniciar sesión
import { login } from "../services/authService";

// Guarda el token de sesión recibido al iniciar sesión
import { guardarSesion } from "../api/sesion";

// Importa las notificaciones emergentes (toast)
import { toast } from "react-toastify";

// Componentes compartidos del sistema de diseño
import Aviso from "./ui/Aviso";
import Boton from "./ui/Boton";
import Campo from "./ui/Campo";
import PantallaAcceso from "./cuenta/PantallaAcceso";
import ReenvioActivacion from "./cuenta/ReenvioActivacion";
import RegistroCuenta from "./cuenta/RegistroCuenta";

const TITULOS = {
    login: "Iniciar Sesión",
    registro: "Crear cuenta",
    reenvio: "Reenviar enlace de activación",
};

/**
 * Componente Login
 *
 * Además del inicio de sesión, permite solicitar una cuenta con el carnet y pedir otro enlace de activación.
 *
 * Props:
 * - onLoginSuccess: función que se ejecuta cuando el usuario
 *   inicia sesión correctamente.
 * - correoInicial: correo con el que se abre el formulario (tras activar la cuenta).
 * - avisoInicial: mensaje de éxito que se muestra sobre el formulario (tras activar la cuenta).
 * - modoInicial: "login", "registro" o "reenvio".
 */

function Login({ onLoginSuccess, correoInicial = "", avisoInicial = "", modoInicial = "login" }) {

    // Pantalla visible: inicio de sesión, solicitud de cuenta o reenvío del enlace
    const [modo, setModo] = useState(TITULOS[modoInicial] ? modoInicial : "login");

    // Carnet con el que se abre el reenvío cuando se llega desde la solicitud de cuenta
    const [identificadorReenvio, setIdentificadorReenvio] = useState("");

    const [aviso, setAviso] = useState(avisoInicial);

    /**
     * Estado que almacena los campos del inicio de sesión.
     */
    const [form, setForm] = useState({
        correoInstitucional: correoInicial,
        contrasenia: ""
    });

    /**
     * Actualiza automáticamente cualquier campo del formulario.
     * El atributo "name" del input debe coincidir con la propiedad
     * correspondiente dentro del objeto form.
     */
    const handleChange = (e) => {
        setForm({
            ...form,
            [e.target.name]: e.target.value
        });
    };

    const irA = (nuevoModo, identificador = "") => {
        setAviso("");
        setIdentificadorReenvio(identificador);
        setModo(nuevoModo);
    };

    /**
     * Maneja el inicio de sesión.
     * Envía correo y contraseña al backend para validación.
     */
    const handleLogin = async (e) => {
        e.preventDefault();

        try {

            // Envía credenciales al backend
            const respuesta = await login({
                correoInstitucional: form.correoInstitucional,
                contrasenia: form.contrasenia
            });

            // Sin token no hay sesión válida, aunque el backend diga que autenticó
            if (respuesta.autenticado && !respuesta.token) {
                toast.error("No se pudo iniciar sesión: el servidor no envió el token de sesión");
                return;
            }

            // Si las credenciales son correctas
            if (respuesta.autenticado) {

                /**
                 * Objeto con la información del estudiante autenticado.
                 */
                const estudianteAutenticado = {
                    id: respuesta.estudianteId,
                    nombre: respuesta.nombre,
                    carnet: respuesta.carnet,
                    correoInstitucional: respuesta.correoInstitucional
                };

                // Guarda el token y los datos del estudiante en sessionStorage
                guardarSesion({
                    token: respuesta.token,
                    estudiante: estudianteAutenticado
                });

                toast.success("Inicio de sesión exitoso");

                // Notifica al componente padre que el login fue exitoso
                onLoginSuccess(estudianteAutenticado);

            } else {

                // Siempre el mismo aviso; no se distingue si la cuenta existe, está bloqueada o pendiente
                toast.error("Credenciales incorrectas", { toastId: "credenciales-incorrectas" });
            }

        } catch (error) {

            // Manejo de errores inesperados
            toast.error(
                error.message || "Error al iniciar sesión"
            );
        }
    };

    return (
        <PantallaAcceso titulo={TITULOS[modo]}>

            {modo === "registro" && (
                <RegistroCuenta
                    onReenviar={(carnet) => irA("reenvio", carnet)}
                    onVolver={() => irA("login")}
                />
            )}

            {modo === "reenvio" && (
                <ReenvioActivacion
                    identificadorInicial={identificadorReenvio}
                    onVolver={() => irA("login")}
                />
            )}

            {modo === "login" && (
                <>
                    {aviso && <Aviso tono="exito" className="mb-4">{aviso}</Aviso>}

                    {/* Formulario principal */}
                    {/* Sin required: los campos vacíos los rechaza el backend y su mensaje se muestra como aviso */}
                    <form onSubmit={handleLogin} className="flex flex-col gap-4">

                        {/* Correo institucional */}
                        <Campo
                            label="Correo institucional"
                            name="correoInstitucional"
                            required={false}
                            value={form.correoInstitucional}
                            autoComplete="off"
                            onChange={handleChange}
                        />

                        {/* Contraseña */}
                        <Campo
                            label="Contraseña"
                            name="contrasenia"
                            type="password"
                            required={false}
                            value={form.contrasenia}
                            autoComplete="current-password"
                            onChange={handleChange}
                        />

                        {/* Botón principal */}
                        <Boton type="submit" className="mt-2 min-h-11 w-full">
                            Ingresar
                        </Boton>

                    </form>

                    {/* Ayuda fija, igual para todos; no depende de la respuesta ni revela si una cuenta existe */}
                    <div className="mt-5 space-y-2 rounded-lg bg-surface-muted p-3 text-xs text-ink-600" role="note">
                        <p>
                            Si tus datos no son aceptados, revisa tu correo institucional y tu contraseña. Tras tres
                            intentos fallidos seguidos, el acceso se bloquea durante cinco minutos.
                        </p>
                        <p>
                            Si creaste tu cuenta hace poco, primero actívala con el enlace que te enviamos a tu
                            correo institucional.
                        </p>
                    </div>

                    <Boton variante="texto" onClick={() => irA("reenvio")} className="mt-4 w-full">
                        ¿No te llegó el enlace de activación? Solicitar otro
                    </Boton>

                    <Boton variante="texto" onClick={() => irA("registro")} className="mt-2 w-full">
                        ¿No tienes cuenta? Regístrate
                    </Boton>
                </>
            )}

        </PantallaAcceso>
    );
}

// Exporta el componente para usarlo en otras partes de la aplicación
export default Login;
