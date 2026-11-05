/**
 * Aritmética de fechas calendario ("aaaa-mm-dd", como LocalDate de la API) y horas ("HH:mm"), sin depender de la
 * zona del navegador: se opera en UTC sobre el día calendario. "Hoy" y "ahora" siempre son los de America/Lima.
 */
import { ZONA_HORARIA } from '@/shared/format'

function aUtc(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`)
}

function aIso(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function sumarDias(fecha: string, dias: number): string {
  const d = aUtc(fecha)
  d.setUTCDate(d.getUTCDate() + dias)
  return aIso(d)
}

/** Día de la semana ISO: 1 = lunes … 7 = domingo (igual que `diaSemana` de los horarios). */
export function diaSemanaIso(fecha: string): number {
  const dia = aUtc(fecha).getUTCDay()
  return dia === 0 ? 7 : dia
}

/** Lunes de la semana de la fecha. */
export function inicioSemana(fecha: string): string {
  return sumarDias(fecha, 1 - diaSemanaIso(fecha))
}

export function diasDeLaSemana(fecha: string): string[] {
  const lunes = inicioSemana(fecha)
  return Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i))
}

const NOMBRE_DIA = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo']
const NOMBRE_DIA_CORTO = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']

export function nombreDia(fecha: string): string {
  return NOMBRE_DIA[diaSemanaIso(fecha) - 1]
}

export function nombreDiaCorto(fecha: string): string {
  return NOMBRE_DIA_CORTO[diaSemanaIso(fecha) - 1]
}

export function capitalizar(texto: string): string {
  return texto.charAt(0).toLocaleUpperCase('es-PE') + texto.slice(1)
}

/** "HH:mm[:ss]" → minutos desde la medianoche. */
export function aMinutos(hora: string | undefined | null): number {
  const m = /^(\d{1,2}):(\d{2})/.exec(hora ?? '')
  return m ? Number(m[1]) * 60 + Number(m[2]) : 0
}

/** Minutos → "HH:mm". */
export function deMinutos(minutos: number): string {
  const h = Math.floor(minutos / 60)
  const m = minutos % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

export function horaCorta(hora: string | undefined | null): string {
  return hora ? deMinutos(aMinutos(hora)) : '—'
}

export function horaFin(hora: string | undefined | null, duracion: number | undefined): string {
  return deMinutos(aMinutos(hora) + (duracion && duracion > 0 ? duracion : 30))
}

/** Minutos transcurridos del día en Lima. */
export function minutosAhoraEnLima(ahora: Date = new Date()): number {
  const p = new Intl.DateTimeFormat('es-PE', {
    timeZone: ZONA_HORARIA,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(ahora)
  const h = Number(p.find((x) => x.type === 'hour')?.value ?? 0)
  const m = Number(p.find((x) => x.type === 'minute')?.value ?? 0)
  return h * 60 + m
}

export function minutosEntreInstantes(inicio: string | null | undefined, fin: string | null | undefined = new Date().toISOString()): number | null {
  if (!inicio || !fin) return null
  const inicioMs = Date.parse(inicio)
  const finMs = Date.parse(fin)
  if (!Number.isFinite(inicioMs) || !Number.isFinite(finMs)) return null
  return Math.max(0, Math.floor((finMs - inicioMs) / 60_000))
}

export function horaInstanteEnLima(instante: string | null | undefined): string | null {
  if (!instante || !Number.isFinite(Date.parse(instante))) return null
  return new Intl.DateTimeFormat('es-PE', {
    timeZone: ZONA_HORARIA,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).format(new Date(instante))
}
