import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { API, HttpResponse, servidor } from '@/test/msw'

async function renderEn(ruta = '/villamax') {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
}

describe('A3 · Villamax Centro Radiológico 3D', () => {
  it('renderiza el título oficial, distintivos diagnósticos y reglas de atención', async () => {
    await renderEn('/villamax')

    expect(screen.getByRole('heading', { name: 'Villamax Centro Radiológico' })).toBeInTheDocument()
    expect(screen.getByText('Sin cita previa')).toBeInTheDocument()
    expect(screen.getByText('Pagas en recepción')).toBeInTheDocument()
    expect(screen.getByText('Directo a tu médico')).toBeInTheDocument()
  })

  it('lista los estudios del catálogo del cliente con su precio «Desde», no una lista fija', async () => {
    servidor.use(
      http.get(`${API}/reservas-publicas/catalogo/servicios`, () =>
        HttpResponse.json([
          { id: 'vmx-tomografia-3d', nombre: 'Tomografía 3D Cone Beam', especialidad: 'Radiología', precio: 190, activo: true, orden: 1 },
          { id: 'vmx-panoramica', nombre: 'Radiografía panorámica', especialidad: 'Radiología', precio: 50, activo: true, orden: 2 },
        ]),
      ),
    )
    await renderEn('/villamax')

    expect(await screen.findByRole('heading', { name: 'Tomografía 3D Cone Beam' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Radiografía panorámica' })).toBeInTheDocument()
    expect(screen.getByText('Desde S/ 190.00')).toBeInTheDocument()
    expect(screen.getByText('Desde S/ 50.00')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Telerradiografía/i })).not.toBeInTheDocument()
  })

  it('muestra la sección para odontólogos derivadores y la sede oficial Villamax Chilca', async () => {
    await renderEn('/villamax')

    expect(screen.getByRole('heading', { name: /¿Eres Odontólogo o Cirujano en Huancayo\?/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sede Villamax Chilca' })).toBeInTheDocument()
    expect(screen.getAllByText(/Parque de los Héroes/i).length).toBeGreaterThan(0)
  })
})
