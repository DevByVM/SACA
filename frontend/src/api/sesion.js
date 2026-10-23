// Sesión del estudiante autenticado: token JWT, su vigencia y los datos básicos del estudiante.
// Se guarda en sessionStorage para que no sobreviva al cierre del navegador.

const CLAVE_SESION = "saca.sesion";
const EVENTO_SESION_EXPIRADA = "saca:sesion-expirada";

export const MENSAJE_SESION_EXPIRADA = "Tu sesión expiró. Inicia sesión de nuevo.";

// Versiones anteriores guardaban estos datos en localStorage sin token
const CLAVES_ANTERIORES = ["estudiante", "correoInstitucional"];

function descartarDatosAnteriores() {
  try {
    CLAVES_ANTERIORES.forEach((clave) => localStorage.removeItem(clave));
  } catch {
    // Sin acceso al almacenamiento no hay nada que limpiar
  }
}

// La vigencia (exp - iat) sale del propio token y se cuenta desde el reloj local,
// para que un reloj desajustado no acorte ni anule la sesión
function calcularExpiracionLocal(token) {
  try {
    const carga = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const { iat, exp } = JSON.parse(atob(carga));
    if (Number.isFinite(iat) && Number.isFinite(exp) && exp > iat) {
      return Date.now() + (exp - iat) * 1000;
    }
  } catch {
    // Se informa abajo con el mismo mensaje
  }
  throw new Error("El token de sesión recibido no es válido");
}

export function guardarSesion({ token, estudiante }) {
  const expiraLocal = calcularExpiracionLocal(token);
  descartarDatosAnteriores();
  sessionStorage.setItem(CLAVE_SESION, JSON.stringify({ token, expiraLocal, estudiante }));
}

export function borrarSesion() {
  descartarDatosAnteriores();
  try {
    sessionStorage.removeItem(CLAVE_SESION);
  } catch {
    // Sin acceso al almacenamiento tampoco hay sesión guardada
  }
}

// Devuelve null si no hay sesión, si está dañada o si ya venció
export function obtenerSesion() {
  let sesion;
  try {
    sesion = JSON.parse(sessionStorage.getItem(CLAVE_SESION));
  } catch {
    sesion = null;
  }

  if (!sesion?.token || !sesion?.estudiante || !(milisegundosHastaExpirar(sesion) > 0)) {
    if (sesion) borrarSesion();
    return null;
  }
  return sesion;
}

export function milisegundosHastaExpirar(sesion) {
  return sesion.expiraLocal - Date.now();
}

export function cabecerasAutorizacion() {
  const sesion = obtenerSesion();
  return sesion ? { Authorization: `Bearer ${sesion.token}` } : {};
}

// Token de la última sesión que App cerró; otras peticiones que lo llevaban pueden
// responder 401 después, cuando App ya no escucha el evento
let tokenCerradoPorApp = null;

// Se lee sin validar la vigencia: al vencer el temporizador la sesión ya no es válida
function tokenGuardado() {
  try {
    return JSON.parse(sessionStorage.getItem(CLAVE_SESION))?.token ?? null;
  } catch {
    return null;
  }
}

// Se llama ante un 401 o al vencer el token. Devuelve true si alguien atendió el evento
// (App lo cancela al volver al login y mostrar el único aviso de sesión expirada).
export function notificarSesionExpirada() {
  const token = tokenGuardado();
  borrarSesion();
  const atendido = !window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA, { cancelable: true }));
  if (atendido && token) tokenCerradoPorApp = token;
  return atendido;
}

export function sesionYaCerradaPorApp(token) {
  return Boolean(token) && token === tokenCerradoPorApp;
}

export function alExpirarSesion(callback) {
  window.addEventListener(EVENTO_SESION_EXPIRADA, callback);
  return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, callback);
}
