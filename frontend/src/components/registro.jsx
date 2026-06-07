import { useState } from "react";
// Importa la función registrar desde el servicio de autenticación
import { registrar } from "../services/authService";

function Registro() {

    // Estado que almacena los datos del formulario
    const [form, setForm] = useState({
        nombre: "",
        carnet: "",
        correoInstitucional: "",
        contrasenia: ""
    });
     /**
     * Actualiza automáticamente el campo que el usuario modifica.
     * El atributo "name" del input debe coincidir con la propiedad
     * dentro del objeto form.
     */
    const handleChange = (e) => {
        setForm({
            ...form, // Mantiene los valores existentes
            [e.target.name]: e.target.value // Actualiza solo el campo modificado
        });
    };

    /**
     * Se ejecuta cuando el usuario envía el formulario.
     * Envía los datos al backend para registrar al estudiante.
     */

    const handleSubmit = async (e) => {
        e.preventDefault();

        try {
            // Envía los datos del formulario al servicio
            const respuesta = await registrar(form);
            alert(respuesta);

            // Limpia el formulario después del registro
            setForm({
                nombre: "",
                carnet: "",
                correoInstitucional: "",
                contrasenia: ""
            });

        } catch (error) {
            // Manejo de errores en caso de fallo
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
                 {/* Botón para enviar el formulario */}
                <button type="submit">
                    Registrarse
                </button>
            </form>
        </div>
    );
}
// Exporta el componente para poder usarlo en otras partes del proyecto
export default Registro;