// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { MINIMO_AA, PARES_CONTRASTE, leerTokensColor, ratioContraste } from './contrast'

// Se lee el archivo real de tokens: el test falla si alguien cambia un color y rompe un par.
const tokens = leerTokensColor(readFileSync(new URL('./tokens.css', import.meta.url), 'utf8'))

describe('utilidades de contraste', () => {
  it('calcula los extremos conocidos', () => {
    expect(ratioContraste('#000000', '#FFFFFF')).toBeCloseTo(21, 5)
    expect(ratioContraste('#FFFFFF', '#FFFFFF')).toBeCloseTo(1, 5)
  })

  it('confirma que el texto blanco tiene contraste accesible sobre secondary-container', () => {
    expect(ratioContraste('#FFFFFF', tokens['secondary-container'])).toBeGreaterThanOrEqual(MINIMO_AA.normal)
  })
})

describe('tokens de color (WCAG AA)', () => {
  it('lee los tokens de tokens.css', () => {
    expect(Object.keys(tokens).length).toBeGreaterThan(20)
    expect(tokens['primary-container']).toBe('#1E293B')
  })

  it.each(PARES_CONTRASTE)('$texto sobre $fondo ($uso)', ({ texto, fondo, tamano }) => {
    const colorTexto = tokens[texto]
    const colorFondo = tokens[fondo]
    expect(colorTexto, `falta el token --vd-${texto}`).toBeDefined()
    expect(colorFondo, `falta el token --vd-${fondo}`).toBeDefined()
    const ratio = ratioContraste(colorTexto, colorFondo)
    expect(ratio, `${texto} (${colorTexto}) / ${fondo} (${colorFondo}) = ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(
      MINIMO_AA[tamano],
    )
  })
})
