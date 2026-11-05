import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'
import { refrescarSedesSesion } from '../auth/sesion-real'

export type SedeAdmin = Esquema<'SedeResponseDTO'>
export type CuerpoSede = Esquema<'SedeRequestDTO'>

export const UNIDADES = [
  { valor: 'VILLADENT', etiqueta: 'Villadent (odontología)' },
  { valor: 'VILLAMAX', etiqueta: 'Villamax (radiología)' },
] as const

/**
 * Sedes físicas oficiales de la clínica (identificadores de la base de datos: Villadent Chilca, Villadent Huancayo Centro y
 * Villamax Chilca). No se pueden desactivar desde la pantalla: la web, las reservas y la agenda dependen de ellas.
 * Si se agrega otra sede fija, se agrega su identificador aquí.
 */
export const SEDES_FIJAS_IDS: readonly string[] = ['sede-chilca-1', 'sede-eltambo', 'sede-villamax-chilca']

export function esSedeFija(id: string | undefined): boolean {
  return !!id && SEDES_FIJAS_IDS.includes(id)
}

export function etiquetaUnidad(unidad: string | undefined): string {
  return UNIDADES.find((u) => u.valor === unidad)?.etiqueta ?? unidad ?? '—'
}

/** GET /sedes/admin: todas las sedes, también las inactivas, para poder reactivarlas. Solo ADMIN_GENERAL. */
export function useSedesAdmin() {
  return useQuery({
    queryKey: ['administracion', 'sedes'] as const,
    queryFn: () => apiTipada.get('/sedes/admin'),
    select: (sedes) => sedes.filter((s) => s.id !== 'sede-chilca-2'),
  })
}

/** Tras tocar una sede: se refrescan las listas y el selector de sedes de la cabecera (que se arma al iniciar sesión). */
function useAlCambiarSede() {
  const qc = useQueryClient()
  return async () => {
    await Promise.all([qc.invalidateQueries({ queryKey: ['administracion'] }), qc.invalidateQueries({ queryKey: ['sedes'] })])
    try {
      await refrescarSedesSesion()
    } catch {
      /* si no se puede releer, la cabecera se actualizará en el próximo inicio de sesión */
    }
  }
}

/**
 * Crear (sin id) o editar (con id). OJO: PUT /sedes/{id} deja en blanco teléfono, WhatsApp, correo, horario y descripción
 * si no se envían; por eso el formulario de edición siempre reenvía todos esos campos con su valor actual.
 */
export function useGuardarSede() {
  const alCambiar = useAlCambiarSede()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id?: string; cuerpo: CuerpoSede }) =>
      id ? apiTipada.put('/sedes/{id}', { path: { id }, cuerpo }) : apiTipada.post('/sedes', { cuerpo }),
    onSuccess: alCambiar,
  })
}

export function useCambiarEstadoSede() {
  const alCambiar = useAlCambiarSede()
  return useMutation({
    mutationFn: ({ id, activa }: { id: string; activa: boolean }) =>
      apiTipada.patch('/sedes/{id}/estado', { path: { id }, query: { activa } }),
    onSuccess: alCambiar,
  })
}
