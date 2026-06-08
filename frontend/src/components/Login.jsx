// Importa el hook useState para manejar estados dentro del componente
import { useState } from "react";

// Importa las funciones para iniciar sesión y registrar usuarios
import { login, registrar } from "../services/authService";

// Importa las notificaciones emergentes (toast)
import { toast } from "react-toastify";

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

            // Si las credenciales son correctas
            if (respuesta.autenticado) {

                toast.success("Inicio de sesión exitoso");

                /**
                 * Objeto con la información del estudiante autenticado.
                 */
                const estudianteAutenticado = {
                    id: respuesta.estudianteId,
                    nombre: respuesta.nombre,
                    carnet: respuesta.carnet,
                    correoInstitucional: respuesta.correoInstitucional
                };

                // Guarda el correo en el navegador
                localStorage.setItem(
                    "correoInstitucional",
                    estudianteAutenticado.correoInstitucional
                );

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

            // Mensaje de éxito
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
        <div className="min-h-screen flex items-center justify-center bg-[#ffffff] p-6">

            {/* Contenedor principal */}
            <div className="w-full max-w-md p-8 rounded-2xl bg-[#ffffff] border border-[#430000]/20 shadow-lg">

                {/* Encabezado */}
                <div className="flex flex-col items-center mb-6">

                    {/* Ícono decorativo */}
                    <div className="w-20 h-20 rounded-full bg-[#960000]/10 border border-[#960000]/20 flex items-center justify-center mb-4">
                        <i className="fa-solid fa-graduation-cap text-[#960000] text-4xl"></i>
                    </div>

                    {/* Nombre del sistema */}
                    <h1 className="text-3xl font-bold text-[#430000] text-center">
                        SACA UES
                    </h1>

                    {/* Descripción */}
                    <p className="text-sm text-[#430000]/60 mt-1">
                        Sistema de Análisis de Carga Académica
                    </p>

                    {/* Línea decorativa */}
                    <div className="w-16 h-1 bg-[#960000] rounded-full mt-4"></div>
                </div>

                {/* Título dinámico */}
                <h2 className="text-[#960000] text-center font-semibold mb-6">
                    {modo === "login" ? "Iniciar Sesión" : "Registro"}
                </h2>

                {/* Formulario principal */}
                <form
                    onSubmit={modo === "login" ? handleLogin : handleRegistro}
                    className="flex flex-col gap-4"
                >

                    {/* Campos exclusivos para registro */}
                    {modo === "registro" && (
                        <>
                            <input
                                name="nombre"
                                value={form.nombre}
                                placeholder="Nombre"
                                className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                                onChange={handleChange}
                            />

                            <input
                                name="carnet"
                                value={form.carnet}
                                placeholder="Carnet"
                                className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                                onChange={handleChange}
                            />
                        </>
                    )}

                    {/* Correo institucional */}
                    <input
                        name="correoInstitucional"
                        value={form.correoInstitucional}
                        autoComplete="off"
                        placeholder="Correo institucional"
                        className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                        onChange={handleChange}
                    />


                    {/* Contraseña */}
                    <input
                        name="contrasenia"
                        type="password"
                        value={form.contrasenia}
                        autoComplete="new-password"
                        placeholder="Contraseña"
                        className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                        onChange={handleChange}
                    />
                    {/* Botón principal */}
                    <button
                        type="submit"
                        className="bg-[#960000] hover:bg-[#430000] text-[#eeeeee] font-bold p-3 rounded-lg transition-all"
                    >
                        {modo === "login"
                            ? "Ingresar"
                            : "Registrarse"}
                    </button>


                </form>

                {/* Botón para cambiar entre login y registro */}
                <button
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
                    className="mt-5 text-sm text-[#430000]/70 hover:text-[#960000] w-full transition-colors"
                >
                    {modo === "login"
                        ? "¿No tienes cuenta? Regístrate"
                        : "¿Ya tienes cuenta? Inicia sesión"}
                </button>

            </div>
        </div>
    );
}

// Exporta el componente para usarlo en otras partes de la aplicación
export default Login;