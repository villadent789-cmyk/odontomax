import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

/** Estado de una pieza en el momento de una captura del odontograma. */
export interface PiezaInstantanea {
  piezaFdi: number
  estado: string
  caras?: string[]
  observacion?: string
}

/** Un episodio de atención: odontograma inicial al llegar y, tras el alta, el final. */
export interface EpisodioOdontograma {
  id: string
  pacienteId: string
  numero: number
  motivo?: string
  estado: 'ABIERTO' | 'CERRADO'
  inicialEn: string
  inicialPor?: string
  inicial: PiezaInstantanea[]
  finalEn?: string
  finalPor?: string
  finalPiezas?: PiezaInstantanea[]
  notaFinal?: string
}

export const CLAVES_EPISODIOS = {
  paciente: (pacienteId: string) => ['odontograma', 'episodios', pacienteId] as const,
}

const ruta = (pacienteId: string) => `/odontograma/pacientes/${encodeURIComponent(pacienteId)}/episodios`

export function useEpisodiosOdontograma(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_EPISODIOS.paciente(pacienteId ?? ''),
    queryFn: () => api.get<EpisodioOdontograma[]>(ruta(pacienteId!)),
    enabled: Boolean(pacienteId),
  })
}

export function useAbrirEpisodio(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (motivo?: string) => api.post<EpisodioOdontograma>(ruta(pacienteId), { motivo }),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVES_EPISODIOS.paciente(pacienteId) }),
  })
}

export function useCerrarEpisodio(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ episodioId, nota }: { episodioId: string; nota?: string }) =>
      api.post<EpisodioOdontograma>(`${ruta(pacienteId)}/${encodeURIComponent(episodioId)}/cerrar`, { nota }),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVES_EPISODIOS.paciente(pacienteId) }),
  })
}
