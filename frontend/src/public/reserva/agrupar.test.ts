import { describe, expect, it } from 'vitest'
import { agruparPorEspecialidad, type ServicioPublico } from './api-reserva'

const servicio = (id: string, especialidad?: string): ServicioPublico => ({ id, nombre: id, especialidad })

describe('Servicios por especialidad', () => {
  it('agrupa respetando el orden en que aparece cada especialidad y junta las repetidas', () => {
    const grupos = agruparPorEspecialidad([
      servicio('a', 'Restauraciones'),
      servicio('b', 'Exodoncias'),
      servicio('c', 'Restauraciones'),
      servicio('d'),
    ])
    expect(grupos.map((g) => g.especialidad)).toEqual(['Restauraciones', 'Exodoncias', 'Otros servicios'])
    expect(grupos[0].servicios.map((s) => s.id)).toEqual(['a', 'c'])
  })
})
