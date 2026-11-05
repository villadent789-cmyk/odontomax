import { formatSoles } from './format'

export function esSaldoAFavor(saldo: number | null | undefined): boolean {
  return (saldo ?? 0) < 0
}

export function formatoSaldo(saldo: number | null | undefined): string {
  const importe = saldo ?? 0
  return importe < 0 ? `Saldo a favor ${formatSoles(Math.abs(importe))}` : formatSoles(importe)
}
