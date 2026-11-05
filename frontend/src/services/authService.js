// Importa Axios para realizar peticiones HTTP al backend
import axios from "axios";

/**
 * URL base de la API de autenticación.
 * 
 * Si el backend cambia de dirección o puerto,
 * solo es necesario modificar esta constante.
 * 
 * Ejemplos:
 * Desarrollo:
 * http://localhost:8080/api/auth
 * 
 * Producción:
 * https://midominio.com/api/auth
 */
const API_URL = `${import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api"}/auth`;

// Sin respuesta del servidor (caído o sin conexión) se muestra un mensaje propio en lugar del de Axios
function normalizarError(error, fallback) {
    const mensajeBackend = error.response?.data?.message;
    if (mensajeBackend) {
        return new Error(mensajeBackend);
    }
    if (!error.response) {
        return new Error("No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.");
    }
    return new Error(fallback);
}

/**
 * Solicita una cuenta con el carnet. El correo institucional se deriva de él en el backend.
 *
 * La respuesta es la misma exista o no la cuenta, para no revelarlo:
 * { mensaje, correoInstitucional }
 */
export const registrar = async (carnet) => {
    try {
        const response = await axios.post(`${API_URL}/registro`, { carnet });
        return response.data;
    } catch (error) {
        throw normalizarError(error, "No se pudo completar el registro");
    }
};

/**
 * Comprueba el enlace de activación sin consumirlo.
 *
 * Retorna { estado, carnet, correoInstitucional }; carnet y correo solo llegan si estado es "VALIDO".
 * El token viaja en el cuerpo, nunca en la URL.
 */
export const consultarActivacion = async (token) => {
    try {
        const response = await axios.post(`${API_URL}/activacion/consultar`, { token });
        return response.data;
    } catch (error) {
        throw normalizarError(error, "No se pudo comprobar el enlace de activación");
    }
};

/**
 * Activa la cuenta con el nombre y la contraseña elegidos. No inicia sesión.
 *
 * Retorna { mensaje, correoInstitucional } para volver al inicio de sesión con el correo escrito.
 */
export const activarCuenta = async ({ token, nombre, contrasenia }) => {
    try {
        const response = await axios.post(`${API_URL}/activacion`, { token, nombre, contrasenia });
        return response.data;
    } catch (error) {
        throw normalizarError(error, "No se pudo activar la cuenta");
    }
};

/**
 * Pide otro enlace de activación con el carnet o el correo institucional.
 *
 * La respuesta es la misma haya o no una cuenta pendiente: { mensaje, correoInstitucional }
 */
export const reenviarActivacion = async (identificador) => {
    try {
        const response = await axios.post(`${API_URL}/reenviar-activacion`, { identificador });
        return response.data;
    } catch (error) {
        throw normalizarError(error, "No se pudo solicitar el enlace");
    }
};

/**
 * Inicia sesión de un estudiante.
 * 
 * Parámetro:
 * datos -> Credenciales del usuario.
 * 
 * Ejemplo:
 * {
 *   correoInstitucional: "jperez001@ues.edu.sv",
 *   contrasenia: "123456"
 * }
 * 
 * Retorna:
 * La respuesta enviada por el backend.
 * 
 * Ejemplo:
 * {
 *   autenticado: true,
 *   estudianteId: 1,
 *   nombre: "Juan Pérez",
 *   carnet: "AB12345"
 * }
 */
export const login = async (datos) => {
    try {
        const response = await axios.post(
            `${API_URL}/login`,
            datos
        );

        return response.data;
    } catch (error) {
        throw normalizarError(error, "No se pudo iniciar sesión");
    }
};
