import { act, renderHook } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { useAlCambiar } from './useAlCambiar'

function useFormulario(abierto: boolean, inicial: string) {
  const [texto, setTexto] = useState('')
  useAlCambiar([abierto, inicial], () => {
    if (abierto) setTexto(inicial)
  })
  return { texto, setTexto }
}

describe('useAlCambiar', () => {
  it('aplica el estado en el primer render y cuando cambia una dependencia, sin pisar lo escrito entre cambios', () => {
    const { result, rerender } = renderHook(({ abierto, inicial }) => useFormulario(abierto, inicial), {
      initialProps: { abierto: true, inicial: 'Ana' },
    })
    expect(result.current.texto).toBe('Ana')

    act(() => result.current.setTexto('Ana María'))
    rerender({ abierto: true, inicial: 'Ana' })
    expect(result.current.texto).toBe('Ana María')

    rerender({ abierto: true, inicial: 'Luis' })
    expect(result.current.texto).toBe('Luis')

    act(() => result.current.setTexto('borrador'))
    rerender({ abierto: false, inicial: 'Luis' })
    expect(result.current.texto).toBe('borrador')
    rerender({ abierto: true, inicial: 'Luis' })
    expect(result.current.texto).toBe('Luis')
  })
})
