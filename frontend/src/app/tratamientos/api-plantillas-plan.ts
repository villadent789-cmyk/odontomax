import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'
import type { Gravedad } from './api-presupuestos'

/** Un plan que se repite: servicio del catálogo, piezas, gravedad y sesiones. El precio sale del catálogo al usarla. */
export interface PlantillaPlan {
  id?: string
  nombre: string
  procedimientoId: string
  servicioNombre: string
  piezas: number
  gravedad?: Gravedad
  sesiones: number
  notas?: string
}

const CLAVE = ['plantillas-plan'] as const

export function usePlantillasPlan(habilitado = true) {
  return useQuery({
    queryKey: CLAVE,
    queryFn: () => api.get<PlantillaPlan[]>('/plantillas-plan'),
    enabled: habilitado,
  })
}

export function useCrearPlantillaPlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (p: PlantillaPlan) => api.post<PlantillaPlan>('/plantillas-plan', p),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVE }),
  })
}

export function useQuitarPlantillaPlan() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`/plantillas-plan/${encodeURIComponent(id)}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVE }),
  })
}
