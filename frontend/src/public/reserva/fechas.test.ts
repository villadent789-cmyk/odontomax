import { describe, expect, it } from 'vitest'
import { hoyEnLima } from '@/shared/format'
import { especialistasPara, type EspecialistaPublico } from './api-reserva'
import { fechaConDia, proximosDias, sumarDias } from './fechas'

describe('fechas del asistente (America/Lima)', () => {
  it('a las 21:00 de Lima del 31/12 sigue siendo 31/12 aunque en UTC ya sea 1/1', () => {
    const hoy = hoyEnLima(new Date('2027-01-01T02:00:00Z'))
    expect(hoy).toBe('2026-12-31')
    expect(proximosDias(3, hoy)).toEqual(['2026-12-31', '2027-01-01', '2027-01-02'])
  })

  it('suma días cruzando meses y años bisiestos', () => {
    expect(sumarDias('2028-02-28', 1)).toBe('2028-02-29')
    expect(sumarDias('2026-10-31', 1)).toBe('2026-11-01')
  })

  it('muestra el día de la semana y dd/mm/aaaa', () => {
    expect(fechaConDia('2026-10-09')).toBe('Viernes 09/10/2026')
  })
})

describe('especialistasPara', () => {
  const lista: EspecialistaPublico[] = [
    { id: '1', nombre: 'A', especialidad: 'Ortodoncia', sedes: ['sede-chilca'] },
    { id: '2', nombre: 'B', especialidad: 'ortodoncia ', sedes: ['sede-centro'] },
    { id: '3', nombre: 'C', especialidad: 'Endodoncia', sedes: ['sede-chilca'] },
  ]

  it('filtra por sede y especialidad sin distinguir mayúsculas', () => {
    expect(especialistasPara(lista, 'sede-chilca', 'ORTODONCIA').map((e) => e.id)).toEqual(['1'])
    expect(especialistasPara(lista, 'sede-centro', 'Ortodoncia').map((e) => e.id)).toEqual(['2'])
    expect(especialistasPara(lista, undefined, 'Ortodoncia')).toEqual([])
  })
})
