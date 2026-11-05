import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { API, HttpResponse, servidor } from '@/test/msw'
import type { OdontogramaRegistroDTO } from './api-odontograma'
import { OdontogramaVisual } from './OdontogramaVisual'

const PACIENTE = 'p0000000-0000-4000-8000-00000000000a'

const registro = (piezaFdi: number, estado: string, caras: string[] = []): OdontogramaRegistroDTO => ({
  id: `r-${piezaFdi}-${estado}`,
  pacienteId: PACIENTE,
  piezaFdi,
  estado,
  caras,
  activo: true,
  registradoEn: '2026-10-30T10:00:00Z',
})

function montar(registros: OdontogramaRegistroDTO[]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <OdontogramaVisual pacienteId={PACIENTE} registros={registros} denticion="PERMANENTE" />
    </QueryClientProvider>,
  )
}

describe('Odontograma con varios hallazgos por pieza', () => {
  it('muestra a la vez los hallazgos de la misma pieza', () => {
    montar([registro(16, 'caries', ['O']), registro(16, 'corona_mal_estado'), registro(26, 'restauracion', ['M'])])

    expect(screen.getAllByText('CARIES').length).toBeGreaterThan(0)
    expect(screen.getAllByText('CD').length).toBeGreaterThan(0)
    expect(screen.getAllByText('R').length).toBeGreaterThan(0)
  })

  it('quita solo el hallazgo marcado y deja los demás de la pieza', async () => {
    const cuerpos: Record<string, unknown>[] = []
    servidor.use(
      http.post(`${API}/odontograma/pacientes/:id/pieza`, async ({ request }) => {
        cuerpos.push((await request.json()) as Record<string, unknown>)
        return HttpResponse.json({ id: 'x' }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    montar([registro(16, 'caries', ['O']), registro(16, 'corona_mal_estado')])

    await user.click(screen.getByTitle('Pieza 16 - Clic para marcar completa'))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0]).toMatchObject({ piezaFdi: 16, estado: 'caries', activo: false })
    // La corona en mal estado sigue en la pieza.
    expect(screen.getAllByText('CD').length).toBeGreaterThan(0)
    expect(screen.queryAllByText('CARIES')).toHaveLength(0)
  })

  it('un hallazgo nuevo se suma a los que ya tiene la pieza', async () => {
    const cuerpos: Record<string, unknown>[] = []
    servidor.use(
      http.post(`${API}/odontograma/pacientes/:id/pieza`, async ({ request }) => {
        cuerpos.push((await request.json()) as Record<string, unknown>)
        return HttpResponse.json({ id: 'x' }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    montar([registro(16, 'corona_mal_estado')])

    await user.click(screen.getByRole('button', { name: /Ver todos los hallazgos/ }))
    await user.click(screen.getByTitle('Sellante de fosas y fisuras (extra)'))
    await user.click(screen.getByTitle('Pieza 16 - Clic para marcar completa'))

    await waitFor(() => expect(cuerpos).toEqual([expect.objectContaining({ piezaFdi: 16, estado: 'sellante', caras: ['O'] })]))
    expect(screen.getAllByText('CD').length).toBeGreaterThan(0)
    expect(screen.getAllByText('SELL').length).toBeGreaterThan(0)
  })

  it('la paleta muestra de entrada lo de uso diario y «Ver todos» despliega el resto', async () => {
    const user = userEvent.setup()
    montar([])

    expect(screen.getByTitle('Se pinta de rojo siguiendo su forma')).toBeInTheDocument() // caries
    expect(screen.queryByTitle('Sellante de fosas y fisuras (extra)')).not.toBeInTheDocument()
    expect(screen.queryByTitle('Triángulo azul en la norma')).not.toBeInTheDocument() // diente en clavija

    await user.click(screen.getByRole('button', { name: /Ver todos los hallazgos/ }))
    expect(screen.getByTitle('Sellante de fosas y fisuras (extra)')).toBeInTheDocument()
    expect(screen.getByTitle('Triángulo azul en la norma')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ver menos hallazgos' }))
    expect(screen.queryByTitle('Sellante de fosas y fisuras (extra)')).not.toBeInTheDocument()
  })

  it('el resumen cuenta con los nombres actuales y las Especificaciones explican cada hallazgo', async () => {
    montar([registro(16, 'caries', ['O']), registro(16, 'corona_cmc'), registro(26, 'endodoncia'), registro(36, 'implante')])

    const resumen = screen.getByText('Resumen de Piezas:').parentElement as HTMLElement
    expect(within(resumen).getByText(/Caries:/).textContent).toContain('1')
    expect(within(resumen).getByText(/Coronas:/).textContent).toContain('1')
    expect(within(resumen).getByText(/Tratamientos pulpares:/).textContent).toContain('1')
    expect(within(resumen).getByText(/Implantes:/).textContent).toContain('1')

    const espec = screen.getByRole('region', { name: 'Especificaciones' })
    expect(espec).toHaveTextContent('16: Caries dental, cara oclusal; Corona metal cerámica (CMC)')
    expect(espec).toHaveTextContent('26: Tratamiento de conductos (TC)')
  })

  it('«Sano» limpia la pieza entera', async () => {
    const cuerpos: Record<string, unknown>[] = []
    servidor.use(
      http.post(`${API}/odontograma/pacientes/:id/pieza`, async ({ request }) => {
        cuerpos.push((await request.json()) as Record<string, unknown>)
        return HttpResponse.json({ id: 'x' }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    montar([registro(16, 'caries', ['O']), registro(16, 'corona_mal_estado')])

    await user.click(screen.getByRole('button', { name: /Pieza sana/ }))
    await user.click(screen.getByTitle('Pieza 16 - Clic para marcar completa'))

    await waitFor(() => expect(cuerpos).toEqual([expect.objectContaining({ piezaFdi: 16, estado: 'sano' })]))
    expect(screen.queryAllByText('CD')).toHaveLength(0)
    // La pieza queda visible como sana, no en blanco.
    expect(screen.getAllByText('SANO').length).toBeGreaterThan(0)
  })
})

describe('Odontograma imprimible', () => {
  it('lleva el nombre completo, las dos fechas y solo las piezas que cambiaron', async () => {
    const { documentoOdontograma } = await import('./documentoOdontograma')
    const html = documentoOdontograma({
      pacienteNombre: 'María Quispe Huamán',
      pacienteDni: '41234567',
      sedeNombre: 'Villadent Chilca',
      motivo: 'Dolor de muela <b>',
      capturas: [
        { titulo: 'Antes', fecha: '2026-06-10T15:00:00Z', piezas: [{ piezaFdi: 16, estado: 'caries', caras: ['O'] }, { piezaFdi: 26, estado: 'corona' }] },
        { titulo: 'Después', fecha: '2026-07-20T15:00:00Z', piezas: [{ piezaFdi: 16, estado: 'restauracion', caras: ['O'] }, { piezaFdi: 26, estado: 'corona' }] },
      ],
    })
    expect(html).toContain('Odontograma: antes y después')
    expect(html).toContain('María Quispe Huamán')
    expect(html).toContain('Dolor de muela &lt;b&gt;')
    // Solo la 16 cambió: la 26 sigue igual y no entra en «Piezas que cambiaron».
    const cambios = html.slice(html.indexOf('Piezas que cambiaron'))
    expect(cambios).toContain('<td>16</td>')
    expect(cambios).not.toContain('<td>26</td>')
    expect(cambios).toContain('Restauración de resina')
  })
})
