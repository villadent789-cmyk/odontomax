import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'

export type ServicioAdmin = Esquema<'ServicioResponseDTO'>
export type CuerpoServicio = Esquema<'ServicioRequestDTO'>

/** GET /servicios/admin: todo el catálogo, también los servicios inactivos. Solo ADMIN_GENERAL. */
export function useServiciosAdmin() {
  return useQuery({
    queryKey: ['administracion', 'servicios'] as const,
    queryFn: () => apiTipada.get('/servicios/admin'),
  })
}

/** Tras tocar el catálogo se refrescan también las listas que usan recepción y los tratamientos (solo activos). */
function useAlCambiarServicio() {
  const qc = useQueryClient()
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['administracion', 'servicios'] }),
      qc.invalidateQueries({ queryKey: ['servicios'] }),
    ])
}

/**
 * Crear (sin id) o editar (con id). OJO: PUT /servicios/{id} deja en blanco el precio y la descripción si no se envían
 * (y conserva el orden solo si no se manda); por eso la edición siempre reenvía todos los campos con su valor actual.
 */
export function useGuardarServicio() {
  const alCambiar = useAlCambiarServicio()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id?: string; cuerpo: CuerpoServicio }) =>
      id ? apiTipada.put('/servicios/{id}', { path: { id }, cuerpo }) : apiTipada.post('/servicios', { cuerpo }),
    onSuccess: alCambiar,
  })
}

export function useCambiarEstadoServicio() {
  const alCambiar = useAlCambiarServicio()
  return useMutation({
    mutationFn: ({ id, activo }: { id: string; activo: boolean }) =>
      apiTipada.patch('/servicios/{id}/estado', { path: { id }, query: { activo } }),
    onSuccess: alCambiar,
  })
}
