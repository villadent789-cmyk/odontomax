// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { ESTADOS_CITA } from '@/shared/api/tipos-manuales'
import { COLORES_TONO_BADGE, MINIMO_AA, leerTokensColor, ratioContraste } from './contrast'
import { ESTADOS } from './estados'
import { badgeVariants } from './variants'

const tokens = leerTokensColor(readFileSync(new URL('./tokens.css', import.meta.url), 'utf8'))

describe('mapa único de estados (EstadoBadge)', () => {
  it.each(ESTADOS_CITA)('el estado de cita %s tiene texto en español y tono', (estado) => {
    const definicion = ESTADOS[estado]
    expect(definicion, `falta ${estado} en ESTADOS`).toBeDefined()
    expect(definicion.texto).not.toMatch(/_/)
    expect(definicion.texto.trim()).not.toBe('')
  })

  it.each(Object.entries(ESTADOS))('%s tiene contraste AA en su chip', (_estado, { tono }) => {
    const { texto, fondo } = COLORES_TONO_BADGE[tono]
    const ratio = ratioContraste(tokens[texto], tokens[fondo])
    expect(ratio, `${texto} / ${fondo} = ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(MINIMO_AA.normal)
  })

  it('los colores de cada tono coinciden con las clases de badgeVariants', () => {
    for (const [tono, { texto, fondo }] of Object.entries(COLORES_TONO_BADGE)) {
      const clases = badgeVariants({ tono: tono as keyof typeof COLORES_TONO_BADGE })
      expect(clases).toContain(`bg-${fondo}`)
      expect(clases).toContain(`text-${texto}`)
    }
  })
})
