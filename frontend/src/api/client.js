import axios from 'axios'
import { cabecerasAutorizacion, notificarSesionExpirada, sesionYaCerradaPorApp } from './sesion'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

// El token solo se envía al backend propio, nunca a servicios externos
function esUrlDelBackend(url) {
  return String(url).startsWith(API_BASE_URL)
}

// Token que llevaba la petición, tomado de su cabecera Authorization
function tokenDeLaPeticion(autorizacion) {
  return typeof autorizacion === 'string' && autorizacion.startsWith('Bearer ') ? autorizacion.slice(7) : null
}

// Ante un 401 la sesión se cierra. Si App lo atiende (vuelve al login y muestra el único aviso),
// la petición queda sin resolver para que la pantalla que la hizo, que se desmonta, no muestre
// además su error. Si nadie lo atiende, el 401 sigue su curso como cualquier otro error.
// Los 401 que llegan después con el token de esa misma sesión ya cerrada tampoco se muestran.
function sesionCerradaPorApp(status, url, token) {
  if (status !== 401 || !esUrlDelBackend(url)) return false
  return sesionYaCerradaPorApp(token) || notificarSesionExpirada()
}

const peticionSinResolver = () => new Promise(() => {})

export async function fetchConSesion(url, options = {}) {
  const headers = esUrlDelBackend(url)
    ? { ...(options.headers ?? {}), ...cabecerasAutorizacion() }
    : options.headers

  const response = await fetch(url, { ...options, headers })

  if (sesionCerradaPorApp(response.status, url, tokenDeLaPeticion(headers?.Authorization))) {
    return peticionSinResolver()
  }
  return response
}

// Cliente Axios para el backend, con el mismo tratamiento del token y del 401
export const clienteHttp = axios.create()

clienteHttp.interceptors.request.use((config) => {
  if (esUrlDelBackend(config.url)) {
    Object.entries(cabecerasAutorizacion()).forEach(([nombre, valor]) => config.headers.set(nombre, valor))
  }
  return config
})

clienteHttp.interceptors.response.use(undefined, (error) => {
  if (sesionCerradaPorApp(error.response?.status, error.config?.url, tokenDeLaPeticion(error.config?.headers?.Authorization))) {
    return peticionSinResolver()
  }
  return Promise.reject(error)
})

export async function apiRequest(path, options = {}) {
  const response = await fetchConSesion(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    ...options,
  })

  if (response.status === 204) {
    return null
  }

  const contentType = response.headers.get('content-type') ?? ''
  const data = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (!response.ok) {
    const message =
      typeof data === 'object' && data !== null && 'message' in data
        ? data.message
        : 'No se pudo completar la solicitud'
    throw new Error(message)
  }

  return data
}
