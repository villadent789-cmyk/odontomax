import { describe, expect, it } from 'vitest'
import { mesAnteriorISO, rangoDelMes } from './mesCierre'

describe('cierre de mes', () => {
  it('propone el mes anterior, también al cruzar de año', () => {
    expect(mesAnteriorISO(new Date(2026, 9, 29))).toBe('2026-09')
    expect(mesAnteriorISO(new Date(2027, 0, 5))).toBe('2026-12')
  })

  it('calcula el primer y último día del mes, con febrero bisiesto', () => {
    expect(rangoDelMes('2026-10')).toEqual({ desde: '2026-10-01', hasta: '2026-10-31', nombre: 'octubre de 2026' })
    expect(rangoDelMes('2028-02').hasta).toBe('2028-02-29')
    expect(rangoDelMes('2026-02').hasta).toBe('2026-02-28')
  })
})
