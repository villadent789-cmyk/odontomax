import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { Esquema } from '@/shared/api'
import { api } from '@/shared/http'

/* ─────────── Tipos de Historia Clínica y Notas de Evolución ─────────── */

export interface NotaEvolucionDTO {
  id: string
  pacienteId: string
  citaId?: string
  autorId: string
  autorNombre: string
  autorRol: string
  tipoNota: 'SOAP' | 'TEXTO_LIBRE'
  subjetivo?: string
  objetivo?: string
  analisis?: string
  plan?: string
  contenido?: string
  notaReferenciaId?: string
  motivoCorreccion?: string
  firmado: boolean
  creadoEn: string
}

export interface NotaEvolucionRequestDTO {
  citaId?: string
  tipoNota?: 'SOAP' | 'TEXTO_LIBRE'
  subjetivo?: string
  objetivo?: string
  analisis?: string
  plan?: string
  contenido?: string
  notaReferenciaId?: string
  motivoCorreccion?: string
}

export interface AntecedenteHistorialDTO {
  id: string
  pacienteId: string
  alergias?: string
  antecedentesMedicos?: string
  esGestante: boolean
  semanasGestacion?: number
  motivoCambio: string
  modificadoPor?: string
  modificadoPorNombre?: string
  creadoEn: string
}

export interface AntecedentesUpdateRequestDTO {
  alergias?: string
  antecedentesMedicos?: string
  esGestante: boolean
  semanasGestacion?: number
  motivoCambio: string
}

/* ─────────── Tipos de Acceso a Historia Clínica ─────────── */

export interface HistoriaClinicaAccesoDTO {
  id: string
  pacienteId: string
  usuarioId: string
  usuarioNombre: string
  usuarioRol: string
  ip?: string
  accedidoEn: string
}

/* ─────────── Consultas HTTP ─────────── */

export async function registrarAperturaHistoria(pacienteId: string): Promise<HistoriaClinicaAccesoDTO> {
  return api.post<HistoriaClinicaAccesoDTO>(
    `/historia-clinica/pacientes/${encodeURIComponent(pacienteId)}/apertura`,
    {}
  )
}

export async function listarAccesosHistoria(pacienteId: string): Promise<HistoriaClinicaAccesoDTO[]> {
  return api.get<HistoriaClinicaAccesoDTO[]>(
    `/historia-clinica/accesos?pacienteId=${encodeURIComponent(pacienteId)}`
  )
}

export async function listarNotasEvolucion(pacienteId: string): Promise<NotaEvolucionDTO[]> {
  return api.get<NotaEvolucionDTO[]>(`/evoluciones/pacientes/${encodeURIComponent(pacienteId)}`)
}

export async function crearNotaEvolucion(
  pacienteId: string,
  dto: NotaEvolucionRequestDTO
): Promise<NotaEvolucionDTO> {
  return api.post<NotaEvolucionDTO>(`/evoluciones/pacientes/${encodeURIComponent(pacienteId)}`, dto)
}

export async function obtenerHistorialAntecedentes(pacienteId: string): Promise<AntecedenteHistorialDTO[]> {
  return api.get<AntecedenteHistorialDTO[]>(`/antecedentes/pacientes/${encodeURIComponent(pacienteId)}/historial`)
}

export async function actualizarAntecedentes(
  pacienteId: string,
  dto: AntecedentesUpdateRequestDTO
): Promise<Esquema<'PacienteResponseDTO'>> {
  return api.put<Esquema<'PacienteResponseDTO'>>(`/antecedentes/pacientes/${encodeURIComponent(pacienteId)}`, dto)
}

/* ─────────── Claves de React Query ─────────── */

export const CLAVES_HISTORIA = {
  notas: (pacienteId: string) => ['evoluciones', 'paciente', pacienteId] as const,
  antecedentesHistorial: (pacienteId: string) => ['antecedentes', 'historial', pacienteId] as const,
  accesos: (pacienteId: string) => ['historia-clinica', 'accesos', pacienteId] as const,
}

/* ─────────── Hooks ─────────── */

export function useNotasEvolucion(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_HISTORIA.notas(pacienteId ?? ''),
    queryFn: () => listarNotasEvolucion(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useCrearNotaEvolucionMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ pacienteId, dto }: { pacienteId: string; dto: NotaEvolucionRequestDTO }) =>
      crearNotaEvolucion(pacienteId, dto),
    onSuccess: (_, { pacienteId }) => {
      qc.invalidateQueries({ queryKey: CLAVES_HISTORIA.notas(pacienteId) })
      qc.invalidateQueries({ queryKey: ['pacientes'] })
    },
  })
}

export function useAccesosHistoria(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_HISTORIA.accesos(pacienteId ?? ''),
    queryFn: () => listarAccesosHistoria(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useHistorialAntecedentes(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_HISTORIA.antecedentesHistorial(pacienteId ?? ''),
    queryFn: () => obtenerHistorialAntecedentes(pacienteId!),
    enabled: Boolean(pacienteId),
  })
}

export function useActualizarAntecedentesMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ pacienteId, dto }: { pacienteId: string; dto: AntecedentesUpdateRequestDTO }) =>
      actualizarAntecedentes(pacienteId, dto),
    onSuccess: (_, { pacienteId }) => {
      qc.invalidateQueries({ queryKey: CLAVES_HISTORIA.antecedentesHistorial(pacienteId) })
      qc.invalidateQueries({ queryKey: ['pacientes'] })
    },
  })
}
