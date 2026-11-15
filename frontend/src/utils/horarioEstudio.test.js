import { test } from 'node:test'
import assert from 'node:assert/strict'

import {
  aSolicitud,
  bloquesDesdeSugerencia,
  diasDeLaSemana,
  firmaBloques,
  formatearDuracion,
  hayCambiosSinGuardar,
  minutosTotales,
  nuevoBloque,
  SITUACION,
  situacionPropuesta,
  sumarDias,
  textosAdvertencias,
  validarBloque,
} from './horarioEstudio.js'

const LUNES = '2026-11-16'

test('los días de la semana van de lunes a domingo sin depender de la zona horaria', () => {
  const dias = diasDeLaSemana(LUNES)
  assert.equal(dias.length, 7)
  assert.deepEqual(dias[0], { fecha: '2026-11-16', nombre: 'Lunes' })
  assert.deepEqual(dias[6], { fecha: '2026-11-22', nombre: 'Domingo' })
  assert.equal(sumarDias('2026-12-28', 7), '2027-01-04')
})

test('la sugerencia se convierte en bloques editables con su materiaInscritaId', () => {
  const bloques = bloquesDesdeSugerencia({
    dias: [
      { fecha: LUNES, bloques: [{ inicio: '18:00:00', fin: '20:00:00', materiaInscritaId: 12, materia: 'Física' }] },
      { fecha: '2026-11-17', bloques: [] },
    ],
  })
  assert.equal(bloques.length, 1)
  assert.equal(bloques[0].inicio, '18:00')
  assert.equal(bloques[0].materiaInscritaId, 12)
  assert.equal(minutosTotales(bloques), 120)
})

test('valida horas, duración mínima, semana y superposición', () => {
  const existente = nuevoBloque({ fecha: LUNES, inicio: '18:00', fin: '20:00', materiaInscritaId: 1, materia: 'Física' })
  const base = { clave: 'nuevo', fecha: LUNES, materiaInscritaId: 2, materia: 'Álgebra' }

  assert.match(validarBloque({ ...base, inicio: '15:00', fin: '14:00' }, [existente], LUNES), /posterior/)
  assert.match(validarBloque({ ...base, inicio: '14:00', fin: '14:15' }, [existente], LUNES), /al menos 30/)
  assert.match(validarBloque({ ...base, fecha: '2026-11-23', inicio: '14:00', fin: '15:00' }, [], LUNES), /fuera/)
  assert.match(validarBloque({ ...base, inicio: '19:00', fin: '21:00' }, [existente], LUNES), /Se superpone con Física/)
  assert.equal(validarBloque({ ...base, inicio: '20:00', fin: '21:00' }, [existente], LUNES), null)
  // Al editar un bloque no choca consigo mismo
  assert.equal(validarBloque({ ...existente, inicio: '18:30' }, [existente], LUNES), null)
})

test('la solicitud lleva solo los campos del contrato y en orden', () => {
  const bloques = [
    nuevoBloque({ fecha: '2026-11-17', inicio: '08:00', fin: '09:00', materiaInscritaId: 1, materia: 'A' }),
    nuevoBloque({ fecha: LUNES, inicio: '18:00', fin: '19:00', materiaInscritaId: '2', materia: 'B' }),
  ]
  assert.deepEqual(aSolicitud(LUNES, bloques), {
    desde: LUNES,
    bloques: [
      { fecha: LUNES, inicio: '18:00', fin: '19:00', materiaInscritaId: 2 },
      { fecha: '2026-11-17', inicio: '08:00', fin: '09:00', materiaInscritaId: 1 },
    ],
  })
})

test('distingue sin ciclo, sin materias, sin actividades y con bloques', () => {
  const semanaVacia = Array.from({ length: 7 }, (_, i) => ({ fecha: sumarDias(LUNES, i), bloques: [] }))
  const resumen = (materias) => ({ minutosSugeridosSemanales: 0, minutosNoProgramadosSemanales: 0, materiasConsideradas: materias })

  assert.equal(situacionPropuesta({ dias: [], sinAsignar: [], resumen: resumen(0) }), SITUACION.SIN_CICLO)
  assert.equal(situacionPropuesta({ dias: semanaVacia, sinAsignar: [], resumen: resumen(0) }), SITUACION.SIN_MATERIAS)
  assert.equal(situacionPropuesta({ dias: semanaVacia, sinAsignar: [], resumen: resumen(2) }), SITUACION.SIN_ACTIVIDADES)
  // Sin espacio libre: no hay bloques pero sí tiempo sin asignar, así que hay actividades
  assert.equal(situacionPropuesta({ dias: semanaVacia, sinAsignar: [{ materiaInscritaId: 1 }], resumen: resumen(1) }),
    SITUACION.CON_BLOQUES)
  const conBloque = semanaVacia.map((dia, i) => (i === 0 ? { ...dia, bloques: [{ inicio: '18:00', fin: '19:00' }] } : dia))
  assert.equal(situacionPropuesta({ dias: conBloque, sinAsignar: [], resumen: resumen(1) }), SITUACION.CON_BLOQUES)
})

test('detecta cambios sin guardar respecto a la propuesta de la que partió la edición', () => {
  const original = [
    nuevoBloque({ fecha: LUNES, inicio: '18:00', fin: '20:00', materiaInscritaId: 1, materia: 'Física' }),
    nuevoBloque({ fecha: '2026-11-17', inicio: '08:00', fin: '09:00', materiaInscritaId: 2, materia: 'Álgebra' }),
  ]
  const firma = firmaBloques(original)

  // Sin borrador (vista confirmada o descartada) no hay nada que perder
  assert.equal(hayCambiosSinGuardar(null, firma), false)
  // Mismo contenido aunque cambien las claves locales o el orden
  const reconstruido = [...original].reverse().map((b) => nuevoBloque(b))
  assert.equal(hayCambiosSinGuardar(reconstruido, firma), false)
  // Editar horas, cambiar la materia, quitar o agregar bloques sí son cambios
  assert.equal(hayCambiosSinGuardar([{ ...original[0], fin: '19:30' }, original[1]], firma), true)
  assert.equal(hayCambiosSinGuardar([{ ...original[0], materiaInscritaId: 3 }, original[1]], firma), true)
  assert.equal(hayCambiosSinGuardar([original[0]], firma), true)
  assert.equal(hayCambiosSinGuardar([...original,
    nuevoBloque({ fecha: '2026-11-18', inicio: '18:00', fin: '19:00', materiaInscritaId: 1, materia: 'Física' })], firma), true)
  // Deshacer el cambio a mano vuelve a dejarlo sin cambios
  assert.equal(hayCambiosSinGuardar([{ ...original[0], fin: '20:00' }, original[1]], firma), false)
  // materiaInscritaId como texto (valor de un <select>) equivale al número
  assert.equal(hayCambiosSinGuardar([{ ...original[0], materiaInscritaId: '1' }, original[1]], firma), false)
})

test('textos de duración y advertencias', () => {
  assert.equal(formatearDuracion(45), '45 min')
  assert.equal(formatearDuracion(120), '2 h')
  assert.equal(formatearDuracion(90), '1 h 30 min')
  assert.equal(textosAdvertencias(['DIA_JORNADA_INVALIDO', 'DIA_CLASE_INVALIDO']).length, 1)
  assert.match(textosAdvertencias(['ACTIVIDAD_VENCIDA'])[0], /vencida/)
  assert.equal(textosAdvertencias(['PLANTILLA_PROVISIONAL', 'FUERA_DE_PLANTILLA']).length, 1)
  assert.match(textosAdvertencias(['FUERA_DE_PLANTILLA'])[0], /fuera de las franjas/)
})
