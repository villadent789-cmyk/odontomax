/** Error de impresión con un mensaje apto para mostrar al usuario. */
export class ErrorImpresion extends Error {
  constructor(mensaje: string) {
    super(mensaje)
    this.name = 'ErrorImpresion'
  }
}

/**
 * Imprime un documento HTML completo (o lo guarda como PDF desde el diálogo de impresión del navegador) sin abrir
 * ventanas emergentes —que los navegadores bloquean—: lo carga en un marco oculto y llama a imprimir. Devuelve una
 * promesa que termina al abrirse el diálogo; si el navegador no puede imprimir el marco, rechaza.
 */
export function imprimirDocumento(html: string): Promise<void> {
  return new Promise((resolver, rechazar) => {
    const marco = document.createElement('iframe')
    marco.setAttribute('aria-hidden', 'true')
    marco.tabIndex = -1
    marco.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden'

    const limpiar = () => window.setTimeout(() => marco.remove(), 1000)

    const alCargar = () => {
      const ventana = marco.contentWindow
      if (!ventana) {
        marco.remove()
        rechazar(new ErrorImpresion('No se pudo preparar el documento para imprimir.'))
        return
      }
      ventana.addEventListener('afterprint', limpiar, { once: true })
      try {
        ventana.focus()
        ventana.print()
        resolver()
      } catch {
        marco.remove()
        rechazar(new ErrorImpresion('El navegador no pudo abrir la impresión.'))
        return
      }
      // Si el navegador no avisa al terminar de imprimir, el marco se retira igual.
      window.setTimeout(() => marco.remove(), 120_000)
    }

    // Una sola vez por documento, aunque el navegador vuelva a disparar "load".
    marco.addEventListener('load', alCargar, { once: true })
    marco.srcdoc = html
    document.body.appendChild(marco)
  })
}
