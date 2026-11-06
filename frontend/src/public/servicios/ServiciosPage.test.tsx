import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { WHATSAPP_VILLADENT, enlaceWhatsApp } from '@/public/contacto'
import { crearRouter } from '@/routes/router'
import { TRATAMIENTOS_GENERALES } from './tratamientosGenerales'

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
  it('muestra el título y un tratamiento general por tarjeta, con su descripción', async () => {
    await renderEn('/servicios')

    expect(screen.getByRole('heading', { name: 'Servicios Odontológicos Villadent' })).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(TRATAMIENTOS_GENERALES.length)
    for (const t of TRATAMIENTOS_GENERALES) {
      expect(screen.getByRole('heading', { name: t.titulo })).toBeInTheDocument()
      expect(screen.getByText(t.descripcion)).toBeInTheDocument()
    }
  })

  it('los tratamientos se muestran en secciones, y están los que pidió la clínica', async () => {
    await renderEn('/servicios')

    for (const seccion of ['Diagnóstico y prevención', 'Restauración, endodoncia y cirugía', 'Prótesis y rehabilitación', 'Ortodoncia y estética']) {
      expect(screen.getByRole('region', { name: seccion })).toBeInTheDocument()
    }
    for (const titulo of ['Profilaxis (limpieza dental)', 'Fluorización', 'Endodoncia', 'Coronas y puentes', 'Prótesis removibles y totales', 'Implante dental', 'Blanqueamiento dental']) {
      expect(screen.getByRole('heading', { name: titulo })).toBeInTheDocument()
    }
  })

  it('hay una sola endodoncia', async () => {
    await renderEn('/servicios')

    expect(screen.getAllByRole('heading', { name: /Endodoncia/ })).toHaveLength(1)
  })

  it('cada tarjeta lleva «Reservar cita», que abre WhatsApp con el mensaje del tratamiento', async () => {
    await renderEn('/servicios')

    const botones = screen.getAllByRole('link', { name: /Reservar cita de .* por WhatsApp/ })
    expect(botones).toHaveLength(TRATAMIENTOS_GENERALES.length)
    const ortodoncia = screen.getByRole('link', { name: 'Reservar cita de Ortodoncia por WhatsApp' })
    expect(ortodoncia).toHaveAttribute('href', enlaceWhatsApp('Hola Villadent, quiero reservar una cita para Ortodoncia.'))
    expect(ortodoncia.getAttribute('href')).toContain(`https://wa.me/${WHATSAPP_VILLADENT}?text=`)
    expect(ortodoncia).toHaveAttribute('target', '_blank')
  })

  it('no muestra precios ni el catálogo detallado por procedimiento', async () => {
    await renderEn('/servicios')

    expect(screen.queryByText(/Desde S\//)).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText(/Buscar por tratamiento/i)).not.toBeInTheDocument()
  })

  it('muestra el banner de estudios radiológicos Villamax 3D', async () => {
    await renderEn('/servicios')

    expect(screen.getByRole('heading', { name: /¿Requieres Tomografía 3D o Radiografía Panorámica\?/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Ver estudios Villamax/i })).toBeInTheDocument()
  })
})
