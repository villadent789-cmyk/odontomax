/**
 * Comprueba que el valor de un <select> (siempre string) sea una de las opciones conocidas y lo estrecha a ese tipo,
 * para no forzar el tipo con un cast cuando se guarda en un estado con una unión de literales.
 */
export function esOpcion<T extends string>(opciones: readonly T[], valor: string): valor is T {
  return (opciones as readonly string[]).includes(valor)
}
