import { describe, expect, it } from 'vitest'
import { textoDeError } from './http'
import { esOpcion } from './opciones'

describe('esOpcion', () => {
  it('acepta solo valores de la lista', () => {
    const opciones = ['TODOS', 'BAJO'] as const
    expect(esOpcion(opciones, 'BAJO')).toBe(true)
    expect(esOpcion(opciones, 'otro')).toBe(false)
  })
})

describe('textoDeError', () => {
  it('usa el message del error y, si no hay, el texto por defecto', () => {
    expect(textoDeError(new Error('Stock insuficiente'), 'Falló')).toBe('Stock insuficiente')
    expect(textoDeError(new Error(''), 'Falló')).toBe('Falló')
    expect(textoDeError('texto suelto', 'Falló')).toBe('Falló')
    expect(textoDeError(null, 'Falló')).toBe('Falló')
    expect(textoDeError({ message: 42 }, 'Falló')).toBe('Falló')
  })
})
