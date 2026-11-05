/**
 * Formatos de Perú. Todas las fechas y horas se muestran en la zona horaria de la clínica
 * (America/Lima, UTC-5 sin horario de verano), sin importar la zona del navegador.
 */

export const ZONA_HORARIA = 'America/Lima'
export const LOCALE = 'es-PE'

const formatoSoles = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/**
 * Soles: `S/ 1,234.50`. Se fija el separador de miles con coma y decimal con punto (uso habitual en
 * comprobantes peruanos), porque el `es-PE` de Intl varía entre motores (algunos no agrupan miles).
 */
export function formatSoles(monto: number | string | null | undefined): string {
  if (monto === null || monto === undefined || monto === '') return '—'
  const n = typeof monto === 'string' ? Number(monto) : monto
  if (!Number.isFinite(n)) return '—'
  const texto = formatoSoles.format(Math.abs(n))
  return `${n < 0 ? '-' : ''}S/ ${texto}`
}

type EntradaFecha = Date | string | number

/**
 * Interpreta la entrada. Una fecha sin hora ("2024-10-24", como envía la API para LocalDate) se toma
 * como día calendario y no se desplaza por zona horaria.
 */
function aFecha(valor: EntradaFecha): Date | null {
  if (valor instanceof Date) return Number.isNaN(valor.getTime()) ? null : valor
  if (typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor)) {
    // Mediodía UTC = mismo día calendario en Lima (UTC-5).
    const d = new Date(`${valor}T12:00:00Z`)
    return Number.isNaN(d.getTime()) ? null : d
  }
  const d = new Date(valor)
  return Number.isNaN(d.getTime()) ? null : d
}

function partes(fecha: Date, opciones: Intl.DateTimeFormatOptions) {
  const fmt = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONA_HORARIA, ...opciones })
  const p: Record<string, string> = {}
  for (const { type, value } of fmt.formatToParts(fecha)) p[type] = value
  return p
}

/** Fecha `dd/mm/aaaa` en hora de Lima. */
export function formatFecha(valor: EntradaFecha | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  const fecha = aFecha(valor)
  if (!fecha) return '—'
  const p = partes(fecha, { day: '2-digit', month: '2-digit', year: 'numeric' })
  return `${p.day}/${p.month}/${p.year}`
}

/** Hora `HH:mm` (24 h) en hora de Lima. Acepta también "HH:mm[:ss]" (LocalTime de la API) tal cual. */
export function formatHora(valor: EntradaFecha | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  if (typeof valor === 'string') {
    const m = /^(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/.exec(valor)
    if (m) return `${m[1]}:${m[2]}`
  }
  const fecha = aFecha(valor)
  if (!fecha) return '—'
  const p = partes(fecha, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
  return `${p.hour}:${p.minute}`
}

/** Fecha y hora `dd/mm/aaaa HH:mm` en hora de Lima. */
export function formatFechaHora(valor: EntradaFecha | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  const fecha = aFecha(valor)
  if (!fecha) return '—'
  return `${formatFecha(fecha)} ${formatHora(fecha)}`
}

/** Fecha larga para encabezados: "jueves, 24 de octubre de 2024". */
export function formatFechaLarga(valor: EntradaFecha | null | undefined): string {
  if (valor === null || valor === undefined) return '—'
  const fecha = aFecha(valor)
  if (!fecha) return '—'
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone: ZONA_HORARIA,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(fecha)
}

/** Fecha de hoy en Lima como "aaaa-mm-dd" (para filtros y parámetros de la API). */
export function hoyEnLima(ahora: Date = new Date()): string {
  const p = partes(ahora, { day: '2-digit', month: '2-digit', year: 'numeric' })
  return `${p.year}-${p.month}-${p.day}`
}

/** Saludo según la hora en Lima. */
export function saludoSegunHora(ahora: Date = new Date()): string {
  const hora = Number(partes(ahora, { hour: '2-digit', hourCycle: 'h23' }).hour)
  if (hora < 12) return 'Buenos días'
  if (hora < 19) return 'Buenas tardes'
  return 'Buenas noches'
}
