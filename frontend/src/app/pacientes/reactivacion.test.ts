import { describe, expect, it } from 'vitest'
import { estadoReactivacion, puedeReactivar } from './reactivacion'

const AHORA = new Date('2026-10-14T15:00:00Z').getTime()
const haceDias = (dias: number) => new Date(AHORA - dias * 86_400_000).toISOString().slice(0, 10)

describe('Semáforo del paciente', () => {
  it('sin ninguna atención es "nuevo", nunca "inactivo"', () => {
    expect(estadoReactivacion(null, AHORA)).toBe('nuevo')
    expect(estadoReactivacion(undefined, AHORA)).toBe('nuevo')
    expect(estadoReactivacion('', AHORA)).toBe('nuevo')
  })

  it('activo hasta 89 días, en seguimiento hasta 180 e inactivo después', () => {
    expect(estadoReactivacion(haceDias(10), AHORA)).toBe('activo')
    expect(estadoReactivacion(haceDias(120), AHORA)).toBe('seguimiento')
    expect(estadoReactivacion(haceDias(180), AHORA)).toBe('seguimiento')
    expect(estadoReactivacion(haceDias(200), AHORA)).toBe('inactivo')
  })
})

describe('Botón "Reactivar"', () => {
  const completados = new Set(['p-con-plan'])

  it('aparece solo con tratamiento concluido y más de 180 días sin atención', () => {
    expect(puedeReactivar({ id: 'p-con-plan', ultimaCitaAtendida: haceDias(200) }, completados, AHORA)).toBe(true)
  })

  it('no aparece a un paciente nuevo, aunque tenga un plan concluido registrado', () => {
    expect(puedeReactivar({ id: 'p-con-plan', ultimaCitaAtendida: null }, completados, AHORA)).toBe(false)
  })

  it('no aparece sin tratamiento concluido, aunque lleve mucho sin venir', () => {
    expect(puedeReactivar({ id: 'p-sin-plan', ultimaCitaAtendida: haceDias(400) }, completados, AHORA)).toBe(false)
  })

  it('no aparece con 180 días o menos', () => {
    expect(puedeReactivar({ id: 'p-con-plan', ultimaCitaAtendida: haceDias(180) }, completados, AHORA)).toBe(false)
    expect(puedeReactivar({ id: 'p-con-plan', ultimaCitaAtendida: haceDias(30) }, completados, AHORA)).toBe(false)
  })
})
