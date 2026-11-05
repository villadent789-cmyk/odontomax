import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { AbrirCajaModal } from './AbrirCajaModal'

function montar(props: Partial<React.ComponentProps<typeof AbrirCajaModal>> = {}) {
  const alCerrar = vi.fn()
  const alGuardar = vi.fn()
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <AbrirCajaModal abierto alCerrar={alCerrar} alGuardar={alGuardar} sedeId="sede-chilca" sedeNombre="Villadent Chilca" {...props} />
    </QueryClientProvider>,
  )
  return { alCerrar, alGuardar }
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Abrir turno de caja', () => {
  it('si ya hay una caja abierta muestra el motivo real que envía el backend (409), no un error genérico', async () => {
    servidor.use(
      http.post(`${API}/caja/abrir`, () =>
        errorBackend(409, 'Ya existe una caja activa abierta para esta sede. Cierre la sesión anterior primero.'),
      ),
    )
    const { alCerrar } = montar()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar y Abrir Turno' }))

    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('No se pudo abrir el turno de caja')
    expect(alerta).toHaveTextContent('Ya existe una caja activa abierta para esta sede. Cierre la sesión anterior primero.')
    expect(alerta).not.toHaveTextContent('error interno')
    expect(alCerrar).not.toHaveBeenCalled()
  })

  it('al abrir con éxito avisa y cierra el modal', async () => {
    servidor.use(http.post(`${API}/caja/abrir`, () => HttpResponse.json({ id: 'caja-1', sedeId: 'sede-chilca', estado: 'ABIERTA' })))
    const { alCerrar, alGuardar } = montar()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar y Abrir Turno' }))
    await waitFor(() => expect(alGuardar).toHaveBeenCalledTimes(1))
    expect(alCerrar).toHaveBeenCalled()
  })

  it('no muestra fondo de apertura y envía siempre cero', async () => {
    let montoApertura: number | undefined
    servidor.use(
      http.post(`${API}/caja/abrir`, async ({ request }) => {
        const body = await request.json() as { montoApertura: number }
        montoApertura = body.montoApertura
        return HttpResponse.json({})
      }),
    )
    montar()
    expect(screen.queryByLabelText(/Monto Inicial/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar y Abrir Turno' }))
    await waitFor(() => expect(montoApertura).toBe(0))
  })
})
