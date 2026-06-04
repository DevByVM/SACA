import { useState } from "react";
import { login, registrar } from "../services/authService";

function Login({ onLoginSuccess }) {

    const [modo, setModo] = useState("login");

    const [form, setForm] = useState({
        nombre: "",
        carnet: "",
        correoInstitucional: "",
        contrasenia: ""
    });

    const handleChange = (e) => {
        setForm({
            ...form,
            [e.target.name]: e.target.value
        });
    };

    // LOGIN
    const handleLogin = async (e) => {
        e.preventDefault();

        try {
            const respuesta = await login({
                correoInstitucional: form.correoInstitucional,
                contrasenia: form.contrasenia
            });

            if (respuesta.autenticado) {
                onLoginSuccess({
                    id: respuesta.estudianteId,
                    nombre: respuesta.nombre,
                    carnet: respuesta.carnet,
                    correoInstitucional: respuesta.correoInstitucional
                });
            } else {
                alert(respuesta.mensaje || "Credenciales incorrectas");
            }

        } catch (error) {
            alert(error.message || "Error al iniciar sesión");
        }
    };

    // REGISTRO
    const handleRegistro = async (e) => {
        e.preventDefault();

        try {
            const respuesta = await registrar(form);

            alert(respuesta);

            setModo("login");

        } catch (error) {
            alert(error.message || "Error al registrar");
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-[#ffffff] p-6">

            <div className="w-full max-w-md p-8 rounded-2xl bg-[#ffffff] border border-[#430000]/20 shadow-lg">

               {/* LOGO */}
<div className="flex flex-col items-center mb-6">

    <div className="w-20 h-20 rounded-full bg-[#960000]/10 border border-[#960000]/20 flex items-center justify-center mb-4">
        <i className="fa-solid fa-graduation-cap text-[#960000] text-4xl"></i>
    </div>

    <h1 className="text-3xl font-bold text-[#430000] text-center">
        SACA UES
    </h1>

    <p className="text-sm text-[#430000]/60 mt-1">
        Sistema de Análisis de Carga Académica
    </p>

    <div className="w-16 h-1 bg-[#960000] rounded-full mt-4"></div>

</div>

                {/* SUBTÍTULO */}
                <h2 className="text-[#960000] text-center font-semibold mb-6">
                    {modo === "login"
                        ? "Iniciar Sesión"
                        : "Registro"}
                </h2>

                <form
                    onSubmit={
                        modo === "login"
                            ? handleLogin
                            : handleRegistro
                    }
                    className="flex flex-col gap-4"
                >

                    {modo === "registro" && (
                        <>
                            <input
                                name="nombre"
                                placeholder="Nombre"
                                className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                                onChange={handleChange}
                            />

                            <input
                                name="carnet"
                                placeholder="Carnet"
                                className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                                onChange={handleChange}
                            />
                        </>
                    )}

                    <input
                        name="correoInstitucional"
                        placeholder="Correo institucional"
                        className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                        onChange={handleChange}
                    />

                    <input
                        name="contrasenia"
                        type="password"
                        placeholder="Contraseña"
                        className="p-3 rounded-lg bg-[#eeeeee] border border-[#430000]/20 text-[#430000] outline-none focus:border-[#960000]"
                        onChange={handleChange}
                    />

                    <button
                        type="submit"
                        className="bg-[#960000] hover:bg-[#430000] text-[#eeeeee] font-bold p-3 rounded-lg transition-all"
                    >
                        {modo === "login"
                            ? "Ingresar"
                            : "Registrarse"}
                    </button>

                </form>

                <button
                    onClick={() =>
                        setModo(
                            modo === "login"
                                ? "registro"
                                : "login"
                        )
                    }
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

export default Login;
