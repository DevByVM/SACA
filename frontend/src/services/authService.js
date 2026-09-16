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

function normalizarError(error, fallback) {
    const mensajeBackend = error.response?.data?.message;
    return new Error(mensajeBackend || error.message || fallback);
}

/**
 * Registra un nuevo estudiante.
 * 
 * Parámetro:
 * datos -> Objeto con la información del estudiante.
 * 
 * Ejemplo:
 * {
 *   nombre: "Juan Pérez",
 *   carnet: "AB12345",
 *   correoInstitucional: "jperez001@ues.edu.sv",
 *   contrasenia: "123456"
 * }
 * 
 * Retorna:
 * La respuesta enviada por el backend.
 */
export const registrar = async (datos) => {
    try {
        const response = await axios.post(
            `${API_URL}/registro`,
            datos
        );

        return response.data;
    } catch (error) {
        throw normalizarError(error, "No se pudo completar el registro");
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
