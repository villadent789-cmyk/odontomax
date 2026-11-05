import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'
import { apiTipada, type Esquema } from '@/shared/api'

export type TratamientoDTO = Esquema<'TratamientoResponseDTO'>
export type TratamientoItemDTO = Esquema<'TratamientoItemResponseDTO'>
export type TratamientoCreateRequestDTO = Esquema<'TratamientoCreateRequestDTO'>
export type TratamientoItemRequestDTO = Esquema<'TratamientoItemRequestDTO'>
export type TratamientoAceptacionRequestDTO = Esquema<'TratamientoAceptacionRequestDTO'>
export type TratamientoUpdateRequestDTO = Esquema<'TratamientoUpdateRequestDTO'>
export type ServicioCatalogo = Esquema<'ServicioResponseDTO'>

/* ─────────── Consultas HTTP ─────────── */

export async function listarTratamientosPorPaciente(pacienteId: string): Promise<TratamientoDTO[]> {
  return api.get<TratamientoDTO[]>(`/tratamientos/paciente/${encodeURIComponent(pacienteId)}`)
}

export async function listarTratamientosPorSede(sedeId: string): Promise<TratamientoDTO[]> {
  return api.get<TratamientoDTO[]>(`/tratamientos/sede/${encodeURIComponent(sedeId)}`)
}

export async function obtenerTratamiento(id: string): Promise<TratamientoDTO> {
  return api.get<TratamientoDTO>(`/tratamientos/${encodeURIComponent(id)}`)
}

export async function crearTratamiento(dto: TratamientoCreateRequestDTO): Promise<TratamientoDTO> {
  return api.post<TratamientoDTO>('/tratamientos', dto)
}

export async function aceptarTratamiento(
  id: string,
  dto: TratamientoAceptacionRequestDTO
): Promise<TratamientoDTO> {
  return api.post<TratamientoDTO>(`/tratamientos/${encodeURIComponent(id)}/aceptar`, dto)
}

/** PATCH /tratamientos/{id}: estado, sesiones realizadas, próximo control y observaciones (todos opcionales). */
export async function actualizarTratamiento(id: string, dto: TratamientoUpdateRequestDTO): Promise<TratamientoDTO> {
  return api.patch<TratamientoDTO>(`/tratamientos/${encodeURIComponent(id)}`, dto)
}

export async function eliminarTratamiento(id: string): Promise<void> {
  return api.delete<void>(`/tratamientos/${encodeURIComponent(id)}`)
}

/* ─────────── Claves de React Query ─────────── */

export const CLAVES_TRATAMIENTOS = {
  paciente: (pacienteId: string) => ['tratamientos', 'paciente', pacienteId] as const,
  sede: (sedeId: string) => ['tratamientos', 'sede', sedeId] as const,
  detalle: (id: string) => ['tratamientos', 'detalle', id] as const,
}

/* ─────────── React Query Hooks ─────────── */

export function useTratamientosPaciente(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_TRATAMIENTOS.paciente(pacienteId ?? ''),
    queryFn: () => listarTratamientosPorPaciente(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

/** Paciente con hallazgos por tratar en el odontograma y sin plan ni presupuesto en marcha (GET /tratamientos/pacientes-sin-plan). */
export interface PacienteSinPlan {
  pacienteId: string
  pacienteNombre: string
  telefono?: string
  hallazgos: number
  tipos: string[]
}

/** Pacientes sin plan de varias sedes a la vez (el administrador general con «Todas»): cada uno lleva su sede. */
export function usePacientesSinPlan(sedeIds: string[]) {
  return useQueries({
    queries: sedeIds.map((sedeId) => ({
      queryKey: ['tratamientos', 'sin-plan', sedeId] as const,
      queryFn: () => api.get<PacienteSinPlan[]>(`/tratamientos/pacientes-sin-plan?sedeId=${encodeURIComponent(sedeId)}`),
    })),
    combine: (resultados) => ({
      isPending: resultados.some((r) => r.isPending),
      data: resultados
        .flatMap((r, i) => (r.data ?? []).map((p) => ({ ...p, sedeId: sedeIds[i] })))
        .sort((a, c) => c.hallazgos - a.hallazgos),
    }),
  })
}

/** Planes de varias sedes a la vez (el administrador general con «Todas»). */
export function useTratamientosDeSedes(sedeIds: string[]) {
  return useQueries({
    queries: sedeIds.map((sedeId) => ({
      queryKey: CLAVES_TRATAMIENTOS.sede(sedeId),
      queryFn: () => listarTratamientosPorSede(sedeId),
    })),
    combine: (resultados) => ({
      isPending: resultados.some((r) => r.isPending),
      data: resultados.flatMap((r, i) => (r.data ?? []).map((t) => ({ ...t, sedeId: sedeIds[i] }))),
    }),
  })
}

export function useTratamientosSede(sedeId?: string) {
  return useQuery({
    queryKey: CLAVES_TRATAMIENTOS.sede(sedeId ?? ''),
    queryFn: () => listarTratamientosPorSede(sedeId!),
    enabled: Boolean(sedeId),
  })
}

export function useCrearTratamientoMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: crearTratamiento,
    onSuccess: (nuevo) => {
      queryClient.invalidateQueries({ queryKey: ['tratamientos'] })
      queryClient.invalidateQueries({ queryKey: ['pacientes'] })
      queryClient.invalidateQueries({ queryKey: ['citas'] })
      queryClient.invalidateQueries({ queryKey: ['reportes'] })
      if (nuevo.pacienteId) {
        queryClient.invalidateQueries({ queryKey: CLAVES_TRATAMIENTOS.paciente(nuevo.pacienteId) })
        queryClient.invalidateQueries({ queryKey: ['pacientes', 'detalle', nuevo.pacienteId] })
      }
    },
  })
}

/** Avanzar sesiones o cambiar el estado de un plan. Refresca los planes del paciente y los datos que dependen de ellos. */
export function useActualizarTratamientoMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: TratamientoUpdateRequestDTO }) => actualizarTratamiento(id, dto),
    onSuccess: (actualizado) => {
      queryClient.invalidateQueries({ queryKey: ['tratamientos'] })
      queryClient.invalidateQueries({ queryKey: ['pacientes'] })
      queryClient.invalidateQueries({ queryKey: ['citas'] })
      queryClient.invalidateQueries({ queryKey: ['reportes'] })
      if (actualizado.pacienteId) {
        queryClient.invalidateQueries({ queryKey: CLAVES_TRATAMIENTOS.paciente(actualizado.pacienteId) })
        queryClient.invalidateQueries({ queryKey: ['pacientes', 'detalle', actualizado.pacienteId] })
      }
    },
  })
}

/**
 * GET /servicios: catálogo de procedimientos para armar el presupuesto. OJO: `precio` sale de precio_desde, que la
 * migración V19 dejó en NULL; hasta que exista un precio interno, el catálogo autocompleta el nombre y el precio se
 * escribe a mano (docs/frontend/brechas-backend.md).
 */
export function useTratamiento(id?: string) {
  return useQuery({
    queryKey: ['tratamientos', id ?? ''],
    queryFn: () => obtenerTratamiento(id!),
    enabled: Boolean(id),
  })
}

export function useServiciosCatalogo(sedeId?: string, habilitado = true, negocio?: string) {
  return useQuery({
    queryKey: ['servicios', 'catalogo', sedeId ?? null, negocio ?? null],
    queryFn: () => apiTipada.get('/servicios', { query: { sedeId, negocio: negocio || undefined } }),
    enabled: habilitado,
    staleTime: 10_000,
    select: (lista) => lista.filter((s) => s.activo !== false && s.nombre).sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0)),
  })
}

export function useAceptarTratamientoMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, dto }: { id: string; dto: TratamientoAceptacionRequestDTO }) =>
      aceptarTratamiento(id, dto),
    onSuccess: (actualizado) => {
      queryClient.invalidateQueries({ queryKey: ['tratamientos'] })
      queryClient.invalidateQueries({ queryKey: ['pacientes'] })
      queryClient.invalidateQueries({ queryKey: ['citas'] })
      queryClient.invalidateQueries({ queryKey: ['reportes'] })
      if (actualizado.pacienteId) {
        queryClient.invalidateQueries({ queryKey: CLAVES_TRATAMIENTOS.paciente(actualizado.pacienteId) })
        queryClient.invalidateQueries({ queryKey: ['pacientes', 'detalle', actualizado.pacienteId] })
      }
    },
  })
}
