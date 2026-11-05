import { useEffect, useState } from 'react'

/**
 * Marca de tiempo actual (ms) que se refresca cada `intervaloMs`. Sirve para cálculos como "días sin visita" sin
 * llamar a Date.now() durante el render (que no es puro) y manteniendo el valor al día si la pantalla queda abierta.
 */
export function useAhora(intervaloMs = 5 * 60_000): number {
  const [ahora, setAhora] = useState(() => Date.now())
  useEffect(() => {
    const temporizador = setInterval(() => setAhora(Date.now()), intervaloMs)
    return () => clearInterval(temporizador)
  }, [intervaloMs])
  return ahora
}
