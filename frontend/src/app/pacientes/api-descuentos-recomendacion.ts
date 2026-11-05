import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

/** Descuento pendiente de quien recomendó a un paciente nuevo; el monto lo decide el doctor. */
export interface DescuentoRecomendacion {
  id: string
  referidoId: string
  referidoNombre?: string
  estado: 'PENDIENTE' | 'APLICADO'
  creadoEn: string
  aplicadoEn?: string
  aplicadoPor?: string
  tratamientoId?: string
}

const clave = (pacienteId: string) => ['pacientes', 'descuentos-recomendacion', pacienteId] as const

export function useDescuentosRecomendacion(pacienteId?: string, habilitado = true) {
  return useQuery({
    queryKey: clave(pacienteId ?? ''),
    queryFn: () => api.get<DescuentoRecomendacion[]>(`/pacientes/${encodeURIComponent(pacienteId!)}/descuentos-recomendacion`),
    enabled: Boolean(pacienteId) && habilitado,
  })
}

export function useAplicarDescuentoRecomendacion(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ descuentoId, tratamientoId }: { descuentoId: string; tratamientoId?: string }) =>
      api.post<DescuentoRecomendacion>(
        `/pacientes/${encodeURIComponent(pacienteId)}/descuentos-recomendacion/${encodeURIComponent(descuentoId)}/aplicar`,
        { tratamientoId },
      ),
    onSuccess: () => qc.invalidateQueries({ queryKey: clave(pacienteId) }),
  })
}
