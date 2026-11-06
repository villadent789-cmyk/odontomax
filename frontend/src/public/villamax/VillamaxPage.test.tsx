import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { ESTUDIOS_VILLAMAX } from './estudiosVillamax'

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

  it('lista los estudios generales de Villamax, con su explicación y sin precios', async () => {
    await renderEn('/villamax')

    for (const e of ESTUDIOS_VILLAMAX) {
      expect(screen.getByRole('heading', { name: e.titulo })).toBeInTheDocument()
      expect(screen.getByText(e.descripcion)).toBeInTheDocument()
      expect(screen.getByRole('link', { name: `Más información sobre ${e.titulo}` })).toBeInTheDocument()
    }
    expect(screen.queryByText(/Desde S\//)).not.toBeInTheDocument()
    expect(screen.queryByText(/S\/ ?\d/)).not.toBeInTheDocument()
  })

  it('cada estudio abre WhatsApp de Villamax con el mensaje de ese estudio ya escrito', async () => {
    await renderEn('/villamax')

    const panoramica = screen.getByRole('link', { name: 'Consultar por Radiografía panorámica en WhatsApp' })
    expect(panoramica).toHaveAttribute('href', 'https://wa.me/51983246878?text=' + encodeURIComponent('Hola, deseo información y agendar una Radiografía panorámica.'))
    expect(panoramica).toHaveAttribute('target', '_blank')
    const tomografia = screen.getByRole('link', { name: 'Consultar por Tomografía 3D Cone Beam en WhatsApp' })
    expect(tomografia).toHaveAttribute('href', 'https://wa.me/51983246878?text=' + encodeURIComponent('Hola, deseo información y agendar una Tomografía 3D Cone Beam.'))
    const pequenas = screen.getByRole('link', { name: 'Consultar por Radiografías pequeñas en WhatsApp' })
    expect(decodeURIComponent(pequenas.getAttribute('href') ?? '')).toContain('radiografías pequeñas (periapical, bite wing u oclusal)')
  })

  it('el botón del menú y el flotante de WhatsApp usan el número de Villamax, no el de Villadent', async () => {
    await renderEn('/villamax')

    const flotante = screen.getByRole('link', { name: 'Escribir a Villamax por WhatsApp' })
    expect(flotante.getAttribute('href')).toContain('https://wa.me/51983246878?text=')
    expect(screen.queryByRole('link', { name: 'Escribir a Villadent por WhatsApp' })).not.toBeInTheDocument()
  })

  it('muestra la sección para odontólogos derivadores y la sede oficial Villamax Chilca', async () => {
    await renderEn('/villamax')

    expect(screen.getByRole('heading', { name: /¿Eres Odontólogo o Cirujano en Huancayo\?/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Sede Villamax Chilca' })).toBeInTheDocument()
    expect(screen.getAllByText(/Parque de los Héroes/i).length).toBeGreaterThan(0)
  })
})
