import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import type { TratamientoDTO } from './api-tratamientos'
import { CambiarSesionesPlan } from './CambiarSesionesPlan'

const PLAN = {
  id: 't-1',
  nombre: 'Endodoncia',
  estado: 'EN_PROCESO',
  totalSesiones: 4,
  sesionesRealizadas: 1,
  cambiosSesiones: [
    { sesionesAntes: 3, sesionesDespues: 4, motivo: 'Conducto más complejo', usuarioNombre: 'Dra. Ríos', creadoEn: '2026-10-20T15:00:00Z' },
  ],
} as TratamientoDTO

function montar() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={qc}>
      <CambiarSesionesPlan plan={PLAN} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({ token: 't', usuario: { id: 'u-1', nombre: 'Dra. Ríos', rol: 'ODONTOLOGO', sedes: [{ id: 's', nombre: 'S', marca: 'VILLADENT' }] }, sedeActivaId: 's' })
})
afterEach(() => limpiarSesion())

describe('Cambiar las sesiones de un plan', () => {
  it('muestra los cambios anteriores con su motivo', () => {
    montar()
    expect(screen.getByText(/3 → 4 sesiones · Conducto más complejo · Dra\. Ríos/)).toBeInTheDocument()
  })

  it('no deja guardar sin motivo y manda el nuevo número con su motivo', async () => {
    let cuerpo: unknown
    servidor.use(
      http.patch(`${API}/tratamientos/:id`, async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...PLAN, totalSesiones: 1 })
      }),
    )
    const user = userEvent.setup()
    montar()

    await user.click(screen.getByRole('button', { name: 'Cambiar sesiones' }))
    const dialogo = within(await screen.findByRole('dialog'))
    await user.clear(dialogo.getByLabelText('Número de sesiones del plan'))
    await user.type(dialogo.getByLabelText('Número de sesiones del plan'), '1')
    await user.click(dialogo.getByRole('button', { name: 'Guardar cambio' }))
    expect(await dialogo.findByText(/Indica el motivo del cambio/)).toBeInTheDocument()
    expect(cuerpo).toBeUndefined()

    await user.type(dialogo.getByLabelText('Motivo del cambio *'), 'Tenía otro paciente agendado')
    await user.click(dialogo.getByRole('button', { name: 'Guardar cambio' }))
    await waitFor(() => expect(cuerpo).toEqual({ totalSesiones: 1, motivoCambioSesiones: 'Tenía otro paciente agendado' }))
  })

  it('no permite bajar de las sesiones ya realizadas', async () => {
    const user = userEvent.setup()
    montar()
    await user.click(screen.getByRole('button', { name: 'Cambiar sesiones' }))
    const dialogo = within(await screen.findByRole('dialog'))
    await user.clear(dialogo.getByLabelText('Número de sesiones del plan'))
    await user.type(dialogo.getByLabelText('Número de sesiones del plan'), '0')
    await user.type(dialogo.getByLabelText('Motivo del cambio *'), 'Cualquier motivo')
    await user.click(dialogo.getByRole('button', { name: 'Guardar cambio' }))
    expect(await dialogo.findByText(/al menos 1 sesión/)).toBeInTheDocument()
  })
})
