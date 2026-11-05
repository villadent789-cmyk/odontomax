import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'
import type {
  ActualizarOrdenPayload,
  AnularPagoLaboratorioPayload,
  CambioEstadoPayload,
  RegistrarPruebaPayload,
  CrearOrdenPayload,
  FiltrosLaboratorio,
  GuardarLaboratorioPayload,
  LaboratorioExterno,
  LaboratorioCuenta,
  OrdenLaboratorio,
  PagoLaboratorio,
  RegistrarPagoLaboratorioPayload,
} from './types'

/* ─────────── Endpoints API ─────────── */

export async function listarOrdenes(filtros: FiltrosLaboratorio = {}): Promise<OrdenLaboratorio[]> {
  const params = new URLSearchParams()
  if (filtros.sedeId) params.set('sedeId', filtros.sedeId)
  if (filtros.estado) params.set('estado', filtros.estado)
  if (filtros.pacienteId) params.set('pacienteId', filtros.pacienteId)
  if (filtros.especialistaId) params.set('especialistaId', filtros.especialistaId)

  const qs = params.toString()
  return api.get<OrdenLaboratorio[]>(qs ? `/laboratorio/ordenes?${qs}` : '/laboratorio/ordenes')
}

export async function obtenerOrden(id: string): Promise<OrdenLaboratorio> {
  return api.get<OrdenLaboratorio>(`/laboratorio/ordenes/${id}`)
}

export async function listarPagosLaboratorio(ordenId: string): Promise<PagoLaboratorio[]> {
  return api.get<PagoLaboratorio[]>(`/laboratorio/ordenes/${ordenId}/pagos`)
}

export async function registrarPagoLaboratorio(
  ordenId: string,
  payload: RegistrarPagoLaboratorioPayload,
): Promise<PagoLaboratorio> {
  return api.post<PagoLaboratorio>(`/laboratorio/ordenes/${ordenId}/pagos`, payload)
}

export async function anularPagoLaboratorio(
  pagoId: string,
  payload: AnularPagoLaboratorioPayload,
): Promise<PagoLaboratorio> {
  return api.post<PagoLaboratorio>(`/laboratorio/pagos/${pagoId}/anular`, payload)
}

export async function listarCuentasLaboratorios(filtros: { sedeId?: string; laboratorioId?: string } = {}) {
  const params = new URLSearchParams()
  if (filtros.sedeId) params.set('sedeId', filtros.sedeId)
  if (filtros.laboratorioId) params.set('laboratorioId', filtros.laboratorioId)
  const qs = params.toString()
  return api.get<LaboratorioCuenta[]>(qs ? `/laboratorio/cuentas?${qs}` : '/laboratorio/cuentas')
}

export async function crearOrden(payload: CrearOrdenPayload): Promise<OrdenLaboratorio> {
  return api.post<OrdenLaboratorio>('/laboratorio/ordenes', payload)
}

export async function actualizarOrden(id: string, payload: ActualizarOrdenPayload): Promise<OrdenLaboratorio> {
  return api.put<OrdenLaboratorio>(`/laboratorio/ordenes/${id}`, payload)
}

export async function registrarPruebaOrden(id: string, payload: RegistrarPruebaPayload): Promise<OrdenLaboratorio> {
  return api.post<OrdenLaboratorio>(`/laboratorio/ordenes/${id}/pruebas`, payload)
}

export async function cambiarEstadoOrden(id: string, payload: CambioEstadoPayload): Promise<OrdenLaboratorio> {
  return api.patch<OrdenLaboratorio>(`/laboratorio/ordenes/${id}/estado`, payload)
}

export async function listarProveedores(soloActivos = true): Promise<LaboratorioExterno[]> {
  return api.get<LaboratorioExterno[]>(`/laboratorio/proveedores?soloActivos=${soloActivos}`)
}

export async function crearProveedor(payload: GuardarLaboratorioPayload): Promise<LaboratorioExterno> {
  return api.post<LaboratorioExterno>('/laboratorio/proveedores', payload)
}

export async function actualizarProveedor(id: string, payload: GuardarLaboratorioPayload): Promise<LaboratorioExterno> {
  return api.put<LaboratorioExterno>(`/laboratorio/proveedores/${id}`, payload)
}

/* ─────────── Claves de React Query ─────────── */

export const LABORATORIO_KEYS = {
  ordenes: (filtros: FiltrosLaboratorio = {}) => ['laboratorio', 'ordenes', filtros] as const,
  orden: (id: string) => ['laboratorio', 'orden', id] as const,
  pagosOrden: (id: string) => ['laboratorio', 'orden', id, 'pagos'] as const,
  cuentas: (filtros: { sedeId?: string; laboratorioId?: string } = {}) =>
    ['laboratorio', 'cuentas', filtros] as const,
  proveedores: (soloActivos = true) => ['laboratorio', 'proveedores', soloActivos] as const,
}

/* ─────────── React Query Hooks ─────────── */

export function useOrdenesLaboratorio(filtros: FiltrosLaboratorio = {}) {
  return useQuery({
    queryKey: LABORATORIO_KEYS.ordenes(filtros),
    queryFn: () => listarOrdenes(filtros),
  })
}

export function useOrdenLaboratorio(id?: string) {
  return useQuery({
    queryKey: id ? LABORATORIO_KEYS.orden(id) : ['laboratorio', 'orden', 'none'],
    queryFn: () => (id ? obtenerOrden(id) : Promise.reject(new Error('ID requerido'))),
    enabled: Boolean(id),
  })
}

export function usePagosLaboratorio(ordenId?: string, enabled = true) {
  return useQuery({
    queryKey: ordenId ? LABORATORIO_KEYS.pagosOrden(ordenId) : ['laboratorio', 'pagos', 'none'],
    queryFn: () => (ordenId ? listarPagosLaboratorio(ordenId) : Promise.reject(new Error('ID requerido'))),
    enabled: Boolean(ordenId) && enabled,
  })
}

export function useCuentasLaboratorios(filtros: { sedeId?: string; laboratorioId?: string } = {}) {
  return useQuery({
    queryKey: LABORATORIO_KEYS.cuentas(filtros),
    queryFn: () => listarCuentasLaboratorios(filtros),
  })
}

export function useRegistrarPagoLaboratorioMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ ordenId, payload }: { ordenId: string; payload: RegistrarPagoLaboratorioPayload }) =>
      registrarPagoLaboratorio(ordenId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'ordenes'] })
      queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.orden(variables.ordenId) })
      queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.pagosOrden(variables.ordenId) })
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'cuentas'] })
      queryClient.invalidateQueries({ queryKey: ['caja'] })
      queryClient.invalidateQueries({ queryKey: ['reportes'] })
    },
  })
}

export function useAnularPagoLaboratorioMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ pagoId, payload }: { pagoId: string; payload: AnularPagoLaboratorioPayload; ordenId?: string }) =>
      anularPagoLaboratorio(pagoId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'ordenes'] })
      if (variables.ordenId) {
        queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.orden(variables.ordenId) })
        queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.pagosOrden(variables.ordenId) })
      } else {
        queryClient.invalidateQueries({ queryKey: ['laboratorio'] })
      }
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'cuentas'] })
      queryClient.invalidateQueries({ queryKey: ['caja'] })
      queryClient.invalidateQueries({ queryKey: ['reportes'] })
    },
  })
}

export function useProveedoresLaboratorio(soloActivos = true) {
  return useQuery({
    queryKey: LABORATORIO_KEYS.proveedores(soloActivos),
    queryFn: () => listarProveedores(soloActivos),
  })
}

export function useCrearOrdenMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CrearOrdenPayload) => crearOrden(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'ordenes'] })
    },
  })
}

export function useActualizarOrdenMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: ActualizarOrdenPayload }) =>
      actualizarOrden(id, payload),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'ordenes'] })
      queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.orden(vars.id) })
    },
  })
}

export function useRegistrarPruebaOrdenMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: RegistrarPruebaPayload }) =>
      registrarPruebaOrden(id, payload),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'ordenes'] })
      queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.orden(vars.id) })
    },
  })
}

export function useCambiarEstadoOrdenMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: CambioEstadoPayload }) =>
      cambiarEstadoOrden(id, payload),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'ordenes'] })
      queryClient.invalidateQueries({ queryKey: LABORATORIO_KEYS.orden(vars.id) })
    },
  })
}

export function useCrearProveedorMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: GuardarLaboratorioPayload) => crearProveedor(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'proveedores'] })
    },
  })
}

export function useActualizarProveedorMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: GuardarLaboratorioPayload }) =>
      actualizarProveedor(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['laboratorio', 'proveedores'] })
    },
  })
}
