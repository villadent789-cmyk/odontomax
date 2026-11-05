import { useState } from 'react'

/**
 * Ajusta estado al renderizar cuando cambian las dependencias (patrón "ajustar estado durante el render" de React),
 * en lugar de un useEffect que llama a setState de forma síncrona y provoca un render extra con valores viejos.
 * Se ejecuta en el primer render y cada vez que alguna dependencia cambia (comparación Object.is). Pasa valores
 * primitivos o referencias estables: un array u objeto nuevo en cada render dispararía la acción sin parar.
 * `alCambiar` solo debe llamar a los setState del propio componente.
 */
export function useAlCambiar(dependencias: readonly unknown[], alCambiar: () => void) {
  const [previas, setPrevias] = useState<readonly unknown[] | null>(null)
  if (previas === null || dependencias.some((d, i) => !Object.is(d, previas[i]))) {
    setPrevias(dependencias)
    alCambiar()
  }
}
