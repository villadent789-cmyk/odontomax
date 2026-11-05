/**
 * Correcciones de nombre, DNI y fecha de nacimiento de un paciente: cualquier rol las solicita y el dueño las aprueba o
 * rechaza (POST|GET /pacientes/{id}/solicitudes-correccion, GET /pacientes/solicitudes-correccion/pendientes y
 * POST /pacientes/solicitudes-correccion/{id}/aprobar|rechazar).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'
import { formatFecha } from '@/shared/format'
import { CLAVES_PACIENTES } from './api-pacientes'

export type SolicitudCorreccion = Esquema<'SolicitudCorreccionResponseDTO'>
export type CuerpoSolicitudCorreccion = Esquema<'SolicitudCorreccionRequestDTO'>

const claves = {
  pendientes: ['pacientes', 'correcciones', 'pendientes'] as const,
  deUnPaciente: (id: string) => ['pacientes', 'correcciones', id] as const,
}

export function useSolicitudesDePaciente(pacienteId: string | undefined) {
  return useQuery({
    queryKey: claves.deUnPaciente(pacienteId ?? ''),
    queryFn: () => apiTipada.get('/pacientes/{id}/solicitudes-correccion', { path: { id: pacienteId! } }),
    enabled: !!pacienteId,
  })
}

/** Solo el administrador general las ve; `habilitado` evita pedirlas a otros roles. */
export function useSolicitudesPendientes(habilitado: boolean) {
  return useQuery({
    queryKey: claves.pendientes,
    queryFn: () => apiTipada.get('/pacientes/solicitudes-correccion/pendientes'),
    enabled: habilitado,
  })
}

export function useSolicitarCorreccion(pacienteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: CuerpoSolicitudCorreccion) =>
      apiTipada.post('/pacientes/{id}/solicitudes-correccion', { path: { id: pacienteId }, cuerpo }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['pacientes', 'correcciones'] }),
  })
}

export function useResolverSolicitud() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, aprobar, comentario }: { id: string; aprobar: boolean; comentario?: string }) =>
      aprobar
        ? apiTipada.post('/pacientes/solicitudes-correccion/{solicitudId}/aprobar', { path: { solicitudId: id }, cuerpo: { comentario } })
        : apiTipada.post('/pacientes/solicitudes-correccion/{solicitudId}/rechazar', { path: { solicitudId: id }, cuerpo: { comentario } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['pacientes'] })
      void qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos })
    },
  })
}

/** Cambios propuestos, uno por línea: «Nombres: Ana → Anna». */
export function cambiosDe(s: SolicitudCorreccion): string[] {
  const lineas: string[] = []
  if (s.nombresNuevo) lineas.push(`Nombres: ${s.nombresAnterior} → ${s.nombresNuevo}`)
  if (s.apellidosNuevo) lineas.push(`Apellidos: ${s.apellidosAnterior} → ${s.apellidosNuevo}`)
  if (s.dniNuevo) lineas.push(`DNI: ${s.dniAnterior} → ${s.dniNuevo}`)
  if (s.fechaNacimientoNueva) {
    const antes = s.fechaNacimientoAnterior ? formatFecha(s.fechaNacimientoAnterior) : 'sin fecha'
    lineas.push(`Fecha de nacimiento: ${antes} → ${formatFecha(s.fechaNacimientoNueva)}`)
  }
  return lineas
}
