import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { guardarSesion, limpiarSesion, type Rol, type Sesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'

const SEDE = { id: 'sede-1', nombre: 'Villadent Chilca', marca: 'VILLADENT' as const }

function sesionDe(rol: Rol): Sesion {
  return { token: 't', usuario: { id: 'u-1', nombre: 'Dra. Ana Torres', rol, sedes: [SEDE] }, sedeActivaId: SEDE.id }
}

async function renderEn(ruta: string) {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
  _reiniciarCacheSesion()
})
afterEach(() => limpiarSesion())

describe('Mi especialidad (el doctor edita la suya)', () => {
  it('el odontólogo la cambia desde el menú de su cuenta y se envía al backend', async () => {
    const enviados: unknown[] = []
    servidor.use(
      http.get(`${API}/especialistas`, () =>
        HttpResponse.json([{ id: 'e-1', usuarioId: 'u-1', nombre: 'Dra. Ana Torres', especialidad: 'Odontología General', sedes: ['sede-1'], activo: true }]),
      ),
      http.patch(`${API}/especialistas/mi-especialidad`, async ({ request }) => {
        enviados.push(await request.json())
        return HttpResponse.json({})
      }),
    )
    guardarSesion(sesionDe('ODONTOLOGO'))
    await renderEn('/app')
    await userEvent.click(await screen.findByRole('button', { name: /Menú de cuenta de Dra\. Ana Torres/ }))
    await userEvent.click(await screen.findByRole('menuitem', { name: 'Mi especialidad' }))

    const dialogo = within(await screen.findByRole('dialog'))
    const campo = await dialogo.findByLabelText('Especialidad')
    await waitFor(() => expect(campo).toHaveValue('Odontología General'))
    await userEvent.clear(campo)
    await userEvent.type(campo, 'Endodoncia')
    await userEvent.click(dialogo.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(enviados).toEqual([{ especialidad: 'Endodoncia' }]))
  })

  it('quien no es odontólogo no ve la opción', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    await renderEn('/app')
    await userEvent.click(await screen.findByRole('button', { name: /Menú de cuenta de Dra\. Ana Torres/ }))
    await screen.findByRole('menuitem', { name: 'Cambiar contraseña' })
    expect(screen.queryByRole('menuitem', { name: 'Mi especialidad' })).not.toBeInTheDocument()
  })
})
