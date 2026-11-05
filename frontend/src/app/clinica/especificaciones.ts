/**
 * «Especificaciones» del odontograma (Norma Técnica, reglas 9, 10 y 13): el texto que explica con claridad cada hallazgo
 * de cada pieza, incluidas las piezas con más de una anomalía y lo que no se puede ver en el dibujo. Se arma solo a
 * partir de lo registrado, así que pantalla e impresión dicen siempre lo mismo.
 */
import { buscarHerramienta } from './herramientasOdontograma'

export interface HallazgoRegistrado {
  piezaFdi: number
  estado: string
  caras?: string[] | null
  observacion?: string | null
}

export interface LineaEspecificacion {
  piezaFdi: number
  texto: string
}

/** Marca con que se anota que un hallazgo se vio en una radiografía (regla 11 de la norma); va al inicio de la observación. */
export const MARCA_RADIOGRAFIA = 'Según radiografía'

/** Separa la marca de radiografía del resto de la observación. */
export function separarRadiografia(observacion?: string | null): { radiografico: boolean; texto: string } {
  const o = observacion?.trim() ?? ''
  if (!o.startsWith(MARCA_RADIOGRAFIA)) return { radiografico: false, texto: o }
  return { radiografico: true, texto: o.slice(MARCA_RADIOGRAFIA.length).replace(/^[\s:.\-—]+/, '') }
}

/** Arma la observación a guardar: con la marca de radiografía si corresponde. */
export function unirRadiografia(radiografico: boolean, texto: string): string | undefined {
  const t = texto.trim()
  if (!radiografico) return t || undefined
  return t ? `${MARCA_RADIOGRAFIA}: ${t}` : MARCA_RADIOGRAFIA
}

const NOMBRE_CARA: Record<string, string> = {
  O: 'oclusal',
  V: 'vestibular',
  P: 'palatina',
  L: 'lingual',
  M: 'mesial',
  D: 'distal',
  I: 'incisal',
  TODAS: 'todas las caras',
}

function describir(h: HallazgoRegistrado): string {
  const herramienta = buscarHerramienta(h.estado)
  const nombre = herramienta?.label ?? h.estado
  const sigla = herramienta && herramienta.codigoCorto && herramienta.codigoCorto !== herramienta.label ? ` (${herramienta.codigoCorto})` : ''
  const caras = (h.caras ?? []).map((c) => NOMBRE_CARA[c.toUpperCase()] ?? c)
  const enCaras = caras.length > 0 ? `, cara ${caras.join(' y ')}` : ''
  const nota = h.observacion?.trim() ? ` — ${h.observacion.trim()}` : ''
  return `${nombre}${herramienta?.nivel === 'cara' ? '' : sigla}${enCaras}${nota}`
}

/** Una línea por pieza, en orden FDI, con todos sus hallazgos. */
export function lineasEspecificaciones(hallazgos: HallazgoRegistrado[]): LineaEspecificacion[] {
  const porPieza = new Map<number, HallazgoRegistrado[]>()
  for (const h of hallazgos) porPieza.set(h.piezaFdi, [...(porPieza.get(h.piezaFdi) ?? []), h])
  return [...porPieza]
    .sort(([a], [b]) => a - b)
    .map(([piezaFdi, lista]) => ({ piezaFdi, texto: lista.map(describir).join('; ') }))
}
