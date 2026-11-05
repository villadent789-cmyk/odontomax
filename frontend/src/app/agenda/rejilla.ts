/** Cálculos de la rejilla de la agenda (B3): franjas de 30 min, como los slots de GET /especialistas/{id}/disponibilidad. */
import type { Cita, Horario } from './api-agenda'
import { aMinutos } from './fechas'

export const PASO = 30
const INICIO_DEFECTO = 8 * 60
const FIN_DEFECTO = 18 * 60

/** Las citas canceladas no ocupan horario (el backend las excluye de colisiones y disponibilidad). */
export function ocupa(c: Cita): boolean {
  return c.estado !== 'CANCELADA'
}

function finDe(c: Cita): number {
  return aMinutos(c.hora) + (c.duracionMinutos && c.duracionMinutos > 0 ? c.duracionMinutos : 30)
}

/** Rango del día: une los horarios de los especialistas y las citas; si no hay nada, 08:00-18:00. */
export function rangoDelDia(horarios: Horario[], citas: Cita[]): { inicio: number; fin: number } {
  const inicios = [...horarios.map((h) => aMinutos(h.horaInicio)), ...citas.map((c) => aMinutos(c.hora))]
  const fines = [...horarios.map((h) => aMinutos(h.horaFin)), ...citas.map(finDe)]
  if (inicios.length === 0) return { inicio: INICIO_DEFECTO, fin: FIN_DEFECTO }
  const inicio = Math.floor(Math.min(...inicios) / PASO) * PASO
  const fin = Math.ceil(Math.max(...fines) / PASO) * PASO
  return { inicio, fin: Math.max(fin, inicio + PASO) }
}

export function franjas(inicio: number, fin: number): number[] {
  const r: number[] = []
  for (let m = inicio; m < fin; m += PASO) r.push(m)
  return r
}

/** Citas que empiezan dentro de la franja [franja, franja + 30). */
export function citasQueEmpiezan(citas: Cita[], franja: number): Cita[] {
  return citas.filter((c) => {
    const m = aMinutos(c.hora)
    return m >= franja && m < franja + PASO
  })
}

/** Cita (no cancelada) que empezó antes y sigue ocupando la franja. */
export function citaQueContinua(citas: Cita[], franja: number): Cita | undefined {
  return citas.find((c) => ocupa(c) && aMinutos(c.hora) < franja && finDe(c) > franja)
}

/** Horarios del especialista para un día de la semana ISO y sede. */
export function horariosDelDia(horarios: Horario[] | undefined, diaSemana: number, sedeId: string | null): Horario[] {
  return (horarios ?? []).filter((h) => h.diaSemana === diaSemana && (!sedeId || !h.sedeId || h.sedeId === sedeId))
}

export function dentroDeHorario(horarios: Horario[], franja: number): boolean {
  return horarios.some((h) => franja >= aMinutos(h.horaInicio) && franja < aMinutos(h.horaFin))
}
