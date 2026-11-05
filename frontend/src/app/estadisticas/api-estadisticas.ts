import { useQuery } from '@tanstack/react-query'
import { api } from '@/shared/http'
import type {
  FiltrosReportes,
  ReporteCitasDTO,
  ReporteDeudoresDTO,
  MiAvanceDTO,
  RendimientoDoctoresDTO,
  ReporteDoctoresDTO,
  ReporteFinancieroDTO,
  ReporteInventarioDTO,
  ReportePacientesDTO,
} from './types'

function construirQueryString(params: Record<string, string | undefined>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v && v.trim() !== '') {
      sp.append(k, v)
    }
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

export function useReporteFinanciero(filtros: FiltrosReportes, habilitado = true) {
  return useQuery({
    enabled: habilitado,
    queryKey: ['reportes', 'financiero', filtros],
    queryFn: () =>
      api.get<ReporteFinancieroDTO>(
        `/reportes/financiero${construirQueryString({
          sedeId: filtros.sedeId,
          desde: filtros.desde,
          hasta: filtros.hasta,
        })}`,
      ),
  })
}

export function useReporteDeudores(sedeId?: string) {
  return useQuery({
    queryKey: ['reportes', 'deudores', sedeId],
    queryFn: () =>
      api.get<ReporteDeudoresDTO>(
        `/reportes/deudores${construirQueryString({ sedeId })}`,
      ),
  })
}

export function useReporteCitas(filtros: FiltrosReportes) {
  return useQuery({
    queryKey: ['reportes', 'citas', filtros],
    queryFn: () =>
      api.get<ReporteCitasDTO>(
        `/reportes/citas${construirQueryString({
          sedeId: filtros.sedeId,
          desde: filtros.desde,
          hasta: filtros.hasta,
        })}`,
      ),
  })
}

export function useReportePacientes(filtros: FiltrosReportes) {
  return useQuery({
    queryKey: ['reportes', 'pacientes', filtros],
    queryFn: () =>
      api.get<ReportePacientesDTO>(
        `/reportes/pacientes${construirQueryString({
          sedeId: filtros.sedeId,
          desde: filtros.desde,
          hasta: filtros.hasta,
        })}`,
      ),
  })
}

export function useReporteDoctores(filtros: FiltrosReportes) {
  return useQuery({
    queryKey: ['reportes', 'doctores', filtros],
    queryFn: () =>
      api.get<ReporteDoctoresDTO>(
        `/reportes/doctores${construirQueryString({
          sedeId: filtros.sedeId,
          desde: filtros.desde,
          hasta: filtros.hasta,
        })}`,
      ),
  })
}

export function useReporteInventario(sedeId?: string) {
  return useQuery({
    queryKey: ['reportes', 'inventario', sedeId],
    queryFn: () =>
      api.get<ReporteInventarioDTO>(
        `/reportes/inventario${construirQueryString({ sedeId })}`,
      ),
  })
}

export function useRendimientoDoctores(mes: string, sedeId?: string) {
  return useQuery({
    queryKey: ['reportes', 'rendimiento-doctores', mes, sedeId],
    queryFn: () =>
      api.get<RendimientoDoctoresDTO>(`/reportes/rendimiento-doctores${construirQueryString({ mes, sedeId })}`),
  })
}

/** Avance del mes del odontólogo autenticado. Solo se consulta con rol ODONTOLOGO. */
export function useMiAvance(mes: string, habilitado = true) {
  return useQuery({
    queryKey: ['rendimiento', 'mi-avance', mes],
    queryFn: () => api.get<MiAvanceDTO>(`/rendimiento/mi-avance${construirQueryString({ mes })}`),
    enabled: habilitado,
  })
}
