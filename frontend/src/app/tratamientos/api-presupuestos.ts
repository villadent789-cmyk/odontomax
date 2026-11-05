import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

export type Gravedad = 'LEVE' | 'MODERADO' | 'SEVERO'

export interface ItemPresupuesto {
  id: string
  procedimientoId?: string
  nombre: string
  piezaFdi?: number
  gravedad?: Gravedad
  cantidad: number
  precioUnitario: number
  subtotal: number
  aceptado: boolean
}

/** Lo que se puede hacer por un paciente: piezas × costo por pieza; el paciente acepta todo o una parte. */
export interface Presupuesto {
  id: string
  pacienteId: string
  sedeId: string
  especialistaId?: string
  especialistaNombre?: string
  motivo?: string
  notas?: string
  estado: 'VIGENTE' | 'ACEPTADO' | 'RECHAZADO'
  total: number
  totalAceptado: number
  tratamientoId?: string
  creadoPor?: string
  creadoEn: string
  resueltoEn?: string
  items: ItemPresupuesto[]
}

export interface NuevoPresupuesto {
  pacienteId: string
  sedeId: string
  especialistaId?: string
  motivo?: string
  notas?: string
  items: { procedimientoId?: string; nombre: string; piezaFdi?: number; gravedad?: Gravedad; cantidad: number; precioUnitario: number }[]
}

/** Presupuesto vigente de una sede con el nombre del paciente (GET /presupuestos/pendientes). */
export interface PresupuestoPendiente {
  id: string
  pacienteId: string
  pacienteNombre?: string
  especialistaNombre?: string
  motivo?: string
  total: number
  lineas: number
  creadoEn: string
}

const clave = (pacienteId: string) => ['presupuestos', pacienteId] as const

/** Pendientes de varias sedes a la vez (el administrador general con «Todas»): cada uno lleva su sede. */
export function usePresupuestosPendientes(sedeIds: string[]) {
  return useQueries({
    queries: sedeIds.map((sedeId) => ({
      queryKey: ['presupuestos', 'pendientes', sedeId] as const,
      queryFn: () => api.get<PresupuestoPendiente[]>(`/presupuestos/pendientes?sedeId=${encodeURIComponent(sedeId)}`),
    })),
    combine: (resultados) => ({
      isPending: resultados.some((r) => r.isPending),
      data: resultados
        .flatMap((r, i) => (r.data ?? []).map((p) => ({ ...p, sedeId: sedeIds[i] })))
        .sort((a, c) => c.creadoEn.localeCompare(a.creadoEn)),
    }),
  })
}

export function usePresupuestosPaciente(pacienteId?: string) {
  return useQuery({
    queryKey: clave(pacienteId ?? ''),
    queryFn: () => api.get<Presupuesto[]>(`/presupuestos/paciente/${encodeURIComponent(pacienteId!)}`),
    enabled: Boolean(pacienteId),
  })
}

export function useCrearPresupuesto(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (p: NuevoPresupuesto) => api.post<Presupuesto>('/presupuestos', p),
    onSuccess: () => qc.invalidateQueries({ queryKey: clave(pacienteId) }),
  })
}

export function useAceptarPresupuesto(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, itemIds, especialistaId, totalSesiones }: { id: string; itemIds?: string[]; especialistaId?: string; totalSesiones?: number }) =>
      api.post<Presupuesto>(`/presupuestos/${encodeURIComponent(id)}/aceptar`, { itemIds, especialistaId, totalSesiones }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: clave(pacienteId) })
      void qc.invalidateQueries({ queryKey: ['tratamientos'] })
    },
  })
}

export function useRechazarPresupuesto(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.post<Presupuesto>(`/presupuestos/${encodeURIComponent(id)}/rechazar`),
    onSuccess: () => qc.invalidateQueries({ queryKey: clave(pacienteId) }),
  })
}
