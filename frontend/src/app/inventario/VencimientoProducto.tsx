import { Badge } from '@/design-system/components/Badge'
import { formatFecha, hoyEnLima } from '@/shared/format'

const DIAS_DE_AVISO = 30

function diasHasta(fecha: string, hoy: string): number {
  const aUtc = (f: string) => {
    const [anio, mes, dia] = f.split('-').map(Number)
    return Date.UTC(anio, mes - 1, dia)
  }
  return Math.round((aUtc(fecha) - aUtc(hoy)) / 86_400_000)
}

/** Próximo vencimiento de un producto: resalta lo vencido o por vencer en 30 días o menos. */
export function VencimientoProducto({ fecha }: { fecha?: string | null }) {
  if (!fecha) return <span className="text-on-surface-variant">—</span>
  const dias = diasHasta(fecha, hoyEnLima())
  const tono = dias < 0 ? 'peligro' : dias <= DIAS_DE_AVISO ? 'accion' : 'neutro'
  const aviso = dias < 0 ? 'vencido' : dias === 0 ? 'vence hoy' : dias <= DIAS_DE_AVISO ? `en ${dias} d` : ''
  return (
    <Badge tono={tono} punto>
      {formatFecha(fecha)}
      {aviso && ` · ${aviso}`}
    </Badge>
  )
}
