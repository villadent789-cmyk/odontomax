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
      screen.getByRole('heading', { name: /Sonrisas saludables, confianza para toda la vida/i }),
    ).toBeInTheDocument()
    const reservar = screen.getByRole('link', { name: /Agenda tu cita por WhatsApp/i })
    expect(reservar.getAttribute('href')).toMatch(/^https:\/\/wa\.me\/51912192005\?text=/)
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
    expect(screen.getByRole('heading', { name: 'Ortodoncia' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Implante dental' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Reservar cita de Ortodoncia por WhatsApp' })).toBeInTheDocument()
  })
})
