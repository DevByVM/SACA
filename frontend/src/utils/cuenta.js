// Reglas de registro, activación y reenvío de cuentas. Repiten las del backend para avisar antes de enviar,
// con los mismos mensajes; el backend sigue siendo quien decide.

import { carnetValido, normalizarCarnet } from "./carnet.js";

export const DOMINIO_INSTITUCIONAL = "@ues.edu.sv";

// 32 bytes aleatorios en Base64 URL-safe sin relleno: siempre 43 caracteres de ese alfabeto
const FORMATO_TOKEN = /^[A-Za-z0-9_-]{43}$/;

const FORMATO_NOMBRE = /^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+(?: [a-zA-ZáéíóúÁÉÍÓÚñÑ]+)*$/;
const FORMATO_CONTRASENIA = /^(?=.*[A-Z])(?=.*\d).{8,}$/;
const MAXIMO_CARACTERES_NOMBRE = 120;
// BCrypt solo tiene en cuenta los primeros 72 bytes (UTF-8): el backend rechaza contraseñas más largas
const MAXIMO_BYTES_CONTRASENIA = 72;

export const MENSAJE_NOMBRE_OBLIGATORIO = "Nombre obligatorio";
export const MENSAJE_NOMBRE_LARGO = "El nombre no debe superar 120 caracteres";
export const MENSAJE_NOMBRE_INVALIDO = "El nombre solo puede contener letras y espacios";
export const MENSAJE_CONTRASENIA_INVALIDA = "La contraseña debe tener al menos 8 caracteres, una mayúscula y un número";
export const MENSAJE_CONTRASENIA_LARGA =
  "La contraseña es demasiado larga: admite hasta 72 bytes (las letras con tilde y la ñ cuentan doble)";
export const MENSAJE_CONFIRMACION = "Las contraseñas no coinciden";
export const MENSAJE_IDENTIFICADOR_INVALIDO =
  "Escribe tu carnet (por ejemplo AP23001) o tu correo institucional (por ejemplo ap23001@ues.edu.sv).";

export const AYUDA_CONTRASENIA =
  "Al menos 8 caracteres, con una mayúscula y un número. Hasta 72 bytes: las letras con tilde y la ñ cuentan doble.";

// Estados que devuelve la consulta del enlace y qué se le dice al estudiante en cada uno
export const ESTADOS_ENLACE = {
  VALIDO: "VALIDO",
  EXPIRADO: "EXPIRADO",
  USADO: "USADO",
  INVALIDO: "INVALIDO",
};

export const MENSAJES_ENLACE = {
  EXPIRADO: "El enlace de activación venció. Solicita uno nuevo con tu carnet o tu correo institucional.",
  USADO: "Este enlace ya se utilizó. Inicia sesión con tu correo institucional y tu contraseña.",
  INVALIDO:
    "El enlace de activación no es válido. Puede que esté incompleto o que lo haya sustituido uno más reciente: usa el último correo que recibiste o solicita uno nuevo.",
};

export const correoDeCarnet = (carnet) => `${normalizarCarnet(carnet).toLowerCase()}${DOMINIO_INSTITUCIONAL}`;

// Token del enlace de activación (…/#token=XXXX), o null si no hay o no tiene el formato esperado
export function extraerTokenDelFragmento(fragmento) {
  const texto = String(fragmento ?? "").replace(/^#/, "");
  if (!texto) {
    return null;
  }
  const token = new URLSearchParams(texto).get("token");
  return token && FORMATO_TOKEN.test(token) ? token : null;
}

// Quita el fragmento de la barra de direcciones sin recargar ni añadir una entrada al historial, para que el
// token no siga visible ni pueda volver a abrirse con el botón Atrás
export function quitarFragmento(ubicacion, historial) {
  if (ubicacion?.hash && historial?.replaceState) {
    historial.replaceState(null, "", `${ubicacion.pathname}${ubicacion.search}`);
  }
}

export const bytesUtf8 = (texto) => new TextEncoder().encode(String(texto ?? "")).length;

// Mensaje de error o null si el nombre es válido
export function validarNombre(nombre) {
  const limpio = String(nombre ?? "").trim();
  if (!limpio) {
    return MENSAJE_NOMBRE_OBLIGATORIO;
  }
  if (limpio.length > MAXIMO_CARACTERES_NOMBRE) {
    return MENSAJE_NOMBRE_LARGO;
  }
  return FORMATO_NOMBRE.test(limpio) ? null : MENSAJE_NOMBRE_INVALIDO;
}

export function validarContrasenia(contrasenia) {
  const texto = contrasenia ?? "";
  if (!FORMATO_CONTRASENIA.test(texto)) {
    return MENSAJE_CONTRASENIA_INVALIDA;
  }
  return bytesUtf8(texto) > MAXIMO_BYTES_CONTRASENIA ? MENSAJE_CONTRASENIA_LARGA : null;
}

// Mensaje de error o null si el reenvío puede pedirse con ese dato: el carnet o el correo institucional
// derivado de él (carnet en minúsculas + @ues.edu.sv), nunca otro correo
export function validarIdentificador(identificador) {
  const texto = String(identificador ?? "").trim();
  if (!texto) {
    return MENSAJE_IDENTIFICADOR_INVALIDO;
  }
  if (!texto.includes("@")) {
    return carnetValido(texto) ? null : MENSAJE_IDENTIFICADOR_INVALIDO;
  }
  const correo = texto.toLowerCase();
  if (!correo.endsWith(DOMINIO_INSTITUCIONAL)) {
    return MENSAJE_IDENTIFICADOR_INVALIDO;
  }
  const local = correo.slice(0, -DOMINIO_INSTITUCIONAL.length);
  return local === local.trim() && carnetValido(local) ? null : MENSAJE_IDENTIFICADOR_INVALIDO;
}
