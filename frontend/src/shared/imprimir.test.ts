import { afterEach, describe, expect, it, vi } from 'vitest'
import { ErrorImpresion, imprimirDocumento } from './imprimir'

/** jsdom no imprime: se simula la ventana del marco y se dispara su evento "load" a mano. */
function simularVentana(print: () => void) {
  const ventana = { focus: vi.fn(), print: vi.fn(print), addEventListener: vi.fn() }
  vi.spyOn(HTMLIFrameElement.prototype, 'contentWindow', 'get').mockReturnValue(ventana as unknown as Window)
  return ventana
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
  document.body.innerHTML = ''
})

describe('imprimirDocumento', () => {
  it('carga el documento en un marco oculto, abre la impresión y no deja nada visible', async () => {
    const ventana = simularVentana(() => {})
    const pendiente = imprimirDocumento('<html><body>Reporte</body></html>')

    const marco = document.querySelector('iframe') as HTMLIFrameElement
    expect(marco).not.toBeNull()
    expect(marco.getAttribute('aria-hidden')).toBe('true')
    expect(marco.srcdoc).toBe('<html><body>Reporte</body></html>')
    expect(marco.style.visibility).toBe('hidden')

    marco.dispatchEvent(new Event('load'))
    await expect(pendiente).resolves.toBeUndefined()
    expect(ventana.focus).toHaveBeenCalled()
    expect(ventana.print).toHaveBeenCalledTimes(1)
  })

  it('retira el marco cuando termina la impresión', async () => {
    vi.useFakeTimers()
    const ventana = simularVentana(() => {})
    const pendiente = imprimirDocumento('<p>x</p>')
    const marco = document.querySelector('iframe') as HTMLIFrameElement
    marco.dispatchEvent(new Event('load'))
    await pendiente

    const alTerminar = ventana.addEventListener.mock.calls.find(([evento]) => evento === 'afterprint')![1] as () => void
    alTerminar()
    vi.advanceTimersByTime(1000)
    expect(document.querySelector('iframe')).toBeNull()
  })

  it('si el navegador no puede imprimir, rechaza con un mensaje claro y retira el marco', async () => {
    simularVentana(() => {
      throw new Error('bloqueado')
    })
    const pendiente = imprimirDocumento('<p>x</p>')
    document.querySelector('iframe')!.dispatchEvent(new Event('load'))

    await expect(pendiente).rejects.toThrow(ErrorImpresion)
    await expect(pendiente).rejects.toThrow('El navegador no pudo abrir la impresión.')
    expect(document.querySelector('iframe')).toBeNull()
  })
})
