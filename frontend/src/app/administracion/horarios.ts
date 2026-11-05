import type { CuerpoHorario, HorarioDoctor } from './api-administracion'

export const DIAS = [
  { valor: 1, nombre: 'Lunes' },
  { valor: 2, nombre: 'Martes' },
  { valor: 3, nombre: 'Miércoles' },
  { valor: 4, nombre: 'Jueves' },
  { valor: 5, nombre: 'Viernes' },
  { valor: 6, nombre: 'Sábado' },
  { valor: 7, nombre: 'Domingo' },
] as const

export function nombreDia(dia: number | undefined): string {
  return DIAS.find((d) => d.valor === dia)?.nombre ?? '—'
}

export interface FilaHorario {
  clave: string
  sedeId: string
  diaSemana: number
  horaInicio: string
  horaFin: string
}

let contador = 0
export function nuevaClave(): string {
  contador += 1
  return `h-${contador}`
}

/** "09:00:00" → "09:00" (lo que entiende <input type="time">). */
const hhmm = (hora: string | undefined) => (hora ?? '').slice(0, 5)

export function filasDesdeHorarios(horarios: readonly HorarioDoctor[] | undefined): FilaHorario[] {
  return [...(horarios ?? [])]
    .sort((a, b) => (a.sedeId ?? '').localeCompare(b.sedeId ?? '') || (a.diaSemana ?? 0) - (b.diaSemana ?? 0))
    .map((h) => ({
      clave: nuevaClave(),
      sedeId: h.sedeId ?? '',
      diaSemana: h.diaSemana ?? 1,
      horaInicio: hhmm(h.horaInicio),
      horaFin: hhmm(h.horaFin),
    }))
}

export function cuerpoDesdeFilas(filas: readonly FilaHorario[]): CuerpoHorario[] {
  return filas.map((f) => ({ sedeId: f.sedeId, diaSemana: f.diaSemana, horaInicio: f.horaInicio, horaFin: f.horaFin }))
}

/** Mismas reglas que el backend (Especialista.reemplazarHorarios), para avisar antes de enviar. */
export function validarHorarios(filas: readonly FilaHorario[]): string | null {
  const vistas = new Set<string>()
  for (const f of filas) {
    if (!f.sedeId) return 'Elige la sede de cada turno.'
    if (!f.horaInicio || !f.horaFin) return 'Indica la hora de inicio y de fin de cada turno.'
    if (f.horaFin <= f.horaInicio) return `El turno del ${nombreDia(f.diaSemana).toLowerCase()} termina antes de empezar.`
    const clave = `${f.sedeId}#${f.diaSemana}`
    if (vistas.has(clave)) return `Solo se permite un turno por sede y día (hay dos el ${nombreDia(f.diaSemana).toLowerCase()}).`
    vistas.add(clave)
  }
  return null
}
