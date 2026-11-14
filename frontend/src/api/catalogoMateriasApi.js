import { apiRequest } from './client'

export function listarCatalogoMaterias() {
  return apiRequest('/catalogo-materias')
}
