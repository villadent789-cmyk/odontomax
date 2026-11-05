import type { TonoBadge } from './variants'

export interface DefinicionEstado {
  texto: string
  tono: TonoBadge
}

/**
 * Mapa ÚNICO estado → texto/color de todo el sistema (citas, pagos, comprobantes, tratamientos...).
 * La clave es el valor exacto que devuelve la API (p. ej. "CONFIRMADA"). Solo se añaden estados que existen en el
 * backend: los que el diseño inventa (p. ej. "PENDIENTE_DE_PAGO") no entran (brechas-api.md, G-2).
 *
 * Convención de tonos (guía A1, sección 5): primario = confirmado/correcto, accion = en espera/pendiente,
 * villamax = en atención/en curso, peligro = cancelado/rechazado, neutro = resto.
 */
export const ESTADOS: Record<string, DefinicionEstado> = {
  /*
   * Citas: los 7 valores del CHECK de `citas.estado` (V1__esquema_base.sql) y de Cita.java; son los únicos que
   * acepta PATCH /citas/{id}/estado (brechas-api.md A.2). La reserva pública crea PENDIENTE_WEB.
   */
  PENDIENTE_WEB: { texto: 'Pendiente de confirmación', tono: 'accion' },
  PENDIENTE_DE_PAGO: { texto: 'Pendiente de pago', tono: 'accion' },
  CONFIRMADA_CON_ADELANTO: { texto: 'Confirmada con adelanto', tono: 'primario' },
  EXPIRADA: { texto: 'Expirada', tono: 'peligro' },
  PROGRAMADA: { texto: 'Programada', tono: 'neutro' },
  CONFIRMADA: { texto: 'Confirmada', tono: 'primario' },
  EN_SALA: { texto: 'En sala de espera', tono: 'accion' },
  EN_ATENCION: { texto: 'En atención', tono: 'villamax' },
  ATENDIDA: { texto: 'Atendida', tono: 'primario' },
  CANCELADA: { texto: 'Cancelada', tono: 'peligro' },
  NO_ASISTIO: { texto: 'No asistió', tono: 'peligro' },
  /* Transferencias de inventario */
  PENDIENTE: { texto: 'Pendiente', tono: 'accion' },
  EN_TRANSITO: { texto: 'En tránsito', tono: 'villamax' },
  RECIBIDO: { texto: 'Recibido', tono: 'primario' },
  CANCELADO: { texto: 'Cancelado', tono: 'peligro' },
}

/** Convierte "PENDIENTE_DE_PAGO" en "Pendiente de pago" para estados aún no mapeados. */
export function humanizarEstado(estado: string): string {
  const texto = estado.replace(/_/g, ' ').toLocaleLowerCase('es-PE').trim()
  return texto.charAt(0).toLocaleUpperCase('es-PE') + texto.slice(1)
}

export function definicionEstado(
  estado: string,
  mapa: Record<string, DefinicionEstado> = ESTADOS,
): DefinicionEstado {
  return mapa[estado] ?? { texto: humanizarEstado(estado), tono: 'neutro' }
}
