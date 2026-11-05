import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'

export type TipoCampo = 'texto' | 'textoLargo' | 'numero' | 'seleccion' | 'multiple' | 'si_no' | 'fecha' | 'pieza'

export interface CampoPlantilla {
  id: string
  etiqueta: string
  tipo: TipoCampo
  opciones?: string[]
  obligatorio?: boolean
}

export interface SeccionPlantilla {
  titulo: string
  campos: CampoPlantilla[]
}

export interface PlantillaTratamiento {
  codigo: string
  nombre: string
  negocio: 'VILLADENT' | 'VILLAMAX'
  descripcion?: string
  version: number
  esquema: { secciones: SeccionPlantilla[]; consentimiento?: string }
}

export type DatosFicha = Record<string, string | number | boolean | string[] | null | undefined>

export interface RegistroPlantilla {
  id: string
  pacienteId: string
  tratamientoId?: string | null
  citaId?: string | null
  plantillaCodigo: string
  plantillaNombre: string
  plantillaVersion: number
  datos: DatosFicha
  estado: 'BORRADOR' | 'CERRADO'
  corrigeId?: string | null
  creadoPorNombre?: string | null
  creadoEn: string
  cerradoEn?: string | null
}

const CLAVE = ['plantillas'] as const

/** Plantillas activas del negocio indicado (o de todos si no se indica). */
export function usePlantillas(negocio?: string) {
  return useQuery({
    queryKey: [...CLAVE, 'lista', negocio ?? 'todas'] as const,
    queryFn: () => api.get<PlantillaTratamiento[]>(negocio ? `/plantillas?negocio=${encodeURIComponent(negocio)}` : '/plantillas'),
  })
}

export function useRegistrosPlantilla(pacienteId?: string, habilitado = true) {
  return useQuery({
    queryKey: [...CLAVE, 'registros', pacienteId] as const,
    queryFn: () => api.get<RegistroPlantilla[]>(`/plantillas/registros/paciente/${pacienteId}`),
    enabled: Boolean(pacienteId) && habilitado,
  })
}

function useRefrescar() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries({ queryKey: [...CLAVE, 'registros'] })
}

export function useCrearFichaMutation() {
  const refrescar = useRefrescar()
  return useMutation({
    mutationFn: (cuerpo: { pacienteId: string; plantillaCodigo: string; tratamientoId?: string; citaId?: string; corrigeId?: string; datos: DatosFicha }) =>
      api.post<RegistroPlantilla>('/plantillas/registros', cuerpo),
    onSuccess: refrescar,
  })
}

export function useGuardarFichaMutation() {
  const refrescar = useRefrescar()
  return useMutation({
    mutationFn: ({ id, datos }: { id: string; datos: DatosFicha }) => api.put<RegistroPlantilla>(`/plantillas/registros/${id}`, { datos }),
    onSuccess: refrescar,
  })
}

export function useCerrarFichaMutation() {
  const refrescar = useRefrescar()
  return useMutation({
    mutationFn: (id: string) => api.post<RegistroPlantilla>(`/plantillas/registros/${id}/cerrar`, {}),
    onSuccess: refrescar,
  })
}
