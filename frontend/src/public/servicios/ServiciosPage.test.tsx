import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { API, HttpResponse, servidor } from '@/test/msw'

async function renderEn(ruta = '/servicios') {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
}

describe('A2 · Servicios Odontológicos Villadent', () => {
  it('renderiza el título de catálogo, buscador y llamadas a reservar', async () => {
    await renderEn('/servicios')

    expect(screen.getByRole('heading', { name: 'Servicios Odontológicos Villadent' })).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/Buscar por tratamiento o especialidad/i)).toBeInTheDocument()
  })

  it('muestra el banner de estudios radiológicos Villamax 3D', async () => {
    await renderEn('/servicios')

    expect(screen.getByRole('heading', { name: /¿Requieres Tomografía 3D o Radiografía Panorámica\?/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ver estudios Villamax/i })).toBeInTheDocument()
  })

  it('muestra los tratamientos en secciones por especialidad, con el precio «Desde»', async () => {
    servidor.use(
      http.get(`${API}/reservas-publicas/catalogo/servicios`, () =>
        HttpResponse.json([
          { id: 'tar-1', nombre: 'Restauración LISS simple', especialidad: 'Restauraciones', precio: 60, duracionMin: 30, activo: true, orden: 1 },
          { id: 'tar-2', nombre: 'Restauración LISS compuesta', especialidad: 'Restauraciones', precio: 70, duracionMin: 40, activo: true, orden: 2 },
          { id: 'tar-3', nombre: 'Exodoncia de molares', especialidad: 'Exodoncias', precio: 60, duracionMin: 40, activo: true, orden: 3 },
        ]),
      ),
    )
    await renderEn('/servicios')

    const restauraciones = await screen.findByRole('region', { name: /Restauraciones/ })
    expect(within(restauraciones).getByText('Restauración LISS simple')).toBeInTheDocument()
    expect(within(restauraciones).getByText('Restauración LISS compuesta')).toBeInTheDocument()
    expect(within(restauraciones).getByText('Desde S/ 60.00')).toBeInTheDocument()
    expect(within(restauraciones).queryByText('Exodoncia de molares')).not.toBeInTheDocument()

    const exodoncias = screen.getByRole('region', { name: /Exodoncias/ })
    expect(within(exodoncias).getByText('Exodoncia de molares')).toBeInTheDocument()
  })
})
