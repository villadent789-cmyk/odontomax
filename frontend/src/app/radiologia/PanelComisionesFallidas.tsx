import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/design-system'
import { api } from '@/shared/http'

interface ComisionFallida {
  citaId: string
  pacienteNombre?: string
  estudio?: string
  motivo: string
  intentos?: number
}

/** Estudios cobrados cuya comisión no se pudo generar: el administrador general los ve y los reintenta. */
export function PanelComisionesFallidas({ onReintentado }: { onReintentado: () => Promise<void> | void }) {
  const qc = useQueryClient()
  const fallidas = useQuery({
    queryKey: ['radiologia', 'comisiones', 'fallidas'],
    queryFn: () => api.get<ComisionFallida[]>('/radiologia/comisiones/fallidas'),
  })
  const reintentar = useMutation({
    mutationFn: (citaId: string) => api.post(`/citas/${encodeURIComponent(citaId)}/reintentar-comision`, {}),
    onSettled: async () => {
      await qc.invalidateQueries({ queryKey: ['radiologia', 'comisiones', 'fallidas'] })
      await onReintentado()
    },
  })

  const lista = fallidas.data ?? []
  if (lista.length === 0) return null
  return (
    <section role="alert" aria-label="Comisiones pendientes de generar" className="flex flex-col gap-2 rounded-card border border-error/40 bg-error-container/40 p-4">
      <p className="flex items-center gap-2 text-sm font-semibold text-on-error-container">
        <AlertTriangle aria-hidden className="size-4" />
        {lista.length === 1 ? '1 comisión no se pudo generar al cobrar' : `${lista.length} comisiones no se pudieron generar al cobrar`}
      </p>
      <ul className="flex flex-col gap-2">
        {lista.map((f) => (
          <li key={f.citaId} className="flex flex-wrap items-center justify-between gap-2 text-xs text-on-surface">
            <span>
              <strong>{f.pacienteNombre}</strong> · {f.estudio} · {f.motivo}
              {f.intentos && f.intentos > 1 ? ` (${f.intentos} intentos)` : ''}
            </span>
            <Button size="sm" variant="secundario" disabled={reintentar.isPending} onClick={() => reintentar.mutate(f.citaId)}>
              Reintentar
            </Button>
          </li>
        ))}
      </ul>
    </section>
  )
}
