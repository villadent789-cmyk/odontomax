/** Medios de pago que acepta caja. Las tarjetas de débito y de crédito se registran por separado. */
export type MetodoPago = 'EFECTIVO' | 'YAPE' | 'PLIN' | 'TARJETA_DEBITO' | 'TARJETA_CREDITO'

export const METODOS_PAGO: { valor: MetodoPago; nombre: string }[] = [
  { valor: 'EFECTIVO', nombre: 'Efectivo' },
  { valor: 'YAPE', nombre: 'Yape' },
  { valor: 'PLIN', nombre: 'Plin' },
  { valor: 'TARJETA_DEBITO', nombre: 'Tarjeta de débito' },
  { valor: 'TARJETA_CREDITO', nombre: 'Tarjeta de crédito' },
]

const NOMBRES: Record<string, string> = {
  ...Object.fromEntries(METODOS_PAGO.map((m) => [m.valor, m.nombre])),
  TARJETA: 'Tarjeta',
  TRANSFERENCIA: 'Transferencia',
}

/** Nombre legible del medio de pago tal como llega del servidor. */
export function nombreMetodoPago(metodo?: string | null): string {
  if (!metodo) return '—'
  return NOMBRES[metodo.toUpperCase()] ?? metodo
}
