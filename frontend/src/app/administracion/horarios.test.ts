import { describe, expect, it } from 'vitest'
import { cuerpoDesdeFilas, filasDesdeHorarios, validarHorarios, type FilaHorario } from './horarios'

const fila = (cambios: Partial<FilaHorario> = {}): FilaHorario => ({
  clave: 'k',
  sedeId: 'sede-chilca',
  diaSemana: 1,
  horaInicio: '09:00',
  horaFin: '19:00',
  ...cambios,
})

describe('horarios · validarHorarios', () => {
  it('acepta turnos válidos, incluso en la misma sede con días distintos o el mismo día en sedes distintas', () => {
    expect(validarHorarios([])).toBeNull()
    expect(validarHorarios([fila(), fila({ diaSemana: 2 }), fila({ sedeId: 'sede-centro' })])).toBeNull()
  })

  it('rechaza turnos sin sede u hora, con fin anterior o igual al inicio, y duplicados de sede+día', () => {
    expect(validarHorarios([fila({ sedeId: '' })])).toMatch(/sede/)
    expect(validarHorarios([fila({ horaInicio: '' })])).toMatch(/hora de inicio y de fin/)
    expect(validarHorarios([fila({ horaFin: '09:00' })])).toMatch(/termina antes de empezar/)
    expect(validarHorarios([fila({ horaFin: '08:00', diaSemana: 3 })])).toMatch(/miércoles/)
    expect(validarHorarios([fila(), fila()])).toMatch(/Solo se permite un turno por sede y día/)
  })
})

describe('horarios · conversiones', () => {
  it('convierte HH:mm:ss a HH:mm, ordena por sede y día, y vuelve al cuerpo del contrato', () => {
    const filas = filasDesdeHorarios([
      { sedeId: 'sede-chilca', diaSemana: 3, horaInicio: '09:00:00', horaFin: '13:00:00' },
      { sedeId: 'sede-centro', diaSemana: 5, horaInicio: '10:30:00', horaFin: '18:00:00' },
      { sedeId: 'sede-chilca', diaSemana: 1, horaInicio: '09:00:00', horaFin: '19:00:00' },
    ])
    expect(cuerpoDesdeFilas(filas)).toEqual([
      { sedeId: 'sede-centro', diaSemana: 5, horaInicio: '10:30', horaFin: '18:00' },
      { sedeId: 'sede-chilca', diaSemana: 1, horaInicio: '09:00', horaFin: '19:00' },
      { sedeId: 'sede-chilca', diaSemana: 3, horaInicio: '09:00', horaFin: '13:00' },
    ])
    expect(new Set(filas.map((f) => f.clave)).size).toBe(3)
  })

  it('tolera una lista ausente', () => {
    expect(filasDesdeHorarios(undefined)).toEqual([])
  })
})
