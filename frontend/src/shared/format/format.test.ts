import { describe, expect, it } from 'vitest'
import {
  formatFecha,
  formatFechaHora,
  formatFechaLarga,
  formatHora,
  formatSoles,
  hoyEnLima,
  saludoSegunHora,
} from '.'

describe('formatSoles', () => {
  it.each([
    [1234.5, 'S/ 1,234.50'],
    [0, 'S/ 0.00'],
    [3420, 'S/ 3,420.00'],
    [1234567.891, 'S/ 1,234,567.89'],
    [0.005, 'S/ 0.01'],
    [-50, '-S/ 50.00'],
    ['1234.5', 'S/ 1,234.50'],
  ])('%s → %s', (entrada, esperado) => {
    expect(formatSoles(entrada)).toBe(esperado)
  })

  it('muestra una raya para valores vacíos o inválidos', () => {
    expect(formatSoles(null)).toBe('—')
    expect(formatSoles(undefined)).toBe('—')
    expect(formatSoles('')).toBe('—')
    expect(formatSoles('abc')).toBe('—')
    expect(formatSoles(Number.NaN)).toBe('—')
  })
})

describe('formatFecha (dd/mm/aaaa, America/Lima)', () => {
  it('formatea un LocalDate de la API sin desplazarlo de día', () => {
    expect(formatFecha('2024-10-24')).toBe('24/10/2024')
    expect(formatFecha('2025-01-01')).toBe('01/01/2025')
  })

  it('convierte un instante UTC a la fecha de Lima', () => {
    // 03:00 UTC del 25 = 22:00 del 24 en Lima
    expect(formatFecha('2024-10-25T03:00:00Z')).toBe('24/10/2024')
    expect(formatFecha(new Date('2024-10-25T05:00:00Z'))).toBe('25/10/2024')
  })

  it('devuelve una raya para entradas vacías o inválidas', () => {
    expect(formatFecha(null)).toBe('—')
    expect(formatFecha('no-es-fecha')).toBe('—')
  })
})

describe('formatHora (24 h, America/Lima)', () => {
  it('convierte instantes UTC a la hora de Lima (UTC-5, sin horario de verano)', () => {
    expect(formatHora('2024-10-24T15:45:00Z')).toBe('10:45')
    expect(formatHora('2024-07-01T00:30:00Z')).toBe('19:30')
    expect(formatHora('2024-01-15T05:00:00Z')).toBe('00:00')
  })

  it('respeta el desfase explícito de la cadena', () => {
    expect(formatHora('2024-10-24T09:00:00-05:00')).toBe('09:00')
  })

  it('deja tal cual un LocalTime de la API', () => {
    expect(formatHora('09:00:00')).toBe('09:00')
    expect(formatHora('18:30')).toBe('18:30')
  })
})

describe('otros formatos', () => {
  it('formatFechaHora combina fecha y hora de Lima', () => {
    expect(formatFechaHora('2024-10-25T03:15:00Z')).toBe('24/10/2024 22:15')
  })

  it('formatFechaLarga en español', () => {
    expect(formatFechaLarga('2024-10-24')).toBe('jueves, 24 de octubre de 2024')
  })

  it('hoyEnLima usa el día de Lima aunque en UTC ya sea mañana', () => {
    expect(hoyEnLima(new Date('2024-10-25T02:00:00Z'))).toBe('2024-10-24')
  })

  it('saludoSegunHora usa la hora de Lima', () => {
    expect(saludoSegunHora(new Date('2024-10-24T13:00:00Z'))).toBe('Buenos días') // 08:00
    expect(saludoSegunHora(new Date('2024-10-24T20:00:00Z'))).toBe('Buenas tardes') // 15:00
    expect(saludoSegunHora(new Date('2024-10-25T01:00:00Z'))).toBe('Buenas noches') // 20:00
  })
})
