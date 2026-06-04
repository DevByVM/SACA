import axios from "axios";

const API_URL = "http://localhost:8080/api/auth";

export const registrar = async (datos) => {
    const response = await axios.post(
        `${API_URL}/registro`,
        datos
    );

    return response.data;
};

export const login = async (datos) => {
    const response = await axios.post(
        `${API_URL}/login`,
        datos
    );

    return response.data;
};