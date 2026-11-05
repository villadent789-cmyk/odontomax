import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/shared/http'
import { CLAVES_PACIENTES } from './api-pacientes'

/** Historia clínica general del paciente, ordenada por secciones. Todo es opcional. */
export interface FichaIngreso {
  // Datos personales
  tipoDocumento?: string
  lugarNacimiento?: string
  estadoCivil?: string
  gradoInstruccion?: string
  ocupacion?: string
  centroTrabajo?: string
  centroEstudios?: string
  hijos?: number
  direccion?: string
  urbanizacion?: string
  pais?: string
  departamento?: string
  provincia?: string
  distrito?: string
  correoAlternativo?: string
  telefonoFijo?: string
  // En caso de emergencia
  emergenciaNombre?: string
  emergenciaTelefono?: string
  emergenciaParentesco?: string
  // Apoderado
  apoderadoNombre?: string
  apoderadoApellido?: string
  apoderadoDni?: string
  apoderadoResponsabilidad?: string
  // Seguro
  seguroCompania?: string
  seguroPoliza?: string
  // Atención
  motivoConsulta?: string
  observaciones?: string
  ultimaVisitaDentista?: string
  motivoUltimaVisita?: string
  fichaAnterior?: string
  pacienteConRiesgo?: boolean
  // Examen clínico y estomatológico
  sangradoEncias?: boolean
  bruxismo?: boolean
  mucosaBucalAlterada?: boolean
  dolor?: boolean
  movilidadDentaria?: boolean
  frecuenciaCepillado?: string
  frecuenciaVisitaDentista?: string
  experienciasTraumaticas?: string
  ultimaProfilaxis?: string
  siguienteProfilaxis?: string
  // Historial de enfermedades
  enfVenereas?: boolean
  hemorragias?: boolean
  alteracionRespiratoria?: boolean
  enfermedadRenal?: boolean
  infartoCardiaco?: boolean
  anginaPecho?: boolean
  diabetes?: boolean
  hepatitis?: boolean
  tbc?: boolean
  presionAlta?: boolean
  medicamentos?: string
  nombreMedico?: string
  // Pediatría
  pedMadre?: string
  pedPadre?: string
  pedAbuelosPaternos?: string
  pedAbuelosMaternos?: string
  pedTipoParto?: string
  pedHorasTrabajoParto?: string
  pedSemanasGestacion?: string
  pedLloroAlNacer?: boolean
  pedObservaciones?: string
  // Solo en la respuesta
  actualizadoEn?: string
  actualizadoPorNombre?: string
}

export const CLAVES_FICHA = {
  ficha: (pacienteId: string) => ['pacientes', 'ficha-ingreso', pacienteId] as const,
  foto: (pacienteId: string, version?: string | null) => ['pacientes', 'foto', pacienteId, version ?? ''] as const,
}

export function useFichaIngreso(pacienteId?: string) {
  return useQuery({
    queryKey: CLAVES_FICHA.ficha(pacienteId ?? ''),
    queryFn: () => api.get<FichaIngreso>(`/pacientes/${encodeURIComponent(pacienteId!)}/ficha-ingreso`),
    enabled: Boolean(pacienteId),
  })
}

export function useGuardarFichaIngreso(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ficha: FichaIngreso) =>
      api.put<FichaIngreso>(`/pacientes/${encodeURIComponent(pacienteId)}/ficha-ingreso`, ficha),
    onSuccess: (guardada) => qc.setQueryData(CLAVES_FICHA.ficha(pacienteId), guardada),
  })
}

/** Foto de perfil como URL local (blob). `version` cambia al subir otra foto y obliga a pedirla de nuevo. */
export function useFotoPaciente(pacienteId?: string, version?: string | null) {
  return useQuery({
    queryKey: CLAVES_FICHA.foto(pacienteId ?? '', version),
    queryFn: async () => {
      const blob = await api.get<Blob>(`/pacientes/${encodeURIComponent(pacienteId!)}/foto`, { binario: true })
      return URL.createObjectURL(blob)
    },
    enabled: Boolean(pacienteId) && Boolean(version),
    staleTime: Infinity,
    retry: false,
  })
}

/** Reduce la foto en el navegador (lado mayor de 480 px, JPEG): pesa unos 50 KB y se sube rápido desde el celular. */
export async function reducirImagen(archivo: Blob, ladoMaximo = 480): Promise<Blob> {
  const bitmap = await createImageBitmap(archivo)
  const escala = Math.min(1, ladoMaximo / Math.max(bitmap.width, bitmap.height))
  const lienzo = document.createElement('canvas')
  lienzo.width = Math.round(bitmap.width * escala)
  lienzo.height = Math.round(bitmap.height * escala)
  lienzo.getContext('2d')?.drawImage(bitmap, 0, 0, lienzo.width, lienzo.height)
  bitmap.close()
  return new Promise((resolver, rechazar) => {
    lienzo.toBlob((b) => (b ? resolver(b) : rechazar(new Error('No se pudo preparar la foto.'))), 'image/jpeg', 0.85)
  })
}

export function useSubirFotoPaciente(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (archivo: Blob) => {
      const reducida = await reducirImagen(archivo)
      const datos = new FormData()
      datos.append('archivo', reducida, 'foto.jpg')
      return api.put<{ fotoActualizadaEn: string }>(`/pacientes/${encodeURIComponent(pacienteId)}/foto`, datos)
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos }),
  })
}

export function useEliminarFotoPaciente(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.delete<void>(`/pacientes/${encodeURIComponent(pacienteId)}/foto`),
    onSuccess: () => qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos }),
  })
}
