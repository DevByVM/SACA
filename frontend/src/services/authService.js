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
const API_URL = "http://localhost:8080/api/auth";

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

    // Envía una petición POST al endpoint de registro
    const response = await axios.post(
        `${API_URL}/registro`,
        datos
    );

    // Devuelve únicamente los datos de la respuesta
    return response.data;
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

    // Envía una petición POST al endpoint de login
    const response = await axios.post(
        `${API_URL}/login`,
        datos
    );

    // Devuelve únicamente los datos de la respuesta
    return response.data;
};