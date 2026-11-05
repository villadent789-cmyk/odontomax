/**
 * Ref callback que da el foco al elemento cuando se monta (reemplaza al atributo autoFocus, que jsx-a11y
 * desaconseja). Es una función estable de módulo: React la llama una sola vez al montar el elemento, no en cada
 * render, así que no le roba el foco al usuario mientras escribe. Úsalo solo en el campo principal de un diálogo.
 */
export function enfocarAlMontar(elemento: HTMLElement | null) {
  elemento?.focus()
}
