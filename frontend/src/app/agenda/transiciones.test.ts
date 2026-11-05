import { describe, expect, it } from 'vitest'
import { accionesPara, puedeCancelar, puedeReprogramar, TRANSICIONES } from './transiciones'
import { diaSemanaIso, diasDeLaSemana, horaFin, inicioSemana, sumarDias } from './fechas'

describe('transiciones de cita', () => {
  it('no ofrece acciones sobre citas cerradas (como el backend: no se cancela ni reprograma ATENDIDA/CANCELADA)', () => {
    for (const estado of ['ATENDIDA', 'CANCELADA'] as const) {
      expect(TRANSICIONES[estado]).toEqual([])
      expect(puedeCancelar(estado)).toBe(false)
      expect(puedeReprogramar(estado)).toBe(false)
    }
    expect(puedeReprogramar('NO_ASISTIO')).toBe(true)
    expect(puedeCancelar('PENDIENTE_WEB')).toBe(true)
  })

  it('no ofrece cancelar una cita no asistida o expirada, ni reprogramar una expirada (el backend responde 400)', () => {
    expect(puedeCancelar('NO_ASISTIO')).toBe(false)
    expect(puedeCancelar('EXPIRADA')).toBe(false)
    expect(puedeReprogramar('EXPIRADA')).toBe(false)
    expect(puedeCancelar('CONFIRMADA')).toBe(true)
    expect(puedeCancelar('EN_SALA')).toBe(true)
  })

  it('filtra por rol con la tabla prudente', () => {
    expect(accionesPara('PROGRAMADA', 'RECEPCION')).toEqual({
      estados: ['CONFIRMADA', 'EN_SALA', 'NO_ASISTIO'],
      cancelar: true,
      reprogramar: true,
      iniciarAtencion: false,
    })
    expect(accionesPara('PROGRAMADA', 'ODONTOLOGO')).toEqual({
      estados: [],
      cancelar: false,
      reprogramar: false,
      iniciarAtencion: false,
    })
    expect(accionesPara('EN_SALA', 'ODONTOLOGO').estados).toEqual([])
    expect(accionesPara('EN_ATENCION', 'ODONTOLOGO').estados).toEqual(['ATENDIDA'])
    expect(accionesPara('EN_SALA', 'RECEPCION').iniciarAtencion).toBe(true)
    expect(accionesPara('EN_SALA', 'ODONTOLOGO').iniciarAtencion).toBe(false)
    expect(accionesPara('desconocido', 'ADMIN_GENERAL').estados).toEqual([])
    expect(accionesPara('confirmada', 'ADMIN_SEDE').estados).toEqual(['EN_SALA', 'NO_ASISTIO'])
  })
})

describe('fechas de la agenda', () => {
  it('calcula días y semanas sobre el día calendario', () => {
    expect(sumarDias('2026-10-31', 1)).toBe('2026-11-01')
    expect(diaSemanaIso('2026-10-08')).toBe(4)
    expect(diaSemanaIso('2026-10-11')).toBe(7)
    expect(inicioSemana('2026-10-11')).toBe('2026-10-05')
    expect(diasDeLaSemana('2026-10-08')).toHaveLength(7)
    expect(horaFin('09:30:00', 45)).toBe('10:15')
    expect(horaFin('09:30', 0)).toBe('10:00')
  })
})
