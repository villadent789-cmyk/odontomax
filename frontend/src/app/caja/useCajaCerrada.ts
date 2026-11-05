import { useCajaActiva } from './api-caja'

/**
 * true solo cuando la consulta TERMINÓ y la sede no tiene turno de caja abierto. Mientras carga, si falla o si no
 * hay sede elegida devuelve false: un aviso de "caja cerrada" erróneo sería peor que ninguno.
 */
export function useCajaCerrada(sedeId: string | null | undefined): boolean {
  const { data, isSuccess } = useCajaActiva(sedeId ?? undefined)
  return isSuccess && !(data && data.estado === 'ABIERTA')
}
