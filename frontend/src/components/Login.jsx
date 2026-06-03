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

            if (respuesta === "LOGIN_OK") {
                onLoginSuccess();
            } else {
                alert(respuesta);
            }

        } catch (error) {
            alert("Error al iniciar sesión");
        }
    };

    // REGISTRO
    const handleRegistro = async (e) => {
        e.preventDefault();

        try {
            const respuesta = await registrar(form);
            alert(respuesta);

            // después del registro vuelve a login
            setModo("login");

        } catch (error) {
            alert("Error al registrar");
        }
    };

return (
    <div className="min-h-screen flex items-center justify-center bg-[#111827] text-slate-300">

        <div className="w-full max-w-md p-8 rounded-2xl bg-[#1e293b]/40 border border-slate-800 shadow-xl backdrop-blur-sm">

            <h1 className="text-3xl font-bold text-white text-center mb-6">
                SACA UES
            </h1>

            <h2 className="text-cyan-400 text-center mb-6">
                {modo === "login" ? "Iniciar Sesión" : "Registro"}
            </h2>

            <form onSubmit={
                modo === "login"
                    ? handleLogin
                    : handleRegistro
            } className="flex flex-col gap-4">

                {modo === "registro" && (
                    <>
                        <input
                            name="nombre"
                            placeholder="Nombre"
                            className="p-3 rounded-lg bg-[#0f172a] border border-slate-700"
                            onChange={handleChange}
                        />

                        <input
                            name="carnet"
                            placeholder="Carnet"
                            className="p-3 rounded-lg bg-[#0f172a] border border-slate-700"
                            onChange={handleChange}
                        />
                    </>
                )}

                <input
                    name="correoInstitucional"
                    placeholder="Correo institucional"
                    className="p-3 rounded-lg bg-[#0f172a] border border-slate-700"
                    onChange={handleChange}
                />

                <input
                    name="contrasenia"
                    type="password"
                    placeholder="Contraseña"
                    className="p-3 rounded-lg bg-[#0f172a] border border-slate-700"
                    onChange={handleChange}
                />

                <button
                    type="submit"
                    className="bg-cyan-500 hover:bg-cyan-400 text-black font-bold p-3 rounded-lg transition"
                >
                    {modo === "login"
                        ? "Ingresar"
                        : "Registrarse"}
                </button>
            </form>

            <button
                onClick={() =>
                    setModo(modo === "login"
                        ? "registro"
                        : "login"
                    )
                }
                className="mt-4 text-sm text-slate-400 hover:text-cyan-400 w-full"
            >
                {modo === "login"
                    ? "Ir a Registro"
                    : "Ir a Login"}
            </button>

        </div>
    </div>
);
}

export default Login;