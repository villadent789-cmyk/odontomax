import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

/* ─────────── Tipos de Odontograma ─────────── */

export interface OdontogramaRegistroDTO {
  id: string
  pacienteId: string
  piezaFdi: number
  estado: string // 'sano', 'caries', 'restauracion', 'corona', 'endodoncia', 'implante', 'ausente', 'sellante', etc.
  caras: string[] // 'V', 'O', 'P', 'L', 'M', 'D'
  observacion?: string
  /** Falso cuando el registro quita ese hallazgo de la pieza; el servidor solo devuelve los vigentes. */
  activo?: boolean
  especialistaId?: string
  registradoPor?: string
  registradoEn: string
}

export interface OdontogramaPiezaRequestDTO {
  piezaFdi: number
  estado: string
  caras?: string[]
  observacion?: string
  /** Falso para quitar este hallazgo de la pieza. */
  activo?: boolean
  especialistaId?: string
}

export interface OdontogramaLoteRequestDTO {
  piezas: OdontogramaPiezaRequestDTO[]
}

/* ─────────── Consultas HTTP ─────────── */

export async function obtenerOdontogramaActual(pacienteId: string): Promise<OdontogramaRegistroDTO[]> {
  return api.get<OdontogramaRegistroDTO[]>(`/odontograma/pacientes/${encodeURIComponent(pacienteId)}`)
}

export async function obtenerHistorialOdontograma(pacienteId: string): Promise<OdontogramaRegistroDTO[]> {
  return api.get<OdontogramaRegistroDTO[]>(`/odontograma/pacientes/${encodeURIComponent(pacienteId)}/historial`)
}

export async function registrarPiezaOdontograma(
  pacienteId: string,
  dto: OdontogramaPiezaRequestDTO
): Promise<OdontogramaRegistroDTO> {
  return api.post<OdontogramaRegistroDTO>(`/odontograma/pacientes/${encodeURIComponent(pacienteId)}/pieza`, dto)
}

export async function registrarLoteOdontograma(
  pacienteId: string,
  dto: OdontogramaLoteRequestDTO
): Promise<OdontogramaRegistroDTO[]> {
  return api.post<OdontogramaRegistroDTO[]>(`/odontograma/pacientes/${encodeURIComponent(pacienteId)}/lote`, dto)
}

/* ─────────── Claves de React Query ─────────── */

export const CLAVES_ODONTOGRAMA = {
  actual: (pacienteId: string) => ['odontograma', 'actual', pacienteId] as const,
  historial: (pacienteId: string) => ['odontograma', 'historial', pacienteId] as const,
}

/* ─────────── Hooks ─────────── */

export function useOdontogramaActual(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_ODONTOGRAMA.actual(pacienteId ?? ''),
    queryFn: () => obtenerOdontogramaActual(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useHistorialOdontograma(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_ODONTOGRAMA.historial(pacienteId ?? ''),
    queryFn: () => obtenerHistorialOdontograma(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useRegistrarPiezaOdontogramaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ pacienteId, dto }: { pacienteId: string; dto: OdontogramaPiezaRequestDTO }) =>
      registrarPiezaOdontograma(pacienteId, dto),
    onSuccess: (_, { pacienteId }) => {
      qc.invalidateQueries({ queryKey: CLAVES_ODONTOGRAMA.actual(pacienteId) })
      qc.invalidateQueries({ queryKey: CLAVES_ODONTOGRAMA.historial(pacienteId) })
      qc.invalidateQueries({ queryKey: ['pacientes', 'detalle', pacienteId] })
      qc.invalidateQueries({ queryKey: ['pacientes'] })
    },
  })
}

export function useRegistrarLoteOdontogramaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ pacienteId, dto }: { pacienteId: string; dto: OdontogramaLoteRequestDTO }) =>
      registrarLoteOdontograma(pacienteId, dto),
    onSuccess: (_, { pacienteId }) => {
      qc.invalidateQueries({ queryKey: CLAVES_ODONTOGRAMA.actual(pacienteId) })
      qc.invalidateQueries({ queryKey: CLAVES_ODONTOGRAMA.historial(pacienteId) })
      qc.invalidateQueries({ queryKey: ['pacientes', 'detalle', pacienteId] })
      qc.invalidateQueries({ queryKey: ['pacientes'] })
    },
  })
}
