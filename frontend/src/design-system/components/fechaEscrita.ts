import { hoyEnLima } from '@/shared/format'

const SOLO_DIGITOS = /\D/g

/** «15031985», «15-03-1985» o «15/03/1985» → «15/03/1985» (se van agregando las barras al escribir). */
export function formatearFechaEscrita(texto: string): string {
  const d = texto.replace(SOLO_DIGITOS, '').slice(0, 8)
  if (d.length <= 2) return d
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`
}

/** Convierte «dd/mm/aaaa» a ISO. Si está incompleto, devuelve iso vacío y sin error; si está completo y no vale, un error. */
export function interpretarFechaEscrita(texto: string, hoy: string = hoyEnLima()): { iso: string; error: string | null } {
  if (!texto) return { iso: '', error: null }
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto)
  if (!m) return { iso: '', error: texto.replace(SOLO_DIGITOS, '').length >= 8 ? 'Escribe la fecha como día/mes/año, por ejemplo 15/03/1985.' : null }
  const [, dd, mm, aaaa] = m
  const dia = Number(dd)
  const mes = Number(mm)
  const anio = Number(aaaa)
  const fecha = new Date(Date.UTC(anio, mes - 1, dia))
  const existe = fecha.getUTCFullYear() === anio && fecha.getUTCMonth() === mes - 1 && fecha.getUTCDate() === dia
  if (!existe || anio < 1900) return { iso: '', error: 'Esa fecha no existe. Revisa el día, el mes y el año.' }
  const iso = `${aaaa}-${mm}-${dd}`
  if (iso > hoy) return { iso: '', error: 'La fecha de nacimiento no puede ser futura.' }
  return { iso, error: null }
}

export function isoATexto(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  return m ? `${m[3]}/${m[2]}/${m[1]}` : ''
}

