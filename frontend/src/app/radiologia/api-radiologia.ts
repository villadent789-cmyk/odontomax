import { api } from '@/shared/http'
import type {
  ComisionRadiologicaCreateRequestDTO,
  ComisionRadiologicaDTO,
  ComisionRadiologicaLiquidarRequestDTO,
  DoctorDerivadorDTO,
  DoctorDerivadorRequestDTO,
  ResumenComisionesDTO,
} from './types'

export async function listarComisiones(
  doctorId?: string,
  estado?: string
): Promise<ComisionRadiologicaDTO[]> {
  const params = new URLSearchParams()
  if (doctorId) params.set('doctorId', doctorId)
  if (estado && estado !== 'TODOS') params.set('estado', estado)
  const qs = params.toString()
  return api.get<ComisionRadiologicaDTO[]>(qs ? `/radiologia/comisiones?${qs}` : '/radiologia/comisiones')
}

export async function obtenerResumenComisiones(): Promise<ResumenComisionesDTO> {
  return api.get<ResumenComisionesDTO>('/radiologia/comisiones/resumen')
}

export async function registrarComision(
  payload: ComisionRadiologicaCreateRequestDTO
): Promise<ComisionRadiologicaDTO> {
  return api.post<ComisionRadiologicaDTO>('/radiologia/comisiones', payload)
}

export async function liquidarComision(
  id: string,
  payload: ComisionRadiologicaLiquidarRequestDTO
): Promise<ComisionRadiologicaDTO> {
  return api.patch<ComisionRadiologicaDTO>(`/radiologia/comisiones/${id}/liquidar`, payload)
}

export async function listarDoctoresDerivadores(
  soloActivos = false
): Promise<DoctorDerivadorDTO[]> {
  return api.get<DoctorDerivadorDTO[]>(`/radiologia/derivadores?soloActivos=${soloActivos}`)
}

export async function crearDoctorDerivador(
  payload: DoctorDerivadorRequestDTO
): Promise<DoctorDerivadorDTO> {
  return api.post<DoctorDerivadorDTO>('/radiologia/derivadores', payload)
}

export async function actualizarDoctorDerivador(
  id: string,
  payload: DoctorDerivadorRequestDTO
): Promise<DoctorDerivadorDTO> {
  return api.put<DoctorDerivadorDTO>(`/radiologia/derivadores/${id}`, payload)
}
