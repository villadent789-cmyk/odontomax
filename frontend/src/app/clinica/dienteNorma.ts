/**
 * Cómo se dibuja un diente en el odontograma de la Norma Técnica: contorno negro, corona de 5 caras y raíz proporcional.
 * Solo se pintan las caras con caries o restauración; los demás hallazgos van como símbolo y como sigla en el recuadro:
 * línea en la raíz (tratamiento pulpar), círculo (corona), líneas a nivel de los ápices que unen las piezas (prótesis
 * y aparatos), flechas (extruido, intruido, migración, giroversión), aspa (diente ausente), etc. La pantalla y la hoja
 * impresa usan este mismo dibujo.
 */
import type { HerramientaConfig } from './herramientasOdontograma'

/** Medidas del dibujo: la corona es un cuadrado de 100 y la raíz mide 65 (proporcional, regla 12 de la norma). */
export const ANCHO = 100
export const ALTO = 165
export const LADO_CORONA = 100
export const CONTORNO = '#27272a'

/** Desplazamiento vertical de la corona: en el maxilar la raíz va arriba; en la mandíbula, abajo. */
export const desplazamientoCorona = (superior: boolean) => (superior ? 65 : 0)

/** Contorno de la raíz (se apoya sobre la corona, hacia afuera de la boca). */
export function rutaRaiz(superior: boolean): string {
  return superior
    ? 'M20,65 L34,14 Q50,2 66,14 L80,65 Z'
    : 'M20,100 L34,151 Q50,163 66,151 L80,100 Z'
}

/** Las 5 caras de la corona (se dibujan dentro de un grupo desplazado con `desplazamientoCorona`). */
export const CARAS_CORONA = {
  arriba: '0,0 100,0 75,25 25,25',
  derecha: '100,0 100,100 75,75 75,25',
  abajo: '0,100 100,100 75,75 25,75',
  izquierda: '0,0 0,100 25,75 25,25',
  centro: '25,25 75,25 75,75 25,75',
} as const

/** Filas de piezas tal como se ven (de izquierda a derecha): sirven para saber qué pieza es vecina de cuál. */
export const FILAS_ARCADA: number[][] = [
  [18, 17, 16, 15, 14, 13, 12, 11, 21, 22, 23, 24, 25, 26, 27, 28],
  [48, 47, 46, 45, 44, 43, 42, 41, 31, 32, 33, 34, 35, 36, 37, 38],
  [55, 54, 53, 52, 51, 61, 62, 63, 64, 65],
  [85, 84, 83, 82, 81, 71, 72, 73, 74, 75],
]

/** Qué hallazgos tienen las piezas de al lado, para unir los trazos que la norma dibuja de una pieza a otra. */
export interface Vecinos {
  izq: ReadonlySet<string>
  der: ReadonlySet<string>
}

export const SIN_VECINOS: Vecinos = { izq: new Set(), der: new Set() }

export function vecinosDe(fdi: number, idsPorPieza: ReadonlyMap<number, readonly string[]>): Vecinos {
  const fila = FILAS_ARCADA.find((f) => f.includes(fdi))
  if (!fila) return SIN_VECINOS
  const i = fila.indexOf(fdi)
  const ids = (pieza?: number) => new Set(pieza == null ? [] : (idsPorPieza.get(pieza) ?? []))
  return { izq: ids(fila[i - 1]), der: ids(fila[i + 1]) }
}

export type Primitiva =
  | { t: 'linea'; x1: number; y1: number; x2: number; y2: number; color: string; ancho: number }
  | { t: 'circulo'; cx: number; cy: number; r: number; color: string; ancho: number }
  | { t: 'rect'; x: number; y: number; w: number; h: number; color: string; ancho: number }
  | { t: 'poli'; puntos: string; color: string; ancho: number }
  | { t: 'camino'; d: string; color: string; ancho: number }
  | { t: 'relleno'; puntos: string; color: string }
  | { t: 'texto'; x: number; y: number; texto: string; color: string; tam: number }

const AZUL_AUSENTE = '#0B4FD6'

/** Flecha recta con punta. */
function flecha(x1: number, y1: number, x2: number, y2: number, color: string, ancho = 6): Primitiva[] {
  return [{ t: 'linea', x1, y1, x2, y2, color, ancho }, cabeza(x2, y2, x2 - x1, y2 - y1, color)]
}

/** Punta de flecha en (x, y) que mira hacia la dirección (dx, dy). */
function cabeza(x: number, y: number, dx: number, dy: number, color: string): Primitiva {
  const largo = Math.hypot(dx, dy) || 1
  const ux = dx / largo
  const uy = dy / largo
  const base = 18
  const bx = x - ux * base
  const by = y - uy * base
  const px = -uy * 9
  const py = ux * 9
  return { t: 'relleno', puntos: `${x},${y} ${bx + px},${by + py} ${bx - px},${by - py}`, color }
}

/** Flecha curva (curva de Bézier cuadrática) con punta. */
function flechaCurva(x1: number, y1: number, cx: number, cy: number, x2: number, y2: number, color: string): Primitiva[] {
  return [{ t: 'camino', d: `M${x1},${y1} Q${cx},${cy} ${x2},${y2}`, color, ancho: 6 }, cabeza(x2, y2, x2 - cx, y2 - cy, color)]
}

/** Decoración que la norma dibuja alrededor del número de la pieza. */
export function decoracionDeNumero(hallazgos: readonly HerramientaConfig[]): { tipo: 'triangulo' | 'circulos'; color: string } | null {
  const clavija = hallazgos.find((h) => h.id === 'diente_clavija')
  if (clavija) return { tipo: 'triangulo', color: clavija.colorHex }
  const doble = hallazgos.find((h) => h.id === 'geminacion' || h.id === 'fusion')
  return doble ? { tipo: 'circulos', color: doble.colorHex } : null
}

/** Símbolos de los hallazgos de una pieza, en las coordenadas del dibujo completo. */
export function primitivasDeHallazgos(superior: boolean, hallazgos: HerramientaConfig[], vecinos: Vecinos = SIN_VECINOS): Primitiva[] {
  const yC = desplazamientoCorona(superior)
  const apice = superior ? 8 : 157
  const haciaCorona = superior ? 1 : -1 // desde el ápice hacia la corona
  const medioRaiz = superior ? 36 : 129
  const medioCorona = yC + 50
  const plano = superior ? 1 : -1 // hacia el plano oclusal (abajo en el maxilar, arriba en la mandíbula)
  const lineas: Primitiva[] = []

  if (hallazgos.some((h) => h.id === 'ausente')) {
    // Diente ausente: aspa azul sobre la figura completa (corona y raíz).
    return [
      { t: 'linea', x1: 14, y1: 6, x2: 86, y2: 159, color: AZUL_AUSENTE, ancho: 8 },
      { t: 'linea', x1: 86, y1: 6, x2: 14, y2: 159, color: AZUL_AUSENTE, ancho: 8 },
    ]
  }

  for (const h of hallazgos) {
    const c = h.colorHex
    const izq = vecinos.izq.has(h.id)
    const der = vecinos.der.has(h.id)
    // Los trazos continuos se prolongan hasta la pieza vecina que comparte el hallazgo.
    const desde = izq ? -14 : 4
    const hasta = der ? 114 : 96

    if (h.id === 'remanente_radicular') {
      lineas.push({ t: 'texto', x: 50, y: medioRaiz + 10, texto: 'RR', color: c, tam: 34 })
      continue
    }
    if (h.id === 'protesis_removible') {
      // Dos líneas horizontales paralelas a nivel de los ápices de las piezas reemplazadas.
      lineas.push({ t: 'linea', x1: desde, y1: apice, x2: hasta, y2: apice, color: c, ancho: 6 })
      lineas.push({ t: 'linea', x1: desde, y1: apice + haciaCorona * 14, x2: hasta, y2: apice + haciaCorona * 14, color: c, ancho: 6 })
      continue
    }
    if (h.id === 'protesis_total') {
      // Dos líneas rectas paralelas y horizontales sobre las coronas.
      lineas.push({ t: 'linea', x1: izq ? -14 : 0, y1: yC + 38, x2: der ? 114 : 100, y2: yC + 38, color: c, ancho: 6 })
      lineas.push({ t: 'linea', x1: izq ? -14 : 0, y1: yC + 62, x2: der ? 114 : 100, y2: yC + 62, color: c, ancho: 6 })
      continue
    }
    if (h.id === 'aparato_removible') {
      // Línea en zig-zag a la altura de los ápices.
      const x0 = izq ? -14 : 4
      const x1 = der ? 114 : 96
      const puntos: string[] = []
      for (let x = x0, arriba = true; x <= x1 + 0.1; x += 12, arriba = !arriba) puntos.push(`${x},${apice + haciaCorona * (arriba ? 2 : 16)}`)
      lineas.push({ t: 'poli', puntos: puntos.join(' '), color: c, ancho: 5 })
      continue
    }
    if (h.id === 'edentulo_total') {
      // Línea recta horizontal sobre las coronas de las piezas ausentes del maxilar edéntulo.
      lineas.push({ t: 'linea', x1: izq ? -14 : 0, y1: medioCorona, x2: der ? 114 : 100, y2: medioCorona, color: c, ancho: 7 })
      continue
    }
    if (h.id === 'extruido') {
      lineas.push(...flecha(50, medioCorona - plano * 30, 50, medioCorona + plano * 34, c)) // hacia el plano oclusal
      continue
    }
    if (h.id === 'intruido') {
      lineas.push(...flecha(50, medioCorona + plano * 30, 50, medioCorona - plano * 34, c)) // recta vertical hacia el ápice
      continue
    }
    if (h.id === 'migracion') {
      lineas.push(...flecha(14, medioCorona, 86, medioCorona, c)) // desplazamiento horizontal (el sentido se anota en Especificaciones)
      continue
    }
    if (h.id === 'giroversion') {
      lineas.push(...flechaCurva(22, medioCorona + plano * 8, 50, medioCorona - plano * 30, 78, medioCorona + plano * 8, c))
      continue
    }
    if (h.id === 'transposicion') {
      lineas.push(...flechaCurva(20, medioCorona - 24, 50, medioCorona + 4, 80, medioCorona - 24, c))
      lineas.push(...flechaCurva(20, medioCorona + 24, 50, medioCorona - 4, 80, medioCorona + 24, c))
      continue
    }
    if (h.id === 'diastema') {
      // Paréntesis invertido «)(» entre esta pieza y la siguiente.
      lineas.push({ t: 'texto', x: 108, y: medioCorona + 22, texto: ')(', color: c, tam: 64 })
      continue
    }
    if (h.id === 'supernumerario') {
      // «S» encerrada en una circunferencia, entre los ápices de las piezas adyacentes.
      const y = apice + haciaCorona * 22
      lineas.push({ t: 'circulo', cx: 106, cy: y, r: 16, color: c, ancho: 4 })
      lineas.push({ t: 'texto', x: 106, y: y + 10, texto: 'S', color: c, tam: 26 })
      continue
    }
    switch (h.simbolo) {
      case 'conducto': // línea vertical en la raíz
        lineas.push({ t: 'linea', x1: 50, y1: apice + haciaCorona * 6, x2: 50, y2: apice + haciaCorona * 52, color: c, ancho: 7 })
        break
      case 'corona': // circunferencia que encierra la corona
        lineas.push({ t: 'circulo', cx: 50, cy: yC + 50, r: 47, color: c, ancho: 6 })
        break
      case 'fractura': // línea recta en el sentido de la fractura
        lineas.push({ t: 'linea', x1: 28, y1: superior ? 14 : 8, x2: 72, y2: superior ? 158 : 150, color: c, ancho: 7 })
        break
      case 'protesis': // prótesis fija: línea a nivel de los ápices con rayas en los pilares (los extremos)
        lineas.push({ t: 'linea', x1: desde, y1: apice, x2: hasta, y2: apice, color: c, ancho: 7 })
        if (!izq) lineas.push({ t: 'linea', x1: 4, y1: apice, x2: 4, y2: apice + haciaCorona * 22, color: c, ancho: 7 })
        if (!der) lineas.push({ t: 'linea', x1: 96, y1: apice, x2: 96, y2: apice + haciaCorona * 22, color: c, ancho: 7 })
        break
      case 'aparato': { // aparato fijo: cuadrados con cruz en las piezas de los extremos, unidos por una línea
        const y0 = apice + haciaCorona * 14 - 13
        const yMedio = y0 + 13
        if (izq && der) {
          lineas.push({ t: 'linea', x1: -14, y1: yMedio, x2: 114, y2: yMedio, color: c, ancho: 4 })
          break
        }
        lineas.push({ t: 'rect', x: 37, y: y0, w: 26, h: 26, color: c, ancho: 4 })
        lineas.push({ t: 'linea', x1: 37, y1: y0, x2: 63, y2: y0 + 26, color: c, ancho: 3 })
        lineas.push({ t: 'linea', x1: 63, y1: y0, x2: 37, y2: y0 + 26, color: c, ancho: 3 })
        if (izq) lineas.push({ t: 'linea', x1: -14, y1: yMedio, x2: 37, y2: yMedio, color: c, ancho: 4 })
        if (der) lineas.push({ t: 'linea', x1: 63, y1: yMedio, x2: 114, y2: yMedio, color: c, ancho: 4 })
        break
      }
      case 'visto': // pieza sana
        lineas.push({ t: 'poli', puntos: `28,${yC + 52} 44,${yC + 68} 74,${yC + 32}`, color: h.strokeHex, ancho: 9 })
        break
      default:
        // implante, equis y siglas: se anotan en el recuadro, sin dibujo sobre el diente
        break
    }
  }
  return lineas
}

const num = (n: number) => String(Math.round(n * 10) / 10)

/** El mismo dibujo como texto SVG (para la hoja impresa). */
export function primitivasASvg(ps: Primitiva[]): string {
  return ps
    .map((p) => {
      switch (p.t) {
        case 'linea':
          return `<line x1="${num(p.x1)}" y1="${num(p.y1)}" x2="${num(p.x2)}" y2="${num(p.y2)}" stroke="${p.color}" stroke-width="${p.ancho}" stroke-linecap="round"/>`
        case 'circulo':
          return `<circle cx="${num(p.cx)}" cy="${num(p.cy)}" r="${num(p.r)}" fill="none" stroke="${p.color}" stroke-width="${p.ancho}"/>`
        case 'rect':
          return `<rect x="${num(p.x)}" y="${num(p.y)}" width="${num(p.w)}" height="${num(p.h)}" fill="#fff" stroke="${p.color}" stroke-width="${p.ancho}"/>`
        case 'poli':
          return `<polyline points="${p.puntos}" fill="none" stroke="${p.color}" stroke-width="${p.ancho}" stroke-linecap="round" stroke-linejoin="round"/>`
        case 'camino':
          return `<path d="${p.d}" fill="none" stroke="${p.color}" stroke-width="${p.ancho}" stroke-linecap="round"/>`
        case 'relleno':
          return `<polygon points="${p.puntos}" fill="${p.color}"/>`
        case 'texto':
          return `<text x="${num(p.x)}" y="${num(p.y)}" font-size="${p.tam}" font-weight="800" fill="${p.color}" text-anchor="middle" font-family="Arial,sans-serif">${p.texto}</text>`
      }
    })
    .join('')
}
