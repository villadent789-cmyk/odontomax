import { Badge } from '@/design-system'
import { formatFecha, formatSoles } from '@/shared/format'

interface CuotaVista {
  numero?: number
  concepto?: string
  venceEn?: string
  monto?: number
  pagado?: number
  saldo?: number
  estado?: string
}

const TONO: Record<string, 'primario' | 'accion' | 'peligro' | 'neutro'> = {
  PAGADA: 'primario',
  PARCIAL: 'accion',
  VENCIDA: 'peligro',
  PENDIENTE: 'neutro',
}

const ETIQUETA: Record<string, string> = {
  PAGADA: 'Pagada',
  PARCIAL: 'Pago parcial',
  VENCIDA: 'Vencida',
  PENDIENTE: 'Pendiente',
}

/** Cronograma de un plan en cuotas: lo pagado se reparte en orden entre las cuotas más antiguas. */
export function CronogramaCuotas({ cuotas, vencidas }: { cuotas?: CuotaVista[]; vencidas?: number }) {
  if (!cuotas || cuotas.length === 0) return null
  return (
    <div className="flex flex-col gap-2 rounded-panel border border-outline-variant/70 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-on-surface">Cronograma de cuotas</span>
        {vencidas ? <Badge tono="peligro">{vencidas === 1 ? '1 cuota vencida' : `${vencidas} cuotas vencidas`}</Badge> : null}
      </div>
      <table className="w-full text-left text-xs text-on-surface">
        <thead className="text-xs text-on-surface-variant">
          <tr>
            <th className="py-1 pr-2 font-medium">Cuota</th>
            <th className="py-1 pr-2 font-medium">Vence</th>
            <th className="py-1 pr-2 text-right font-medium">Monto</th>
            <th className="py-1 pr-2 text-right font-medium">Pagado</th>
            <th className="py-1 text-right font-medium">Estado</th>
          </tr>
        </thead>
        <tbody>
          {cuotas.map((c) => (
            <tr key={c.numero} className="border-t border-outline-variant/40">
              <td className="py-1.5 pr-2">{c.concepto}</td>
              <td className="py-1.5 pr-2">{c.venceEn ? formatFecha(c.venceEn) : '—'}</td>
              <td className="py-1.5 pr-2 text-right">{formatSoles(c.monto ?? 0)}</td>
              <td className="py-1.5 pr-2 text-right">{formatSoles(c.pagado ?? 0)}</td>
              <td className="py-1.5 text-right">
                <Badge tono={TONO[c.estado ?? 'PENDIENTE'] ?? 'neutro'}>{ETIQUETA[c.estado ?? 'PENDIENTE'] ?? c.estado}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
