/**
 * En celular las tablas se muestran como tarjetas (una por fila). El CSS necesita saber el nombre de cada columna:
 * aquí se copia el texto del encabezado a `data-etiqueta` en cada celda. Las filas con celdas combinadas
 * (mensajes de "sin datos") y las tablas marcadas con `sin-apilar` (cuadrículas como la agenda) no se tocan.
 */
export function etiquetarTablas(raiz: ParentNode): void {
  raiz.querySelectorAll<HTMLTableElement>('table:not(.sin-apilar)').forEach((tabla) => {
    const cabeceras = Array.from(tabla.querySelectorAll('thead th')).map((th) => (th.textContent ?? '').trim())
    if (cabeceras.length === 0) return
    tabla.querySelectorAll('tbody tr').forEach((fila) => {
      const celdas = Array.from(fila.children).filter((c): c is HTMLTableCellElement => c.tagName === 'TD' || c.tagName === 'TH')
      if (celdas.some((c) => c.colSpan > 1)) return
      celdas.forEach((celda, i) => {
        const etiqueta = cabeceras[i]
        if (etiqueta && celda.dataset.etiqueta !== etiqueta) celda.dataset.etiqueta = etiqueta
      })
    })
  })
}
