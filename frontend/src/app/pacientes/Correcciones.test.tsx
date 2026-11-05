import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, HttpResponse, servidor } from '@/test/msw'
import { CorreccionesPendientes } from './CorreccionesPendientes'
import { SolicitarCorreccionModal } from './SolicitarCorreccionModal'
import { cambiosDe, type SolicitudCorreccion } from './api-correcciones'

function conCliente(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

const SOLICITUD: SolicitudCorreccion = {
  id: 's-1',
  pacienteId: 'p-1',
  nombresAnterior: 'Ana',
  apellidosAnterior: 'Perez',
  dniAnterior: '12345678',
  nombresNuevo: 'Anna',
  motivo: 'Error al escribir',
  estado: 'PENDIENTE',
  solicitadoPorNombre: 'Doctor A',
}

describe('Correcciones de datos del paciente', () => {
  it('cambiosDe lista solo lo que cambia', () => {
    expect(cambiosDe(SOLICITUD)).toEqual(['Nombres: Ana → Anna'])
    expect(cambiosDe({ ...SOLICITUD, nombresNuevo: undefined, dniNuevo: '87654321' })).toEqual(['DNI: 12345678 → 87654321'])
  })

  it('el doctor pide la corrección con motivo y se envía solo lo que cambió', async () => {
    const enviados: unknown[] = []
    servidor.use(
      http.post(`${API}/pacientes/p-1/solicitudes-correccion`, async ({ request }) => {
        enviados.push(await request.json())
        return HttpResponse.json({ ...SOLICITUD }, { status: 201 })
      }),
    )
    conCliente(
      <SolicitarCorreccionModal abierto alCerrar={() => {}} pacienteId="p-1" actual={{ nombres: 'Ana', apellidos: 'Perez', dni: '12345678' }} />,
    )
    const dialogo = within(await screen.findByRole('dialog'))
    await userEvent.click(dialogo.getByRole('button', { name: 'Enviar solicitud' }))
    expect(await dialogo.findByText('Cambia al menos un dato respecto a lo que figura ahora.')).toBeInTheDocument()

    await userEvent.clear(dialogo.getByLabelText('Nombres'))
    await userEvent.type(dialogo.getByLabelText('Nombres'), 'Anna')
    await userEvent.click(dialogo.getByRole('button', { name: 'Enviar solicitud' }))
    expect(await dialogo.findByText('Explica el motivo de la corrección.')).toBeInTheDocument()

    await userEvent.type(dialogo.getByLabelText(/Motivo de la corrección/), 'Error al escribir')
    await userEvent.click(dialogo.getByRole('button', { name: 'Enviar solicitud' }))
    await waitFor(() => expect(enviados).toEqual([{ nombres: 'Anna', motivo: 'Error al escribir' }]))
  })

  it('el dueño ve las pendientes y las aprueba', async () => {
    const resueltas: string[] = []
    servidor.use(
      http.get(`${API}/pacientes/solicitudes-correccion/pendientes`, () => HttpResponse.json([SOLICITUD])),
      http.post(`${API}/pacientes/solicitudes-correccion/:id/aprobar`, ({ params }) => {
        resueltas.push(String(params.id))
        return HttpResponse.json({ ...SOLICITUD, estado: 'APROBADA' })
      }),
    )
    conCliente(<CorreccionesPendientes />)
    expect(await screen.findByText('Nombres: Ana → Anna')).toBeInTheDocument()
    expect(screen.getByText(/Motivo: Error al escribir/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Aprobar' }))
    await waitFor(() => expect(resueltas).toEqual(['s-1']))
  })

  it('sin pendientes no muestra nada', async () => {
    servidor.use(http.get(`${API}/pacientes/solicitudes-correccion/pendientes`, () => HttpResponse.json([])))
    const { container } = conCliente(<CorreccionesPendientes />)
    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })
})
