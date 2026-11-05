import { LOCALE, ZONA_HORARIA, formatFecha, hoyEnLima } from '@/shared/format'

/** Días que ofrece el asistente a partir de hoy (en Lima). El backend no fija un máximo de anticipación. */
export const DIAS_OFRECIDOS = 14

/** Suma días a una fecha ISO (aaaa-mm-dd) sin depender de la zona del navegador. */
export function sumarDias(iso: string, dias: number): string {
  const d = new Date(`${iso}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + dias)
  return d.toISOString().slice(0, 10)
}

/** Próximos `n` días calendario en Lima, empezando hoy. */
export function proximosDias(n: number = DIAS_OFRECIDOS, hoy: string = hoyEnLima()): string[] {
  return Array.from({ length: n }, (_, i) => sumarDias(hoy, i))
}

const fmtDiaSemana = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONA_HORARIA, weekday: 'short' })
const fmtDiaSemanaLargo = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONA_HORARIA, weekday: 'long' })

function aFecha(iso: string) {
  return new Date(`${iso}T12:00:00Z`)
}

/** "lun", "mar"... sin punto final. */
export function diaSemanaCorto(iso: string): string {
  return fmtDiaSemana.format(aFecha(iso)).replace('.', '')
}

/** "jueves 09/10/2026": nombre del día + dd/mm/aaaa. */
export function fechaConDia(iso: string): string {
  const dia = fmtDiaSemanaLargo.format(aFecha(iso))
  return `${dia.charAt(0).toLocaleUpperCase(LOCALE)}${dia.slice(1)} ${formatFecha(iso)}`
}

export interface HorarioSlot {
  valor: string
  etiqueta: string
  turno: 'MANANA' | 'TARDE'
}

/** Horarios disponibles para atención pública: de 9:00 a.m. a 8:00 p.m. */
export const HORARIOS_SLOTS: readonly HorarioSlot[] = [
  { valor: '09:00', etiqueta: '9:00 am', turno: 'MANANA' },
  { valor: '10:00', etiqueta: '10:00 am', turno: 'MANANA' },
  { valor: '11:00', etiqueta: '11:00 am', turno: 'MANANA' },
  { valor: '12:00', etiqueta: '12:00 pm', turno: 'MANANA' },
  { valor: '13:00', etiqueta: '1:00 pm', turno: 'TARDE' },
  { valor: '14:00', etiqueta: '2:00 pm', turno: 'TARDE' },
  { valor: '15:00', etiqueta: '3:00 pm', turno: 'TARDE' },
  { valor: '16:00', etiqueta: '4:00 pm', turno: 'TARDE' },
  { valor: '17:00', etiqueta: '5:00 pm', turno: 'TARDE' },
  { valor: '18:00', etiqueta: '6:00 pm', turno: 'TARDE' },
  { valor: '19:00', etiqueta: '7:00 pm', turno: 'TARDE' },
  { valor: '20:00', etiqueta: '8:00 pm', turno: 'TARDE' },
] as const
