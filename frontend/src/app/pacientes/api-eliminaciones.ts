/**
 * Eliminar una radiografía o un paciente con autorización del dueño: cualquier rol con acceso lo solicita y el dueño lo
 * aprueba (entonces se elimina de verdad) o lo rechaza (POST /solicitudes-eliminacion, GET /solicitudes-eliminacion/pendientes
 * y POST /solicitudes-eliminacion/{id}/aprobar|rechazar).
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'
import { CLAVES_PACIENTES } from './api-pacientes'

export type SolicitudEliminacion = Esquema<'SolicitudEliminacionResponseDTO'>
export type TipoEliminacion = 'RADIOGRAFIA' | 'PACIENTE'

const claves = {
  pendientes: ['solicitudes-eliminacion', 'pendientes'] as const,
}

/** Solo el administrador general las ve; `habilitado` evita pedirlas a otros roles. */
export function useEliminacionesPendientes(habilitado: boolean) {
  return useQuery({
    queryKey: claves.pendientes,
    queryFn: () => apiTipada.get('/solicitudes-eliminacion/pendientes'),
    enabled: habilitado,
  })
}

export function useSolicitarEliminacion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: { tipo: TipoEliminacion; objetivoId: string; motivo: string }) =>
      apiTipada.post('/solicitudes-eliminacion', { cuerpo }),
    onSuccess: () => qc.invalidateQueries({ queryKey: claves.pendientes }),
  })
}

export function useResolverEliminacion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, aprobar, comentario }: { id: string; aprobar: boolean; comentario?: string }) =>
      aprobar
        ? apiTipada.post('/solicitudes-eliminacion/{solicitudId}/aprobar', { path: { solicitudId: id }, cuerpo: { comentario } })
        : apiTipada.post('/solicitudes-eliminacion/{solicitudId}/rechazar', { path: { solicitudId: id }, cuerpo: { comentario } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: claves.pendientes })
      void qc.invalidateQueries({ queryKey: CLAVES_PACIENTES.todos })
      void qc.invalidateQueries({ queryKey: ['radiografias'] })
    },
  })
}
