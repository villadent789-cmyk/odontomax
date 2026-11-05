import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Esquema } from '@/shared/api'
import { api } from '@/shared/http'

export type EsperaHueco = Esquema<'EsperaHuecoResponseDTO'>
export type EsperaHuecoRequest = Esquema<'EsperaHuecoRequestDTO'>

const CLAVE = ['agenda', 'espera-hueco'] as const

/** Pacientes que esperan un hueco en la agenda de la sede (no confundir con la sala de espera del día). */
export function useEsperaDeHuecos(sedeId: string | null) {
  return useQuery({
    queryKey: [...CLAVE, sedeId ?? ''],
    queryFn: () => api.get<EsperaHueco[]>(`/citas/espera-hueco?sedeId=${encodeURIComponent(sedeId ?? '')}`),
    enabled: Boolean(sedeId),
  })
}

function useInvalidarEspera() {
  const qc = useQueryClient()
  return async () => {
    await qc.invalidateQueries({ queryKey: CLAVE })
    // los huecos sugeridos viajan en la bandeja de tareas
    await qc.invalidateQueries({ queryKey: ['citas'] })
  }
}

export function useAgregarEspera() {
  const invalidar = useInvalidarEspera()
  return useMutation({
    mutationFn: (cuerpo: EsperaHuecoRequest) => api.post<EsperaHueco>('/citas/espera-hueco', cuerpo),
    onSuccess: invalidar,
  })
}

export function useCambiarEstadoEspera() {
  const invalidar = useInvalidarEspera()
  return useMutation({
    mutationFn: ({ id, estado }: { id: string; estado: 'OFRECIDA' | 'ACTIVA' | 'AGENDADA' | 'CANCELADA' }) =>
      api.patch<EsperaHueco>(`/citas/espera-hueco/${encodeURIComponent(id)}/estado`, { estado }),
    onSuccess: invalidar,
  })
}
