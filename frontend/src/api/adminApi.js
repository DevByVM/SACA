import { apiRequest } from './client'

export const listarUsuariosAdmin = () => apiRequest('/admin/usuarios')

export const actualizarUsuarioAdmin = (id, datos) => apiRequest(`/admin/usuarios/${id}`, {
  method: 'PUT',
  body: JSON.stringify(datos),
})

export const solicitarRestablecimientoAdmin = (id) => apiRequest(`/admin/usuarios/${id}/restablecimiento`, {
  method: 'POST',
})

export const crearUsuarioAdmin = (carnet) => apiRequest('/admin/usuarios', {
  method: 'POST',
  body: JSON.stringify({ carnet }),
})

export const cambiarEstadoUsuarioAdmin = (id, habilitada) => apiRequest(`/admin/usuarios/${id}/estado`, {
  method: 'PATCH',
  body: JSON.stringify({ habilitada }),
})

export const listarCatalogoAdmin = () => apiRequest('/admin/catalogo-materias')

export const guardarMateriaCatalogo = (materia, id) => apiRequest(
  id ? `/admin/catalogo-materias/${id}` : '/admin/catalogo-materias',
  {
    method: id ? 'PUT' : 'POST',
    body: JSON.stringify(materia),
  },
)

export const cambiarEstadoMateriaCatalogo = (id, activa) => apiRequest(`/admin/catalogo-materias/${id}/estado`, {
  method: 'PATCH',
  body: JSON.stringify({ activa }),
})

export const listarTiposActividadAdmin = () => apiRequest('/admin/tipos-actividad')

export const guardarTipoActividadAdmin = (tipo, id) => apiRequest(
  id ? `/admin/tipos-actividad/${id}` : '/admin/tipos-actividad',
  {
    method: id ? 'PUT' : 'POST',
    body: JSON.stringify(tipo),
  },
)

export const eliminarTipoActividadAdmin = (id) => apiRequest(`/admin/tipos-actividad/${id}`, {
  method: 'DELETE',
})
