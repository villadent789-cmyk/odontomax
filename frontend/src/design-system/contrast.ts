/** Utilidades de contraste WCAG 2.x y lectura de tokens desde tokens.css. */

export type TamanoTexto = 'normal' | 'grande'

import type { TonoBadge } from './variants'

/** Mínimos WCAG AA: 4.5:1 texto normal, 3:1 texto grande (≥ 24px, o ≥ 18.66px en negrita) y componentes de UI. */
export const MINIMO_AA: Record<TamanoTexto, number> = { normal: 4.5, grande: 3 }

function canal(c: number): number {
  const s = c / 255
  return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
}

export function hexARgb(hex: string): [number, number, number] {
  const limpio = hex.trim().replace('#', '')
  const completo = limpio.length === 3 ? [...limpio].map((c) => c + c).join('') : limpio
  if (!/^[0-9a-fA-F]{6}$/.test(completo)) throw new Error(`Color hex inválido: ${hex}`)
  const n = parseInt(completo, 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

export function luminancia(hex: string): number {
  const [r, g, b] = hexARgb(hex)
  return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b)
}

export function ratioContraste(a: string, b: string): number {
  const la = luminancia(a)
  const lb = luminancia(b)
  const [claro, oscuro] = la > lb ? [la, lb] : [lb, la]
  return (claro + 0.05) / (oscuro + 0.05)
}

/** Extrae los tokens de color `--vd-*: #hex` de un texto CSS. */
export function leerTokensColor(css: string): Record<string, string> {
  const tokens: Record<string, string> = {}
  for (const m of css.matchAll(/--vd-([a-z0-9-]+):\s*(#[0-9a-fA-F]{3,6})\b/g)) {
    tokens[m[1]] = m[2].toUpperCase()
  }
  return tokens
}

export interface ParContraste {
  texto: string
  fondo: string
  tamano: TamanoTexto
  uso: string
}

/**
 * Todos los pares texto/fondo que usa la interfaz. Si un componente empieza a usar un par nuevo,
 * añádelo aquí: el test de contraste lo verificará.
 */
export const PARES_CONTRASTE: ParContraste[] = [
  // Texto general sobre superficies
  { texto: 'on-surface', fondo: 'surface', tamano: 'normal', uso: 'Texto principal sobre superficie clínica' },
  { texto: 'on-surface', fondo: 'surface-container-lowest', tamano: 'normal', uso: 'Texto principal en tarjetas' },
  { texto: 'on-surface', fondo: 'surface-container-low', tamano: 'normal', uso: 'Valor de campos, barra lateral' },
  { texto: 'on-surface', fondo: 'surface-container', tamano: 'normal', uso: 'Texto en bloques internos' },
  { texto: 'on-surface-variant', fondo: 'surface', tamano: 'normal', uso: 'Texto secundario sobre superficie' },
  { texto: 'on-surface-variant', fondo: 'surface-container-lowest', tamano: 'normal', uso: 'Texto secundario en tarjetas' },
  { texto: 'on-surface-variant', fondo: 'surface-container-low', tamano: 'normal', uso: 'Menú lateral, Kbd' },
  { texto: 'on-surface-variant', fondo: 'surface-container', tamano: 'normal', uso: 'Etiquetas en bloques internos' },
  { texto: 'on-surface-variant', fondo: 'surface-container-high', tamano: 'normal', uso: 'Texto sobre hover del menú' },
  { texto: 'placeholder', fondo: 'surface-container-low', tamano: 'normal', uso: 'Placeholder de campos' },
  // Marca
  { texto: 'primary', fondo: 'surface', tamano: 'normal', uso: 'Títulos y enlaces sobre fondo' },
  { texto: 'primary', fondo: 'surface-container-lowest', tamano: 'normal', uso: 'Títulos y enlaces en tarjetas' },
  { texto: 'primary', fondo: 'surface-container-low', tamano: 'normal', uso: 'Marca en barra lateral' },
  { texto: 'primary', fondo: 'surface-container-high', tamano: 'normal', uso: 'Botón secundario tonal' },
  { texto: 'on-primary', fondo: 'primary', tamano: 'normal', uso: 'Panel oscuro del login, pestaña activa' },
  { texto: 'primary-fixed-dim', fondo: 'primary', tamano: 'normal', uso: 'Texto secundario en panel oscuro' },
  { texto: 'on-primary-container', fondo: 'primary-container', tamano: 'normal', uso: 'Botón primario, menú activo' },
  { texto: 'on-primary-fixed', fondo: 'primary-fixed', tamano: 'normal', uso: 'Chip de estado primario' },
  // Acción Clínica (Zafiro)
  { texto: 'on-secondary-container', fondo: 'secondary-container', tamano: 'normal', uso: 'Botón de acción clínica' },
  { texto: 'secondary', fondo: 'surface-container-lowest', tamano: 'normal', uso: 'Texto de acción en tarjetas' },
  { texto: 'secondary', fondo: 'surface', tamano: 'normal', uso: 'Texto de acción sobre fondo' },
  { texto: 'secondary', fondo: 'surface-container-low', tamano: 'normal', uso: 'Turno en la consulta de reserva (A6)' },
  { texto: 'on-secondary-fixed', fondo: 'secondary-fixed', tamano: 'normal', uso: 'Chip de estado de acción' },
  // Índigo Villamax
  { texto: 'on-tertiary', fondo: 'tertiary', tamano: 'normal', uso: 'Botón Villamax' },
  { texto: 'tertiary', fondo: 'surface-container-lowest', tamano: 'normal', uso: 'Marca Villamax en tarjetas' },
  { texto: 'tertiary', fondo: 'surface-container-low', tamano: 'normal', uso: 'Marca Villamax en barra lateral' },
  { texto: 'on-tertiary-fixed', fondo: 'tertiary-fixed', tamano: 'normal', uso: 'Chip de estado índigo' },
  // Error
  { texto: 'on-error-container', fondo: 'error-container', tamano: 'normal', uso: 'Alertas de error y bloqueo' },
  { texto: 'error', fondo: 'surface-container-lowest', tamano: 'normal', uso: 'Mensaje de error de campo' },
  { texto: 'error', fondo: 'surface', tamano: 'normal', uso: 'Mensaje de error sobre fondo' },
  { texto: 'on-error', fondo: 'error', tamano: 'normal', uso: 'Botón destructivo' },
  // Componentes no textuales (3:1): foco y bordes de controles
  { texto: 'focus', fondo: 'surface', tamano: 'grande', uso: 'Anillo de foco sobre fondo' },
  { texto: 'focus', fondo: 'surface-container-lowest', tamano: 'grande', uso: 'Anillo de foco en tarjetas' },
  { texto: 'focus', fondo: 'surface-container-low', tamano: 'grande', uso: 'Anillo de foco en barra lateral' },
  { texto: 'outline', fondo: 'surface-container-lowest', tamano: 'grande', uso: 'Borde de casilla/control' },
]

/** Tokens de texto y fondo de cada tono de Badge/EstadoBadge (deben coincidir con badgeVariants en variants.ts). */
export const COLORES_TONO_BADGE: Record<TonoBadge, { texto: string; fondo: string }> = {
  neutro: { texto: 'on-surface-variant', fondo: 'surface-container-high' },
  primario: { texto: 'on-primary-fixed', fondo: 'primary-fixed' },
  accion: { texto: 'on-secondary-fixed', fondo: 'secondary-fixed' },
  villamax: { texto: 'on-tertiary-fixed', fondo: 'tertiary-fixed' },
  peligro: { texto: 'on-error-container', fondo: 'error-container' },
  claro: { texto: 'primary', fondo: 'surface-container-lowest' },
}
