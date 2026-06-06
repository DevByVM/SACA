import { useState } from "react";
import { registrar } from "../services/authService";

function Registro() {

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

    const handleSubmit = async (e) => {
        e.preventDefault();

        try {
            const respuesta = await registrar(form);

            if (respuesta !== "Usuario registrado correctamente") {
                alert(respuesta || "No se pudo registrar el usuario");
                return;
            }

            alert(respuesta);

            setForm({
                nombre: "",
                carnet: "",
                correoInstitucional: "",
                contrasenia: ""
            });

        } catch (error) {
            alert("Error al registrar usuario");
            console.error(error);
        }
    };

    return (
        <div style={{ padding: "20px" }}>
            <h1>Registro de Estudiante</h1>

            <form onSubmit={handleSubmit}>
                <div>
                    <input
                        type="text"
                        name="nombre"
                        placeholder="Nombre"
                        value={form.nombre}
                        onChange={handleChange}
                    />
                </div>

                <div>
                    <input
                        type="text"
                        name="carnet"
                        placeholder="Carnet"
                        value={form.carnet}
                        onChange={handleChange}
                    />
                </div>

                <div>
                    <input
                        type="email"
                        name="correoInstitucional"
                        placeholder="Correo UES"
                        value={form.correoInstitucional}
                        onChange={handleChange}
                    />
                </div>

                <div>
                    <input
                        type="password"
                        name="contrasenia"
                        placeholder="Contraseña"
                        value={form.contrasenia}
                        onChange={handleChange}
                    />
                </div>

                <br />

                <button type="submit">
                    Registrarse
                </button>
            </form>
        </div>
    );
}

export default Registro;
