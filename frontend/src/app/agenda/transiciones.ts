/**
 * Acciones permitidas sobre una cita según su estado.
 *
 * Qué valida el backend desde la Fase 3.6 (Cita.java y CitaApplicationService):
 * - PATCH /citas/{id}/estado sigue una máquina de estados (B-028): ATENDIDA, CANCELADA, NO_ASISTIO y EXPIRADA son
 *   finales y cualquier otra transición se rechaza con 400. CANCELADA delega en la cancelación.
 * - Cancelar: rechaza (400) si la cita es ATENDIDA, CANCELADA, NO_ASISTIO o EXPIRADA y exige un motivo de 5 a 500
 *   caracteres.
 * - Reprogramar: rechaza (400) si la cita está CANCELADA, ATENDIDA o EXPIRADA; exige motivo de 5+ caracteres.
 * - Un ODONTOLOGO solo puede marcar ATENDIDA sus propias citas (403 en el resto); no cancela ni reprograma.
 *
 * El frontend ofrece SOLO un subconjunto prudente de lo que el backend acepta: el flujo operativo normal
 * (PENDIENTE_WEB → PROGRAMADA/CONFIRMADA → EN_SALA → ATENDIDA, con NO_ASISTIO y CANCELADA como salidas). No ofrece
 * "volver atrás" (p. ej. ATENDIDA → PROGRAMADA) aunque el backend lo acepte (brechas-api.md, B2-9).
 */
import { puede, type Rol } from '@/shared/auth'
import type { EstadoCita } from '@/shared/api'

/** Transiciones que se ofrecen con PATCH /citas/{id}/estado (CANCELADA va aparte, con motivo). */
export const TRANSICIONES: Record<EstadoCita, readonly EstadoCita[]> = {
  PENDIENTE_WEB: ['PROGRAMADA', 'CONFIRMADA'],
  PENDIENTE_DE_PAGO: ['CONFIRMADA_CON_ADELANTO', 'EXPIRADA', 'CANCELADA'],
  CONFIRMADA_CON_ADELANTO: ['EN_SALA', 'NO_ASISTIO', 'CANCELADA'],
  EXPIRADA: [],
  PROGRAMADA: ['CONFIRMADA', 'EN_SALA', 'NO_ASISTIO'],
  CONFIRMADA: ['EN_SALA', 'NO_ASISTIO'],
  EN_SALA: [],
  EN_ATENCION: ['ATENDIDA'],
  ATENDIDA: [],
  CANCELADA: [],
  NO_ASISTIO: [],
}

/** Texto del botón que lleva a cada estado. */
export const ACCION_ESTADO: Record<EstadoCita, string> = {
  PENDIENTE_WEB: 'Marcar pendiente',
  PENDIENTE_DE_PAGO: 'Marcar pendiente de pago',
  CONFIRMADA_CON_ADELANTO: 'Confirmar con adelanto',
  EXPIRADA: 'Marcar expirada',
  PROGRAMADA: 'Aceptar reserva',
  CONFIRMADA: 'Confirmar',
  EN_SALA: 'Marcar llegada',
  EN_ATENCION: 'Pasar al sillón',
  ATENDIDA: 'Finalizar atención',
  CANCELADA: 'Cancelar cita',
  NO_ASISTIO: 'No asistió',
}

function normalizar(estado: string | undefined): EstadoCita | null {
  const e = (estado ?? '').toUpperCase()
  return e in TRANSICIONES ? (e as EstadoCita) : null
}

/** Desde la Fase 3.6 (B-028) el backend solo cancela citas abiertas: no ATENDIDA, CANCELADA, NO_ASISTIO ni EXPIRADA. */
export function puedeCancelar(estado: string | undefined): boolean {
  const e = normalizar(estado)
  return e !== null && e !== 'ATENDIDA' && e !== 'CANCELADA' && e !== 'NO_ASISTIO' && e !== 'EXPIRADA'
}

/** El backend también permite reprogramar NO_ASISTIO (y PENDIENTE_WEB): se ofrece. EXPIRADA no (Fase 3.6). */
export function puedeReprogramar(estado: string | undefined): boolean {
  const e = normalizar(estado)
  return e !== null && e !== 'ATENDIDA' && e !== 'CANCELADA' && e !== 'EXPIRADA'
}

export interface AccionesCita {
  /** Estados a los que se puede pasar con PATCH /citas/{id}/estado. */
  estados: EstadoCita[]
  cancelar: boolean
  reprogramar: boolean
  iniciarAtencion: boolean
}

/**
 * Acciones de la cita para el rol (tabla prudente de roles.ts). La seguridad real la decide el backend.
 * ODONTOLOGO solo puede marcar como atendida una cita en sala (`atenderCita`).
 */
export function accionesPara(estado: string | undefined, rol: Rol | null | undefined): AccionesCita {
  const e = normalizar(estado)
  if (!e || !rol) return { estados: [], cancelar: false, reprogramar: false, iniciarAtencion: false }
  const gestiona = puede(rol, 'gestionarCita')
  const estados = TRANSICIONES[e].filter((destino) =>
    destino === 'ATENDIDA' ? (puede(rol, 'atenderCita') || gestiona) : gestiona,
  )
  return {
    estados,
    cancelar: gestiona && puedeCancelar(e),
    reprogramar: gestiona && puedeReprogramar(e),
    iniciarAtencion: e === 'EN_SALA' && gestiona,
  }
}
