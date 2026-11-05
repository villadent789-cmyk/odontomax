/**
 * /reserva/:codigo. Tras reservar en esta pestaña muestra el ticket (A5, parte existente). No hay endpoint para leer
 * un ticket por código sin DNI, así que en otro caso muestra la consulta (A6) con el código ya escrito.
 */
import { ConsultaReservaPage } from './ConsultaReservaPage'
import { leerTicket } from './ticket-local'
import { TicketConfirmacion } from './TicketConfirmacion'

export function ReservaPage({ codigo }: { codigo: string }) {
  const ticket = leerTicket(codigo)
  if (ticket) return <TicketConfirmacion ticket={ticket} />
  return <ConsultaReservaPage codigoInicial={codigo} />
}
