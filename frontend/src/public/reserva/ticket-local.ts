/**
 * Último ticket emitido en esta pestaña. No hay `GET` del ticket por código (la consulta exige DNI), así que la
 * confirmación (/reserva/:codigo) lo lee de aquí tras reservar. El ticket no contiene datos personales (el backend
 * no devuelve nombre, DNI ni teléfono). Si sessionStorage no está disponible, se guarda solo en memoria.
 */
import type { TicketReserva } from '@/shared/api'

const PREFIJO = 'odontomax.reserva.'
const memoria = new Map<string, TicketReserva>()

function clave(codigo: string) {
  return `${PREFIJO}${codigo.toUpperCase()}`
}

export function guardarTicket(ticket: TicketReserva) {
  memoria.set(clave(ticket.codigoTicket), ticket)
  try {
    sessionStorage.setItem(clave(ticket.codigoTicket), JSON.stringify(ticket))
  } catch {
    /* sin almacenamiento: queda en memoria */
  }
}

export function leerTicket(codigo: string): TicketReserva | null {
  const enMemoria = memoria.get(clave(codigo))
  if (enMemoria) return enMemoria
  try {
    const texto = sessionStorage.getItem(clave(codigo))
    if (!texto) return null
    const ticket = JSON.parse(texto) as TicketReserva
    return typeof ticket?.codigoTicket === 'string' ? ticket : null
  } catch {
    return null
  }
}

/** Solo para tests. */
export function _olvidarTickets() {
  memoria.clear()
}
