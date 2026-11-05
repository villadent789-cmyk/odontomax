import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

import { obtenerToken } from '@/shared/auth/sesion'

/* ─────────── Tipos de Radiografías ─────────── */

export type TipoEstudioRadiografia =
  | 'PANORAMICA'
  | 'PERIAPICAL'
  | 'ALETA_MORDIDA'
  | 'CEFALOMETRICA'
  | 'TOMOGRAFIA'
  | 'FOTO_CLINICA'
  | 'FOTO_ANTES'
  | 'FOTO_DESPUES'
  | 'INFORME'

export interface RadiografiaDTO {
  id: string
  pacienteId: string
  doctorId?: string
  tipoEstudio: TipoEstudioRadiografia
  piezaFdi?: number
  etapa: 'INICIAL' | 'CONTROL' | 'FINAL'
  archivoUrl: string
  nombreArchivo: string
  tamanoBytes: number
  diagnosticoRadiografico?: string
  fechaToma: string
}

export interface UrlFirmadaResponse {
  id: string
  urlFirmada: string
  expiraEnMinutos: number
}

export function resolverUrlRadiografia(rad: RadiografiaDTO): string {
  const token = obtenerToken()
  const base = import.meta.env.VITE_API_URL ?? '/api/v1'
  if (
    rad.archivoUrl &&
    rad.archivoUrl.startsWith('http') &&
    !rad.archivoUrl.includes('storage.local.odontomax.test') &&
    !rad.archivoUrl.includes('test/')
  ) {
    return rad.archivoUrl
  }
  return `${base}/radiografias/${encodeURIComponent(rad.id)}/archivo${token ? `?token=${encodeURIComponent(token)}` : ''}`
}

/* ─────────── Consultas HTTP ─────────── */

export async function listarRadiografias(pacienteId: string): Promise<RadiografiaDTO[]> {
  return api.get<RadiografiaDTO[]>(`/radiografias/paciente/${encodeURIComponent(pacienteId)}`)
}

export async function obtenerUrlFirmada(id: string, minutos = 15): Promise<UrlFirmadaResponse> {
  return api.get<UrlFirmadaResponse>(`/radiografias/${encodeURIComponent(id)}/url-firmada?minutos=${minutos}`)
}

export async function subirRadiografia(formData: FormData): Promise<RadiografiaDTO> {
  return api.post<RadiografiaDTO>('/radiografias/subir', formData)
}

export async function eliminarRadiografia(id: string, motivo: string): Promise<void> {
  return api.delete<void>(`/radiografias/${encodeURIComponent(id)}?motivo=${encodeURIComponent(motivo)}`)
}

/* ─────────── Claves de React Query ─────────── */

export const CLAVES_RADIOGRAFIAS = {
  paciente: (pacienteId: string) => ['radiografias', 'paciente', pacienteId] as const,
}

/* ─────────── Hooks ─────────── */

export function useRadiografiasPaciente(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_RADIOGRAFIAS.paciente(pacienteId ?? ''),
    queryFn: () => listarRadiografias(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useSubirRadiografiaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: subirRadiografia,
    onSuccess: (nueva) => {
      qc.invalidateQueries({ queryKey: CLAVES_RADIOGRAFIAS.paciente(nueva.pacienteId) })
    },
  })
}

export function useEliminarRadiografiaMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, motivo }: { id: string; motivo: string; pacienteId: string }) =>
      eliminarRadiografia(id, motivo),
    onSuccess: (_, { pacienteId }) => {
      qc.invalidateQueries({ queryKey: CLAVES_RADIOGRAFIAS.paciente(pacienteId) })
    },
  })
}
