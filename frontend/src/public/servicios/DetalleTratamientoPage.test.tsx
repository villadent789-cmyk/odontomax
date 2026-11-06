import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { enlaceWhatsApp } from '@/public/contacto'
import { crearRouter } from '@/routes/router'
import { buscarTratamiento, TRATAMIENTOS_GENERALES } from './tratamientosGenerales'

async function renderEn(ruta: string) {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
  return router
}

describe('Explicación de cada tratamiento', () => {
  it('muestra título, introducción, «Reservar cita» a WhatsApp y los datos importantes', async () => {
    const t = buscarTratamiento('endodoncia')!
    await renderEn('/servicios/endodoncia')

    expect(await screen.findByRole('heading', { level: 1, name: t.titulo })).toBeInTheDocument()
    expect(screen.getByText(t.introduccion)).toBeInTheDocument()

    const reservar = screen.getByRole('link', { name: /Reservar cita/ })
    expect(reservar).toHaveAttribute('href', enlaceWhatsApp(`Hola Villadent, quiero reservar una cita para ${t.titulo}.`))

    const datos = screen.getByRole('region', { name: 'Datos importantes' })
    for (const dato of t.datos) expect(within(datos).getByText(dato)).toBeInTheDocument()
    expect(within(datos).getByText(t.nota)).toBeInTheDocument()
  })

  it('desde la tarjeta de la lista se llega a la explicación del tratamiento', async () => {
    await renderEn('/servicios')
    await userEvent.click(await screen.findByRole('link', { name: 'Más información sobre Ortodoncia' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Ortodoncia' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Datos importantes' })).toBeInTheDocument()
  })

  it('un tratamiento que no existe ofrece volver a la lista', async () => {
    await renderEn('/servicios/inventado')
    expect(await screen.findByText('No encontramos ese tratamiento')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver todos los tratamientos' })).toBeInTheDocument()
  })

  it('todos los tratamientos tienen explicación completa y sin prometer precios', () => {
    for (const t of TRATAMIENTOS_GENERALES) {
      expect(t.introduccion.length).toBeGreaterThan(20)
      expect(t.datos.length).toBeGreaterThanOrEqual(3)
      expect(t.nota.length).toBeGreaterThan(20)
      expect([t.introduccion, ...t.datos, t.nota].join(' ')).not.toMatch(/S\/|soles|gratis|garantiz/i)
    }
  })
})
