import { useEffect, useState } from 'react'

/** Indica si la pantalla cumple una condición CSS (p. ej. celular). Sin `matchMedia` (pruebas) devuelve `false`. */
export function useMediaQuery(consulta: string): boolean {
  const [coincide, setCoincide] = useState(() => window.matchMedia?.(consulta).matches ?? false)

  useEffect(() => {
    const lista = window.matchMedia?.(consulta)
    if (!lista) return
    const alCambiar = () => setCoincide(lista.matches)
    alCambiar()
    lista.addEventListener('change', alCambiar)
    return () => lista.removeEventListener('change', alCambiar)
  }, [consulta])

  return coincide
}
