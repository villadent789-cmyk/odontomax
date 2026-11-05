import { describe, expect, it } from 'vitest'
import { avisoDiaSinTurnos, diasDeAtencion, resumenAtencion, textoDias, type TurnoDoctor } from './horarioDoctor'

const t = (sedeId: string, diaSemana: number, horaInicio: string, horaFin: string): TurnoDoctor => ({ sedeId, diaSemana, horaInicio, horaFin })

const LUN_MIE_CHILCA = [t('chilca', 1, '09:00:00', '13:00:00'), t('chilca', 3, '09:00:00', '13:00:00')]

describe('textoDias', () => {
  it('une los días con "y" y resume 3 o más seguidos con "a"', () => {
    expect(textoDias([1])).toBe('Lunes')
    expect(textoDias([3, 1])).toBe('Lunes y Miércoles')
    expect(textoDias([1, 2, 3, 4, 5, 6])).toBe('Lunes a Sábado')
    expect(textoDias([1, 3, 4, 5])).toBe('Lunes y Miércoles a Viernes')
    expect(textoDias([1, 2, 5])).toBe('Lunes, Martes y Viernes')
    expect(textoDias([])).toBe('')
  })
})

describe('resumenAtencion', () => {
  it('"Lunes y Miércoles de 09:00 a 13:00" para la sede elegida, sin segundos', () => {
    expect(resumenAtencion(LUN_MIE_CHILCA, 'chilca')).toBe('Lunes y Miércoles de 09:00 a 13:00')
  })

  it('solo cuenta los turnos de esa sede', () => {
    const turnos = [...LUN_MIE_CHILCA, t('centro', 5, '15:00', '19:00')]
    expect(resumenAtencion(turnos, 'chilca')).toBe('Lunes y Miércoles de 09:00 a 13:00')
    expect(resumenAtencion(turnos, 'centro')).toBe('Viernes de 15:00 a 19:00')
    expect(diasDeAtencion(turnos, 'centro')).toEqual([5])
  })

  it('si los turnos tienen horas distintas los separa con punto y coma', () => {
    const turnos = [t('chilca', 2, '15:00', '19:00'), t('chilca', 1, '09:00', '13:00')]
    expect(resumenAtencion(turnos, 'chilca')).toBe('Lunes de 09:00 a 13:00; Martes de 15:00 a 19:00')
  })

  it('devuelve null si no tiene turnos en esa sede', () => {
    expect(resumenAtencion(LUN_MIE_CHILCA, 'centro')).toBeNull()
    expect(resumenAtencion([], 'chilca')).toBeNull()
    expect(resumenAtencion(undefined, 'chilca')).toBeNull()
  })
})

describe('avisoDiaSinTurnos', () => {
  // 2026-10-14 es miércoles; 2026-10-15 jueves; 2026-10-17 sábado; 2026-10-18 domingo.
  it('avisa con el día y los días de atención cuando la fecha cae en un día sin turnos', () => {
    expect(avisoDiaSinTurnos(LUN_MIE_CHILCA, 'chilca', '2026-10-15')).toBe(
      'El especialista no atiende los jueves en esta sede. Días de atención: Lunes y Miércoles.',
    )
    expect(avisoDiaSinTurnos(LUN_MIE_CHILCA, 'chilca', '2026-10-17')).toContain('los sábados')
    expect(avisoDiaSinTurnos(LUN_MIE_CHILCA, 'chilca', '2026-10-18')).toContain('los domingos')
  })

  it('los días que ya terminan en s no se pluralizan dos veces', () => {
    const solo = [t('chilca', 4, '09:00', '13:00')]
    expect(avisoDiaSinTurnos(solo, 'chilca', '2026-10-14')).toContain('los miércoles')
  })

  it('un día con turno no genera aviso', () => {
    expect(avisoDiaSinTurnos(LUN_MIE_CHILCA, 'chilca', '2026-10-14')).toBeNull()
  })

  it('sin turnos en la sede lo dice; sin datos todavía no afirma nada', () => {
    expect(avisoDiaSinTurnos(LUN_MIE_CHILCA, 'centro', '2026-10-14')).toBe('El especialista no tiene horario de atención en esta sede.')
    expect(avisoDiaSinTurnos(undefined, 'chilca', '2026-10-14')).toBeNull()
  })
})
