// SDACAS-3: formato oficial del carnet, el mismo que valida el backend (EstudianteService).
// Se aceptan minúsculas: el valor se pasa a mayúsculas antes de validar y de enviarlo.
export const FORMATO_CARNET = /^[A-Z]{2}[0-9]{5}$/;

export const MENSAJE_FORMATO_CARNET = "El carnet debe tener dos letras y cinco números, por ejemplo AP23001";

export const AYUDA_CARNET = "Dos letras y cinco números, por ejemplo AP23001.";

export const normalizarCarnet = (carnet) => String(carnet ?? "").trim().toUpperCase();

export const carnetValido = (carnet) => FORMATO_CARNET.test(normalizarCarnet(carnet));
