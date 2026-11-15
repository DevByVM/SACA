// Reglas del borrador del horario de estudio. El backend repite estas validaciones y además
// comprueba los choques con clases, jornada laboral y disponibilidad.

export const DURACION_MINIMA_MINUTOS = 30

export const NOMBRES_DIA = {
  LUNES: 'Lunes',
  MARTES: 'Martes',
  MIERCOLES: 'Miércoles',
  JUEVES: 'Jueves',
  VIERNES: 'Viernes',
  SABADO: 'Sábado',
  DOMINGO: 'Domingo',
}

const DIAS_EN_ORDEN = Object.keys(NOMBRES_DIA)

const TEXTOS_ADVERTENCIA = {
  PLANTILLA_PROVISIONAL:
    'No hay una plantilla de planificación activa: la propuesta usa tu disponibilidad registrada o, si no la tienes, de 06:00 a 22:00.',
  FUERA_DE_PLANTILLA:
    'Algunos bloques quedaron fuera de las franjas de estudio de la plantilla por falta de espacio.',
  ACTIVIDAD_VENCIDA: 'Hay actividades pendientes con la entrega vencida; no se incluyeron.',
  ACTIVIDAD_SIN_FECHA_LIMITE: 'Hay actividades sin fecha de entrega; no se incluyeron.',
  ACTIVIDAD_ANTES_DEL_PERIODO: 'Hay actividades que vencen antes de la semana propuesta; no se incluyeron.',
  TIEMPO_ESTIMADO_INVALIDO: 'Hay actividades sin un tiempo estimado válido; no se incluyeron.',
}

const TEXTO_DATOS_IGNORADOS =
  'Algunos registros de disponibilidad, jornada o plantilla tienen datos no reconocidos y se ignoraron.'

// Una sola línea por advertencia; los códigos de datos inválidos se agrupan en un mismo texto.
// Sin plantilla todos los bloques quedan "fuera de plantilla": ese aviso se omite por redundante.
export function textosAdvertencias(codigos = []) {
  const sinPlantilla = codigos.includes('PLANTILLA_PROVISIONAL')
  const textos = codigos
    .filter((codigo) => !(sinPlantilla && codigo === 'FUERA_DE_PLANTILLA'))
    .map((codigo) => TEXTOS_ADVERTENCIA[codigo] ?? TEXTO_DATOS_IGNORADOS)
  return [...new Set(textos)]
}

// Situación de la propuesta según la respuesta de la sugerencia. materiasConsideradas cuenta todas
// las materias de los ciclos activos, también las que no tienen actividades pendientes.
export const SITUACION = {
  SIN_CICLO: 'sin-ciclo',
  SIN_MATERIAS: 'sin-materias',
  SIN_ACTIVIDADES: 'sin-actividades',
  CON_BLOQUES: 'con-bloques',
}

export function situacionPropuesta(sugerencia) {
  if (!sugerencia || (sugerencia.dias ?? []).length === 0) return SITUACION.SIN_CICLO
  if ((sugerencia.resumen?.materiasConsideradas ?? 0) === 0) return SITUACION.SIN_MATERIAS
  const hayBloques = sugerencia.dias.some((dia) => dia.bloques.length > 0)
  const haySinAsignar = (sugerencia.sinAsignar ?? []).length > 0
  return hayBloques || haySinAsignar ? SITUACION.CON_BLOQUES : SITUACION.SIN_ACTIVIDADES
}

export function aMinutos(hora) {
  const [horas, minutos] = String(hora).split(':').map(Number)
  return horas * 60 + minutos
}

// "18:00:00" -> "18:00"
export function horaCorta(hora) {
  return String(hora ?? '').slice(0, 5)
}

export function formatearDuracion(minutos) {
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  if (horas === 0) return `${resto} min`
  return resto === 0 ? `${horas} h` : `${horas} h ${resto} min`
}

// Fecha ISO sin pasar por Date para no depender de la zona horaria del navegador
export function sumarDias(fechaIso, dias) {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  const fecha = new Date(Date.UTC(anio, mes - 1, dia + dias))
  return fecha.toISOString().slice(0, 10)
}

export function formatearFecha(fechaIso) {
  const [, mes, dia] = fechaIso.split('-')
  return `${dia}/${mes}`
}

// Los 7 días de la semana que empieza en desde, con su nombre
export function diasDeLaSemana(desde) {
  return DIAS_EN_ORDEN.map((clave, indice) => ({
    fecha: sumarDias(desde, indice),
    nombre: NOMBRES_DIA[clave],
  }))
}

let siguienteClave = 0
function claveLocal() {
  siguienteClave += 1
  return `local-${siguienteClave}`
}

export function nuevoBloque({ fecha, inicio, fin, materiaInscritaId, materia }) {
  return {
    clave: claveLocal(),
    fecha,
    inicio: horaCorta(inicio),
    fin: horaCorta(fin),
    materiaInscritaId: Number(materiaInscritaId),
    materia,
  }
}

export function bloquesDesdeSugerencia(sugerencia) {
  return (sugerencia?.dias ?? []).flatMap((dia) =>
    dia.bloques.map((bloque) => nuevoBloque({ ...bloque, fecha: dia.fecha }))
  )
}

export function bloquesDesdeHorario(horario) {
  return (horario?.bloques ?? []).map((bloque) => nuevoBloque(bloque))
}

export function ordenarBloques(bloques) {
  return [...bloques].sort((a, b) =>
    a.fecha === b.fecha ? aMinutos(a.inicio) - aMinutos(b.inicio) : a.fecha.localeCompare(b.fecha)
  )
}

export function minutosDe(bloque) {
  return aMinutos(bloque.fin) - aMinutos(bloque.inicio)
}

export function minutosTotales(bloques) {
  return bloques.reduce((total, bloque) => total + minutosDe(bloque), 0)
}

// Error del bloque en el formulario, o null. otros = el resto de bloques del borrador
export function validarBloque(bloque, otros, desde) {
  if (!bloque.materiaInscritaId) return 'Selecciona la materia.'
  if (!bloque.inicio || !bloque.fin) return 'Indica la hora de inicio y de fin.'
  const hasta = sumarDias(desde, 6)
  if (bloque.fecha < desde || bloque.fecha > hasta) return 'El día está fuera de la semana del horario.'
  const inicio = aMinutos(bloque.inicio)
  const fin = aMinutos(bloque.fin)
  if (fin <= inicio) return 'La hora de fin debe ser posterior a la de inicio.'
  if (fin - inicio < DURACION_MINIMA_MINUTOS) {
    return `Cada bloque debe durar al menos ${DURACION_MINIMA_MINUTOS} minutos.`
  }
  const choque = otros.find(
    (otro) =>
      otro.clave !== bloque.clave &&
      otro.fecha === bloque.fecha &&
      aMinutos(otro.inicio) < fin &&
      inicio < aMinutos(otro.fin)
  )
  if (choque) {
    return `Se superpone con ${choque.materia} de ${choque.inicio} a ${choque.fin}.`
  }
  return null
}

// Huella del contenido de un borrador: solo lo que se guarda (día, horas y materia), sin las claves
// locales ni el orden en que se agregaron los bloques
export function firmaBloques(bloques = []) {
  return JSON.stringify(
    ordenarBloques(bloques).map(({ fecha, inicio, fin, materiaInscritaId }) => [fecha, inicio, fin, Number(materiaInscritaId)])
  )
}

// true si el borrador ya no coincide con la propuesta de la que partió la edición
export function hayCambiosSinGuardar(borrador, firmaOriginal) {
  if (!borrador) return false
  return firmaBloques(borrador) !== firmaOriginal
}

export function aSolicitud(desde, bloques) {
  return {
    desde,
    bloques: ordenarBloques(bloques).map(({ fecha, inicio, fin, materiaInscritaId }) => ({
      fecha,
      inicio,
      fin,
      materiaInscritaId,
    })),
  }
}
