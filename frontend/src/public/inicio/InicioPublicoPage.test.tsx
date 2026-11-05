import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'

async function renderEn(ruta = '/') {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
}

describe('A1 · Inicio público (Landing page)', () => {
  it('renderiza el hero principal, claims de marca y llamadas a la acción', async () => {
    await renderEn('/')

    expect(
      screen.getByRole('heading', { name: /Odontología humana, honesta y de alta precisión/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Reserva tu cita en línea/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ver catálogo de servicios/i })).toBeInTheDocument()
  })

  it('muestra las 3 sedes oficiales alineadas con las especificaciones v2', async () => {
    await renderEn('/')

    expect(screen.getByRole('heading', { name: 'Villadent Chilca' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Villadent Huancayo Centro' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Villamax Chilca' })).toBeInTheDocument()
  })

  it('muestra las especialidades destacadas y el bloque dual Villadent & Villamax', async () => {
    await renderEn('/')

    expect(screen.getByRole('heading', { name: 'Villadent Odontología' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Villamax Radiología' })).toBeInTheDocument()
    expect(screen.getAllByText(/Ortodoncia & Brackets/i).length).toBeGreaterThan(0)
    expect(screen.getByText(/Odontopediatría con Calidez/i)).toBeInTheDocument()
  })
})
