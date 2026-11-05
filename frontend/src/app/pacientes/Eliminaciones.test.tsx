import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, HttpResponse, servidor } from '@/test/msw'
import { EliminacionesPendientes } from './EliminacionesPendientes'
import { SolicitarEliminacionModal } from './SolicitarEliminacionModal'
import type { SolicitudEliminacion } from './api-eliminaciones'

function conCliente(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

const SOLICITUD: SolicitudEliminacion = {
  id: 'e-1',
  tipo: 'RADIOGRAFIA',
  objetivoId: 'r-1',
  pacienteId: 'p-1',
  sedeId: 'sede-chilca-1',
  descripcion: 'Radiografía PANORAMICA (foto.jpg, 2026-11-05) de Ana Perez',
  motivo: 'Se subió la foto de otro paciente',
  estado: 'PENDIENTE',
  solicitadoPorNombre: 'Doctor A',
}

describe('Eliminaciones con autorización del dueño', () => {
  it('quien no es el dueño pide la eliminación con motivo y no se envía nada hasta que lo escribe', async () => {
    const enviados: unknown[] = []
    servidor.use(
      http.post(`${API}/solicitudes-eliminacion`, async ({ request }) => {
        enviados.push(await request.json())
        return HttpResponse.json({ ...SOLICITUD }, { status: 201 })
      }),
    )
    conCliente(<SolicitarEliminacionModal abierto alCerrar={() => {}} tipo="RADIOGRAFIA" objetivoId="r-1" descripcion="el estudio foto.jpg" />)
    const dialogo = within(await screen.findByRole('dialog'))
    expect(dialogo.getByText(/Solo el dueño puede eliminarlo/)).toBeInTheDocument()

    await userEvent.click(dialogo.getByRole('button', { name: 'Enviar solicitud' }))
    expect(await dialogo.findByText('Explica el motivo de la eliminación (mínimo 5 caracteres).')).toBeInTheDocument()
    expect(enviados).toEqual([])

    await userEvent.type(dialogo.getByLabelText(/Motivo de la eliminación/), 'Foto de otro paciente')
    await userEvent.click(dialogo.getByRole('button', { name: 'Enviar solicitud' }))
    await waitFor(() => expect(enviados).toEqual([{ tipo: 'RADIOGRAFIA', objetivoId: 'r-1', motivo: 'Foto de otro paciente' }]))
  })

  it('el dueño elimina el estudio al instante con su motivo (sin pedir autorización)', async () => {
    const borrados: string[] = []
    servidor.use(
      http.delete(`${API}/radiografias/r-1`, ({ request }) => {
        borrados.push(new URL(request.url).searchParams.get('motivo') ?? '')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    let eliminada = false
    conCliente(
      <SolicitarEliminacionModal
        abierto
        alCerrar={() => {}}
        tipo="RADIOGRAFIA"
        objetivoId="r-1"
        descripcion="el estudio foto.jpg"
        directo={{ pacienteId: 'p-1', alEliminar: () => { eliminada = true } }}
      />,
    )
    const dialogo = within(await screen.findByRole('dialog'))
    expect(dialogo.getByText(/Vas a eliminar el estudio foto.jpg de forma permanente/)).toBeInTheDocument()
    await userEvent.type(dialogo.getByLabelText(/Motivo de la eliminación/), 'Duplicada')
    await userEvent.click(dialogo.getByRole('button', { name: 'Eliminar' }))
    await waitFor(() => expect(borrados).toEqual(['Duplicada']))
    await waitFor(() => expect(eliminada).toBe(true))
  })

  it('el dueño ve las pendientes y las aprueba', async () => {
    const resueltas: string[] = []
    servidor.use(
      http.get(`${API}/solicitudes-eliminacion/pendientes`, () => HttpResponse.json([SOLICITUD])),
      http.post(`${API}/solicitudes-eliminacion/:id/aprobar`, ({ params }) => {
        resueltas.push(String(params.id))
        return HttpResponse.json({ ...SOLICITUD, estado: 'APROBADA' })
      }),
    )
    conCliente(<EliminacionesPendientes />)
    expect(await screen.findByText(/Radiografía PANORAMICA/)).toBeInTheDocument()
    expect(screen.getByText(/Motivo: Se subió la foto de otro paciente/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Aprobar y eliminar' }))
    await waitFor(() => expect(resueltas).toEqual(['e-1']))
  })

  it('sin pendientes no muestra nada', async () => {
    servidor.use(http.get(`${API}/solicitudes-eliminacion/pendientes`, () => HttpResponse.json([])))
    const { container } = conCliente(<EliminacionesPendientes />)
    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })
})
