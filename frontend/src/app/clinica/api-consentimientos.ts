import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'
import { obtenerToken } from '@/shared/auth/sesion'

/* ─────────── Tipos de Consentimientos ─────────── */

export interface PlantillaConsentimientoDTO {
  id: string
  codigo: string
  titulo: string
  tipo: 'TRATAMIENTO_DATOS' | 'INFORMADO_PROCEDIMIENTO' | 'USO_IMAGEN'
  contenido: string
  version: number
  activo: boolean
}

export interface ConsentimientoDTO {
  id: string
  pacienteId: string
  plantillaId?: string
  titulo?: string
  tipo: string
  contenidoAceptado?: string
  firmaUrl?: string
  pdfUrl?: string
  nombreFirmante?: string
  documentoFirmante?: string
  parentescoFirmante?: string
  ipFirmante?: string
  firmadoEn: string
  firmadoPorNombre?: string
  activo: boolean
}

export function resolverUrlFirmaConsentimiento(c: ConsentimientoDTO): string {
  const token = obtenerToken()
  const base = import.meta.env.VITE_API_URL ?? '/api/v1'
  if (c.firmaUrl && c.firmaUrl.startsWith('http') && !c.firmaUrl.includes('storage.local.odontomax.test')) {
    return c.firmaUrl
  }
  return `${base}/consentimientos/${encodeURIComponent(c.id)}/firma${token ? `?token=${encodeURIComponent(token)}` : ''}`
}

export function resolverUrlPdfConsentimiento(c: ConsentimientoDTO): string {
  const token = obtenerToken()
  const base = import.meta.env.VITE_API_URL ?? '/api/v1'
  if (c.pdfUrl && c.pdfUrl.startsWith('http') && !c.pdfUrl.includes('storage.local.odontomax.test')) {
    return c.pdfUrl
  }
  return `${base}/consentimientos/${encodeURIComponent(c.id)}/pdf${token ? `?token=${encodeURIComponent(token)}` : ''}`
}

export interface ConsentimientoRequestDTO {
  pacienteId: string
  plantillaId?: string
  tipo: string
  titulo: string
  contenidoAceptado: string
  firmaBase64: string
  nombreFirmante?: string
  documentoFirmante?: string
  parentescoFirmante?: string
}

/* ─────────── Consultas HTTP ─────────── */

export async function listarPlantillas(): Promise<PlantillaConsentimientoDTO[]> {
  return api.get<PlantillaConsentimientoDTO[]>('/consentimientos/plantillas')
}

export async function listarConsentimientosPorPaciente(pacienteId: string): Promise<ConsentimientoDTO[]> {
  return api.get<ConsentimientoDTO[]>(`/consentimientos/pacientes/${encodeURIComponent(pacienteId)}`)
}

export async function registrarConsentimiento(dto: ConsentimientoRequestDTO): Promise<ConsentimientoDTO> {
  return api.post<ConsentimientoDTO>('/consentimientos', dto)
}

/* ─────────── Claves de React Query ─────────── */

export const CLAVES_CONSENTIMIENTOS = {
  plantillas: ['consentimientos', 'plantillas'] as const,
  paciente: (pacienteId: string) => ['consentimientos', 'paciente', pacienteId] as const,
}

/* ─────────── Hooks ─────────── */

export function usePlantillasConsentimiento() {
  return useQuery({
    queryKey: CLAVES_CONSENTIMIENTOS.plantillas,
    queryFn: listarPlantillas,
  })
}

export function useConsentimientosPaciente(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_CONSENTIMIENTOS.paciente(pacienteId ?? ''),
    queryFn: () => listarConsentimientosPorPaciente(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useRegistrarConsentimientoMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: registrarConsentimiento,
    onSuccess: (guardado) => {
      qc.invalidateQueries({ queryKey: CLAVES_CONSENTIMIENTOS.paciente(guardado.pacienteId) })
      qc.invalidateQueries({ queryKey: ['pacientes'] })
    },
  })
}
