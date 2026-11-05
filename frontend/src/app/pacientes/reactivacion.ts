import { useQuery } from '@tanstack/react-query'
import { listarTratamientosDeSedes } from './reportePacientes'

export type EstadoReactivacion = 'nuevo' | 'activo' | 'seguimiento' | 'inactivo'

/** Días sin atención a partir de los cuales se ofrece reactivar (6 meses). */
export const DIAS_PARA_REACTIVAR = 180

export function diasDesde(fecha: string | null | undefined, ahora: number = Date.now()): number | null {
  if (!fecha) return null
  return Math.floor((ahora - new Date(fecha).getTime()) / 86_400_000)
}

/** Semáforo por última atención. Sin ninguna atención el paciente es "nuevo", nunca "inactivo". */
export function estadoReactivacion(ultimaCitaAtendida?: string | null, ahora: number = Date.now()): EstadoReactivacion {
  const dias = diasDesde(ultimaCitaAtendida, ahora)
  if (dias === null) return 'nuevo'
  if (dias < 90) return 'activo'
  if (dias <= DIAS_PARA_REACTIVAR) return 'seguimiento'
  return 'inactivo'
}

export const SEMAFORO_CONFIG: Record<EstadoReactivacion, { etiqueta: string; clase: string }> = {
  nuevo:       { etiqueta: '● Nuevo',           clase: 'bg-sky-100 text-sky-800 border border-sky-300' },
  activo:      { etiqueta: '● Activo',          clase: 'bg-emerald-100 text-emerald-800 border border-emerald-300' },
  seguimiento: { etiqueta: '● En Seguimiento',  clase: 'bg-amber-100 text-amber-800 border border-amber-300' },
  inactivo:    { etiqueta: '● Inactivo',        clase: 'bg-red-100 text-red-800 border border-red-300' },
}

/**
 * "Reactivar" solo tiene sentido con quien ya terminó un tratamiento y no vuelve hace más de 6 meses. A un paciente
 * nuevo (sin atenciones) o sin tratamiento concluido nunca se le ofrece.
 */
export function puedeReactivar(
  paciente: { id?: string; ultimaCitaAtendida?: string | null },
  conTratamientoCompletado: ReadonlySet<string>,
  ahora: number = Date.now(),
): boolean {
  const dias = diasDesde(paciente.ultimaCitaAtendida, ahora)
  return dias !== null && dias > DIAS_PARA_REACTIVAR && !!paciente.id && conTratamientoCompletado.has(paciente.id)
}

/**
 * Pacientes con al menos un tratamiento COMPLETADO, a partir de los tratamientos de las sedes del usuario. Si la
 * consulta falla el conjunto queda vacío: es preferible no ofrecer "Reactivar" que ofrecerlo por error.
 */
export function usePacientesConTratamientoCompletado(sedeIds: string[]) {
  const ids = [...new Set(sedeIds)].sort()
  const consulta = useQuery({
    queryKey: ['tratamientos', 'completados-por-sede', ids] as const,
    queryFn: () => listarTratamientosDeSedes(ids),
    enabled: ids.length > 0,
    staleTime: 60_000,
  })
  const conjunto = new Set(
    (consulta.data ?? []).filter((t) => t.estado === 'COMPLETADO' && t.pacienteId).map((t) => t.pacienteId as string),
  )
  return conjunto
}
