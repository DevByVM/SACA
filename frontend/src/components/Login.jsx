// Importa el hook useState para manejar estados dentro del componente
import { useState } from "react";

// Importa las funciones para iniciar sesión y registrar usuarios
import { login, registrar } from "../services/authService";

// Guarda el token de sesión recibido al iniciar sesión
import { guardarSesion } from "../api/sesion";

// Importa las notificaciones emergentes (toast)
import { toast } from "react-toastify";

// Componentes compartidos del sistema de diseño
import Boton from "./ui/Boton";
import Campo from "./ui/Campo";
import Tarjeta from "./ui/Tarjeta";

/**
 * Componente Login
 * 
 * Props:
 * - onLoginSuccess: función que se ejecuta cuando el usuario
 *   inicia sesión correctamente.
 */

function Login({ onLoginSuccess }) {

    // Determina si se muestra el formulario de login o registro
    const [modo, setModo] = useState("login");

    /**
     * Estado que almacena todos los campos del formulario.
     * Se utiliza tanto para login como para registro.
     */
    const [form, setForm] = useState({
        nombre: "",
        carnet: "",
        correoInstitucional: "",
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

                // Si las credenciales son incorrectas
                toast.error(
                    respuesta.mensaje || "Credenciales incorrectas"
                );
            }

        } catch (error) {

            // Manejo de errores inesperados
            toast.error(
                error.message || "Error al iniciar sesión"
            );
        }
    };

    /**
     * Maneja el registro de nuevos estudiantes.
     */
    const handleRegistro = async (e) => {
        e.preventDefault();

        try {

            // Envía todos los datos del formulario al backend
            const respuesta = await registrar(form);

            if (respuesta !== "Usuario registrado correctamente") {
                throw new Error(respuesta || "No se pudo completar el registro");
            }

            toast.success(respuesta);

            // Regresa automáticamente al formulario de login
            setModo("login");

            // Limpia todos los campos
            setForm({
                nombre: "",
                carnet: "",
                correoInstitucional: "",
                contrasenia: ""
            });

        } catch (error) {

            // Muestra mensaje de error
            toast.error(
                error.message || "Error al registrar"
            );
        }
    };

    return (
        <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-8 sm:p-6">

            {/* Contenedor principal */}
            <Tarjeta className="w-full max-w-md p-6 shadow-md sm:p-8">

                {/* Encabezado */}
                <div className="mb-6 flex flex-col items-center text-center">

                    {/* Ícono decorativo */}
                    <div className="mb-4 flex h-20 w-20 items-center justify-center rounded-full border border-brand-200 bg-brand-50 text-brand-700">
                        <i className="fa-solid fa-graduation-cap text-4xl" aria-hidden="true"></i>
                    </div>

                    {/* Nombre del sistema */}
                    <h1 className="text-3xl font-bold text-ink-900">
                        SACA UES
                    </h1>

                    {/* Descripción */}
                    <p className="mt-1 text-sm text-ink-600">
                        Sistema de Análisis de Carga Académica
                    </p>

                    {/* Línea decorativa */}
                    <div className="mt-4 h-1 w-16 rounded-full bg-brand-700" aria-hidden="true"></div>
                </div>

                {/* Título dinámico */}
                <h2 className="mb-6 text-center text-lg font-semibold text-brand-700">
                    {modo === "login" ? "Iniciar Sesión" : "Registro"}
                </h2>

                {/* Formulario principal */}
                {/* Sin required: los campos vacíos los rechaza el backend y su mensaje se muestra como aviso */}
                <form
                    onSubmit={modo === "login" ? handleLogin : handleRegistro}
                    className="flex flex-col gap-4"
                >

                    {/* Campos exclusivos para registro */}
                    {modo === "registro" && (
                        <>
                            <Campo
                                label="Nombre"
                                name="nombre"
                                required={false}
                                value={form.nombre}
                                onChange={handleChange}
                            />

                            <Campo
                                label="Carnet"
                                name="carnet"
                                required={false}
                                value={form.carnet}
                                onChange={handleChange}
                            />
                        </>
                    )}

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
                        autoComplete="new-password"
                        onChange={handleChange}
                    />

                    {/* Botón principal */}
                    <Boton type="submit" className="mt-2 min-h-11 w-full">
                        {modo === "login"
                            ? "Ingresar"
                            : "Registrarse"}
                    </Boton>

                </form>

                {/* Botón para cambiar entre login y registro */}
                <Boton
                    variante="texto"
                    onClick={() => {

                        setForm({
                            nombre: "",
                            carnet: "",
                            correoInstitucional: "",
                            contrasenia: ""
                        });

                        setModo(
                            modo === "login"
                                ? "registro"
                                : "login"
                        );
                    }}
                    className="mt-5 w-full"
                >
                    {modo === "login"
                        ? "¿No tienes cuenta? Regístrate"
                        : "¿Ya tienes cuenta? Inicia sesión"}
                </Boton>

            </Tarjeta>
        </main>
    );
}

// Exporta el componente para usarlo en otras partes de la aplicación
export default Login;
