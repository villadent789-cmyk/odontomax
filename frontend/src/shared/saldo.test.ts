import { describe, expect, it } from 'vitest'
import { esSaldoAFavor, formatoSaldo } from './saldo'

describe('formatoSaldo', () => {
  it('presenta los saldos negativos como crédito a favor', () => {
    expect(formatoSaldo(-1500)).toBe('Saldo a favor S/ 1,500.00')
    expect(esSaldoAFavor(-1500)).toBe(true)
  })

  it('mantiene la presentación de saldos pendientes y cero', () => {
    expect(formatoSaldo(80)).toBe('S/ 80.00')
    expect(formatoSaldo(0)).toBe('S/ 0.00')
    expect(esSaldoAFavor(0)).toBe(false)
  })
})
