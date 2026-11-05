import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import {
  consultaReserva,
  errorBackend,
  limiteReserva,
  manejadores,
  respuestas,
  servidor,
  ticketReserva,
} from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import { _olvidarTickets, guardarTicket } from './ticket-local'

beforeEach(() => {
  sessionStorage.clear()
  _olvidarTickets()
  servidor.use(manejadores.sedes())
})

async function buscar(dni = '45908123') {
  await userEvent.type(screen.getByLabelText('DNI del paciente'), dni)
  await userEvent.click(screen.getByRole('button', { name: 'Buscar reserva' }))
}

describe('A6 · Consulta tu reserva', { timeout: 15_000 }, () => {
  it('encontrada: envía { ticket, dni } y muestra estado, sede, fecha y turno', async () => {
    let cuerpo: unknown
    servidor.use(
      manejadores.consultar((c) => {
        cuerpo = c
        return respuestas.consulta(consultaReserva({ estado: 'CONFIRMADA', turno: 'TARDE' }))
      }),
    )
    // El código llega de la URL y se normaliza a mayúsculas.
    await renderRuta('/reserva/om-7k9m2p4x')
    expect(screen.getByRole('heading', { level: 1, name: 'Consulta el estado de tu reserva' })).toBeInTheDocument()
    expect(screen.getByLabelText('Código de reserva')).toHaveValue('OM-7K9M2P4X')
    await buscar()

    const resultado = await screen.findByRole('heading', { name: 'OM-7K9M2P4X' })
    expect(resultado).toHaveFocus()
    expect(cuerpo).toEqual({ ticket: 'OM-7K9M2P4X', dni: '45908123' })
    const region = screen.getByRole('region', { name: 'Resultado de la búsqueda' })
    expect(within(region).getByText('Confirmada')).toHaveAttribute('data-estado', 'CONFIRMADA')
    // Nombre de la sede desde GET /sedes (el backend devuelve el id como sedeNombre).
    expect(await within(region).findByText('Sede Chilca')).toBeInTheDocument()
    expect(within(region).getByText('Viernes 09/10/2026')).toBeInTheDocument()
    expect(within(region).getByText('Turno: Tarde')).toBeInTheDocument()
  })

  it('PENDIENTE_WEB: explica que falta la confirmación de la clínica', async () => {
    servidor.use(manejadores.consultar(() => respuestas.consulta()))
    await renderRuta('/reserva?codigo=OM-7K9M2P4X')
    await buscar()
    expect(await screen.findByText('Pendiente de confirmación')).toBeInTheDocument()
    expect(screen.getByText('Tu reserva aún no está confirmada')).toBeInTheDocument()
  })

  it('valida código obligatorio y DNI de 8 dígitos (ConsultaReservaRequestDTO)', async () => {
    await renderRuta('/reserva')
    await buscar('1234')
    expect(await screen.findByText('Ingresa el código de tu reserva.')).toBeInTheDocument()
    expect(screen.getByText('El DNI debe tener 8 dígitos numéricos.')).toBeInTheDocument()
  })

  it('no encontrada: muestra el mensaje del backend', async () => {
    const mensaje = 'No se encontró ninguna reserva activa con el ticket y DNI proporcionados.'
    servidor.use(manejadores.consultar(() => errorBackend(400, mensaje)))
    await renderRuta('/reserva/OM-AAAAAAAA')
    await buscar()
    const alerta = await screen.findByRole('alert')
    expect(within(alerta).getByText('No encontramos tu reserva')).toBeInTheDocument()
    expect(alerta).toHaveTextContent(mensaje)
    expect(within(alerta).getByRole('link', { name: 'Hacer una reserva nueva' })).toHaveAttribute('href', '/reservar')
  })

  it('429: muestra el mensaje de límite del backend', async () => {
    const mensaje = 'Ha superado el límite de consultas permitidas. Intente más tarde.'
    servidor.use(manejadores.consultar(() => limiteReserva(mensaje)))
    await renderRuta('/reserva/OM-7K9M2P4X')
    await buscar()
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Demasiadas consultas')
    expect(alerta).toHaveTextContent(mensaje)
  })
})

describe('A5 · Ticket tras reservar', () => {
  it('muestra el ticket guardado, el aviso de pago en clínica y el enlace a la consulta', async () => {
    guardarTicket(ticketReserva())
    await renderRuta('/reserva/OM-7K9M2P4X')
    expect(screen.getByRole('heading', { level: 1, name: '¡Tu reserva está registrada!' })).toHaveFocus()
    expect(screen.getByText('Pendiente de confirmación')).toBeInTheDocument()
    expect(screen.getByText(/La clínica se comunicará contigo vía WhatsApp/)).toBeInTheDocument()
    expect(await screen.findByText('Sede Chilca')).toBeInTheDocument()
    expect(screen.getByText('Mañana')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pago en la clínica' })).toBeInTheDocument()
    expect(screen.queryByText(/Yape/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Consultar el estado de mi reserva/ })).toHaveAttribute(
      'href',
      '/reserva?codigo=OM-7K9M2P4X',
    )
  })

  it('copia el código y lo anuncia', async () => {
    guardarTicket(ticketReserva())
    const usuario = userEvent.setup()
    await renderRuta('/reserva/OM-7K9M2P4X')
    await usuario.click(screen.getByRole('button', { name: 'Copiar código OM-7K9M2P4X' }))
    expect(await screen.findByText('Código copiado.')).toBeInTheDocument()
    await expect(navigator.clipboard.readText()).resolves.toBe('OM-7K9M2P4X')
  })
})
