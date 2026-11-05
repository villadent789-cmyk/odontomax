/** Iniciales para el avatar: primera letra del primer y del último nombre. */
export function iniciales(nombre: string): string {
  // Solo palabras que empiezan con letra (ignora "(demo)", guiones, etc.).
  const partes = nombre
    .trim()
    .split(/\s+/)
    .filter((p) => /^\p{L}/u.test(p))
  if (partes.length === 0) return '?'
  const primera = partes[0][0] ?? ''
  const ultima = partes.length > 1 ? (partes[partes.length - 1][0] ?? '') : ''
  return (primera + ultima).toLocaleUpperCase('es-PE')
}
