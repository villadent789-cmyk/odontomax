import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'
import type { Esquema } from '@/shared/api'
import type { MetodoPago } from '@/shared/metodosPago'

export type CajaSesionDTO = Esquema<'CajaSesionResponseDTO'>
export type CajaMovimientoDTO = Esquema<'CajaMovimientoResponseDTO'>
export type CajaAperturaRequestDTO = Esquema<'CajaAperturaRequestDTO'>
export type CajaCierreRequestDTO = Esquema<'CajaCierreRequestDTO'>
export type CajaMovimientoRequestDTO = Esquema<'CajaMovimientoRequestDTO'>

export interface PagoDTO {
  id: string
  sedeId: string
  sedeNombre?: string
  pacienteId: string
  pacienteNombre?: string
  pacienteDni?: string
  tratamientoId?: string
  tratamientoNombre?: string
  monto: number
  metodoPago: MetodoPago
  numeroOperacion?: string
  comprobante: string
  comprobanteTipo: string
  comprobanteSerie?: string
  comprobanteNumero?: string
  comprobanteHash?: string
  cajaSesionId?: string
  especialistaId?: string
  especialistaNombre?: string
  pagadoEn: string
  registradoPor: string
  registradoPorNombre?: string
  anulado?: boolean
  anuladoEn?: string
  anuladoPor?: string
  anuladoPorNombre?: string
  motivoAnulacion?: string
  creadoEn: string
}

export interface PagoCreateRequestDTO {
  pacienteId: string
  tratamientoId?: string
  sedeId: string
  monto: number
  metodoPago: MetodoPago
  numeroOperacion?: string
  comprobanteTipo?: string
  cajaSesionId?: string
  especialistaId?: string
}

export interface GastoDTO {
  id: string
  sedeId: string
  sedeNombre?: string
  categoria: string
  concepto: string
  monto: number
  metodoPago: string
  comprobante?: string
  comprobanteKey?: string
  cajaSesionId?: string
  creadoEn: string
  registradoPor: string
  registradoPorNombre?: string
  anulado?: boolean
  anuladoEn?: string
  anuladoPor?: string
  anuladoPorNombre?: string
  motivoAnulacion?: string
}

export interface GastoCreateRequestDTO {
  sedeId: string
  categoria: string
  concepto: string
  monto: number
  metodoPago?: string
  comprobante?: string
  comprobanteKey?: string
  cajaSesionId?: string
}

export type CajaResumenDiarioDTO = Esquema<'CajaResumenDiarioDTO'>

/* ─────────── Consultas HTTP ─────────── */

export async function obtenerCajaActiva(sedeId?: string): Promise<CajaSesionDTO | null> {
  const url = sedeId ? `/caja/activa?sedeId=${encodeURIComponent(sedeId)}` : '/caja/activa'
  const data = await api.get<CajaSesionDTO | undefined>(url)
  return data ?? null
}

export async function abrirCaja(dto: CajaAperturaRequestDTO): Promise<CajaSesionDTO> {
  return api.post<CajaSesionDTO>('/caja/abrir', dto)
}

export async function registrarMovimiento(
  sesionId: string,
  dto: CajaMovimientoRequestDTO
): Promise<CajaMovimientoDTO> {
  return api.post<CajaMovimientoDTO>(`/caja/${sesionId}/movimientos`, dto)
}

export async function cerrarCaja(sesionId: string, dto: CajaCierreRequestDTO): Promise<CajaSesionDTO> {
  return api.post<CajaSesionDTO>(`/caja/${sesionId}/cerrar`, dto)
}

export async function listarHistorialCajas(sedeId: string): Promise<CajaSesionDTO[]> {
  return api.get<CajaSesionDTO[]>(`/caja/historial?sedeId=${encodeURIComponent(sedeId)}`)
}

export async function obtenerResumenDiario(sedeId: string, fecha?: string): Promise<CajaResumenDiarioDTO> {
  const params = new URLSearchParams({ sedeId })
  if (fecha) params.set('fecha', fecha)
  return api.get<CajaResumenDiarioDTO>(`/caja/resumen-diario?${params.toString()}`)
}

export async function registrarPago(dto: PagoCreateRequestDTO): Promise<PagoDTO> {
  return api.post<PagoDTO>('/pagos', dto)
}

export async function listarPagosPorSede(sedeId: string): Promise<PagoDTO[]> {
  return api.get<PagoDTO[]>(`/pagos/sede/${encodeURIComponent(sedeId)}`)
}

export async function listarPagosPorPaciente(pacienteId: string): Promise<PagoDTO[]> {
  return api.get<PagoDTO[]>(`/pagos/paciente/${encodeURIComponent(pacienteId)}`)
}

export async function anularPago(id: string, motivo: string): Promise<PagoDTO> {
  return api.post<PagoDTO>(`/pagos/${id}/anular`, { motivoAnulacion: motivo })
}

export async function listarGastosPorSede(sedeId: string): Promise<GastoDTO[]> {
  return api.get<GastoDTO[]>(`/gastos?sedeId=${encodeURIComponent(sedeId)}`)
}

export async function registrarGasto(dto: GastoCreateRequestDTO): Promise<GastoDTO> {
  return api.post<GastoDTO>('/gastos', dto)
}

export async function anularGasto(id: string, motivo: string): Promise<GastoDTO> {
  return api.post<GastoDTO>(`/gastos/${id}/anular`, { motivoAnulacion: motivo })
}

/* ─────────── Claves de React Query ─────────── */

export const CLAVES_CAJA = {
  activa: (sedeId?: string) => ['caja', 'activa', sedeId] as const,
  historial: (sedeId: string) => ['caja', 'historial', sedeId] as const,
  resumenDiario: (sedeId: string, fecha?: string) => ['caja', 'resumen-diario', sedeId, fecha] as const,
  pagosSede: (sedeId: string) => ['pagos', 'sede', sedeId] as const,
  pagosPaciente: (pacienteId: string) => ['pagos', 'paciente', pacienteId] as const,
  gastosSede: (sedeId: string) => ['gastos', 'sede', sedeId] as const,
}

/* ─────────── React Query Hooks ─────────── */

export function useCajaActiva(sedeId?: string, habilitado = true) {
  return useQuery({
    queryKey: CLAVES_CAJA.activa(sedeId),
    queryFn: () => obtenerCajaActiva(sedeId),
    enabled: Boolean(sedeId) && habilitado,
    refetchInterval: import.meta.env.MODE === 'test' ? false : 10_000,
  })
}

export function useHistorialCajas(sedeId?: string) {
  return useQuery({
    queryKey: CLAVES_CAJA.historial(sedeId ?? ''),
    queryFn: () => listarHistorialCajas(sedeId!),
    enabled: Boolean(sedeId),
  })
}

export function useCajaResumenDiario(sedeId?: string, fecha?: string) {
  return useQuery({
    queryKey: CLAVES_CAJA.resumenDiario(sedeId ?? '', fecha),
    queryFn: () => obtenerResumenDiario(sedeId!, fecha),
    enabled: Boolean(sedeId),
  })
}

export function usePagosSede(sedeId?: string) {
  return useQuery({
    queryKey: CLAVES_CAJA.pagosSede(sedeId ?? ''),
    queryFn: () => listarPagosPorSede(sedeId!),
    enabled: Boolean(sedeId),
    refetchInterval: import.meta.env.MODE === 'test' ? false : 15_000,
  })
}

export function usePagosPaciente(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_CAJA.pagosPaciente(pacienteId ?? ''),
    queryFn: () => listarPagosPorPaciente(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useGastosSede(sedeId?: string) {
  return useQuery({
    queryKey: CLAVES_CAJA.gastosSede(sedeId ?? ''),
    queryFn: () => listarGastosPorSede(sedeId!),
    enabled: Boolean(sedeId),
  })
}

/**
 * Todo lo que cambia con un movimiento de dinero: caja (saldo y totales), pagos y gastos, saldo de los pacientes,
 * lo pagado de cada tratamiento y los reportes del mes del inicio. Se refresca al instante, sin recargar con F5; la
 * promesa termina cuando las listas visibles ya trajeron los datos nuevos.
 */
function useRefrescarFinanzas() {
  const qc = useQueryClient()
  return () =>
    Promise.all(
      [['caja'], ['pagos'], ['gastos'], ['pacientes'], ['tratamientos'], ['reportes'], ['citas']].map((queryKey) =>
        qc.invalidateQueries({ queryKey }),
      ),
    )
}

export function useAbrirCajaMutation() {
  const refrescar = useRefrescarFinanzas()
  return useMutation({ mutationFn: abrirCaja, onSuccess: refrescar })
}

export function useRegistrarMovimientoMutation() {
  const refrescar = useRefrescarFinanzas()
  return useMutation({
    mutationFn: ({ sesionId, dto }: { sesionId: string; dto: CajaMovimientoRequestDTO }) =>
      registrarMovimiento(sesionId, dto),
    onSuccess: refrescar,
  })
}

export function useCerrarCajaMutation() {
  const refrescar = useRefrescarFinanzas()
  return useMutation({
    mutationFn: ({ sesionId, dto }: { sesionId: string; dto: CajaCierreRequestDTO }) =>
      cerrarCaja(sesionId, dto),
    onSuccess: refrescar,
  })
}

export function useRegistrarPagoMutation() {
  const refrescar = useRefrescarFinanzas()
  return useMutation({ mutationFn: registrarPago, onSuccess: refrescar })
}

/** Anular un comprobante también devuelve el saldo al paciente y al tratamiento: se refresca igual que al cobrar. */
export function useAnularPagoMutation() {
  const refrescar = useRefrescarFinanzas()
  return useMutation({
    mutationFn: ({ id, motivo }: { id: string; motivo: string }) => anularPago(id, motivo),
    onSuccess: refrescar,
  })
}
