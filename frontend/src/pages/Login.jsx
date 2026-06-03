import { useState } from "react";
import { login } from "../services/authService";

function Login({ onLoginSuccess }) {

    const [form, setForm] = useState({
        correoInstitucional: "",
        contrasenia: ""
    });

    const handleChange = (e) => {
        setForm({
            ...form,
            [e.target.name]: e.target.value
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        try {

            const respuesta = await login(form);

            if (respuesta === "LOGIN_OK") {
                onLoginSuccess();
            } else {
                alert(respuesta);
            }

        } catch (error) {
            alert("Error al iniciar sesión");
        }
    };

    return (
        <div style={{ padding: "20px" }}>
            <h1>Iniciar Sesión</h1>

            <form onSubmit={handleSubmit}>

                <input
                    type="email"
                    name="correoInstitucional"
                    placeholder="Correo institucional"
                    value={form.correoInstitucional}
                    onChange={handleChange}
                />

                <br />
                <br />

                <input
                    type="password"
                    name="contrasenia"
                    placeholder="Contraseña"
                    value={form.contrasenia}
                    onChange={handleChange}
                />

                <br />
                <br />

                <button type="submit">
                    Ingresar
                </button>

            </form>
        </div>
    );
}

export default Login;