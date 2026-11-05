import { useQuery } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'

export type AuditoriaDTO = Esquema<'AuditoriaResponseDTO'>

/** Valor del selector que significa "sin filtro de módulo". */
export const TODOS_LOS_MODULOS = 'TODOS'

/**
 * GET /auditoria?modulo=. Solo ADMIN_GENERAL. El backend devuelve como máximo los 200 eventos más recientes y solo
 * sabe filtrar por módulo (brecha #15): cualquier otro filtro se aplica en el cliente sobre esa respuesta.
 */
export function listarAuditoria(modulo?: string): Promise<AuditoriaDTO[]> {
  const filtro = modulo && modulo !== TODOS_LOS_MODULOS ? modulo : undefined
  return apiTipada.get('/auditoria', { query: { modulo: filtro } })
}

export function useAuditoria(modulo?: string) {
  return useQuery({
    queryKey: ['auditoria', modulo ?? TODOS_LOS_MODULOS] as const,
    queryFn: () => listarAuditoria(modulo),
  })
}
