import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { buscarEstudio, ESTUDIOS_VILLAMAX } from './estudiosVillamax'

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

describe('Explicación de cada estudio de Villamax', () => {
  it('muestra título, introducción, WhatsApp de Villamax y los datos importantes', async () => {
    const e = buscarEstudio('tomografia')!
    await renderEn('/villamax/tomografia')

    expect(await screen.findByRole('heading', { level: 1, name: e.titulo })).toBeInTheDocument()
    expect(screen.getByText(e.introduccion)).toBeInTheDocument()
    // El botón del menú y el de la página: los dos van al WhatsApp de Villamax.
    const consultar = screen.getAllByRole('link', { name: /Consultar por WhatsApp/ })
    expect(consultar.length).toBeGreaterThanOrEqual(2)
    for (const enlace of consultar) expect(enlace.getAttribute('href')).toContain('https://wa.me/51983246878?text=')

    const datos = screen.getByRole('region', { name: 'Datos importantes' })
    for (const dato of e.datos) expect(within(datos).getByText(dato)).toBeInTheDocument()
    expect(within(datos).getByText(e.nota)).toBeInTheDocument()
  })

  it('desde la tarjeta de la lista se llega a la explicación del estudio', async () => {
    await renderEn('/villamax')
    await userEvent.click(await screen.findByRole('link', { name: 'Más información sobre Radiografía cefalométrica' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Radiografía cefalométrica' })).toBeInTheDocument()
  })

  it('un estudio que no existe ofrece volver a la lista', async () => {
    await renderEn('/villamax/inventado')
    expect(await screen.findByText('No encontramos ese estudio')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver todos los estudios' })).toBeInTheDocument()
  })

  it('todos los estudios tienen explicación completa y sin precios', () => {
    for (const e of ESTUDIOS_VILLAMAX) {
      expect(e.datos.length).toBeGreaterThanOrEqual(3)
      expect([e.introduccion, ...e.datos, e.nota].join(' ')).not.toMatch(/S\/|soles|gratis|garantiz/i)
    }
  })
})
