import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import type { Presupuesto } from './api-presupuestos'
import { documentoPresupuesto } from './documentoPresupuesto'
import { PresupuestosPaciente } from './PresupuestosPaciente'

const VIGENTE: Presupuesto = {
  id: 'pr-1',
  pacienteId: 'p-1',
  sedeId: 'sede-chilca',
  especialistaNombre: 'Dra. Ríos',
  motivo: 'Vino por una curación',
  estado: 'VIGENTE',
  total: 510,
  totalAceptado: 0,
  creadoEn: '2026-10-29T15:00:00Z',
  items: [
    { id: 'i-1', nombre: 'Curación', piezaFdi: 16, gravedad: 'MODERADO', cantidad: 2, precioUnitario: 80, subtotal: 160, aceptado: false },
    { id: 'i-2', nombre: 'Endodoncia <b>', piezaFdi: 26, gravedad: 'SEVERO', cantidad: 1, precioUnitario: 350, subtotal: 350, aceptado: false },
  ],
}

function montar() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={qc}>
      <PresupuestosPaciente pacienteId="p-1" pacienteNombre="Rosa Quispe Vera" pacienteDni="40111222" pacienteTelefono="964123456" sedeId="sede-chilca" sedeNombre="Villadent Chilca" />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Dra. Ríos', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Presupuestos del paciente', () => {
  it('muestra cada línea como piezas × costo y el total, y manda el resumen por WhatsApp', async () => {
    servidor.use(http.get(`${API}/presupuestos/paciente/:id`, () => HttpResponse.json([VIGENTE])))
    montar()

    expect(await screen.findByText('2 × S/ 80.00')).toBeInTheDocument()
    expect(screen.getByText('Total S/ 510.00')).toBeInTheDocument()
    const wa = screen.getByRole('link', { name: /WhatsApp/ })
    expect(wa).toHaveAttribute('href', expect.stringContaining('https://wa.me/51964123456?text='))
    expect(decodeURIComponent(wa.getAttribute('href')!)).toContain('Total: S/ 510.00')
  })

  it('crea el plan solo con lo que el paciente aceptó', async () => {
    let cuerpo: unknown
    servidor.use(
      http.get(`${API}/presupuestos/paciente/:id`, () => HttpResponse.json([VIGENTE])),
      http.post(`${API}/presupuestos/:id/aceptar`, async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...VIGENTE, estado: 'ACEPTADO', totalAceptado: 160 })
      }),
    )
    const user = userEvent.setup()
    montar()

    await user.click(await screen.findByLabelText('Aceptó Endodoncia <b>'))
    await user.click(screen.getByRole('button', { name: /Crear plan con lo aceptado/ }))

    await waitFor(() => expect(cuerpo).toMatchObject({ itemIds: ['i-1'] }))
  })

  it('un presupuesto nuevo se arma con piezas × costo por pieza según la gravedad', async () => {
    let cuerpo: { items?: unknown[] } | undefined
    servidor.use(
      http.get(`${API}/presupuestos/paciente/:id`, () => HttpResponse.json([])),
      http.get(`${API}/servicios`, () =>
        HttpResponse.json([{ id: 'srv-curacion', nombre: 'Curación', precio: 80, precioLeve: 50, precioModerado: 80, precioSevero: 120, activo: true, orden: 1 }]),
      ),
      http.get(`${API}/especialistas`, () => HttpResponse.json([{ id: 'e-1', nombre: 'Dra. Ríos', usuarioId: 'u-1', sedes: ['sede-chilca'], activo: true }])),
      http.post(`${API}/presupuestos`, async ({ request }) => {
        cuerpo = (await request.json()) as { items?: unknown[] }
        return HttpResponse.json({ ...VIGENTE, id: 'pr-2' }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    montar()

    await user.click(await screen.findByRole('button', { name: 'Nuevo presupuesto' }))
    const dialogo = within(await screen.findByRole('dialog'))
    await dialogo.findByRole('option', { name: 'Curación' })
    await user.selectOptions(dialogo.getByLabelText('Tratamiento 1'), 'srv-curacion')
    await user.selectOptions(dialogo.getByLabelText('Gravedad 1'), 'SEVERO')
    expect(dialogo.getByLabelText('Costo por pieza 1')).toHaveValue(120)
    await user.clear(dialogo.getByLabelText('Piezas 1'))
    await user.type(dialogo.getByLabelText('Piezas 1'), '3')
    expect(dialogo.getByText('Total del presupuesto: S/ 360.00')).toBeInTheDocument()
    await user.click(dialogo.getByRole('button', { name: 'Guardar presupuesto' }))

    await waitFor(() => expect(cuerpo?.items).toEqual([expect.objectContaining({ nombre: 'Curación', cantidad: 3, precioUnitario: 120, gravedad: 'SEVERO' })]))
  })

  it('«Otro caso de este tratamiento» duplica la línea para elegir otra gravedad y el total suma ambas', async () => {
    let cuerpo: { items?: unknown[] } | undefined
    servidor.use(
      http.get(`${API}/presupuestos/paciente/:id`, () => HttpResponse.json([])),
      http.get(`${API}/servicios`, () =>
        HttpResponse.json([{ id: 'srv-curacion', nombre: 'Curación', precio: 80, precioLeve: 50, precioModerado: 80, precioSevero: 120, activo: true, orden: 1 }]),
      ),
      http.get(`${API}/especialistas`, () => HttpResponse.json([{ id: 'e-1', nombre: 'Dra. Ríos', usuarioId: 'u-1', sedes: ['sede-chilca'], activo: true }])),
      http.post(`${API}/presupuestos`, async ({ request }) => {
        cuerpo = (await request.json()) as { items?: unknown[] }
        return HttpResponse.json({ ...VIGENTE, id: 'pr-3' }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    montar()

    await user.click(await screen.findByRole('button', { name: 'Nuevo presupuesto' }))
    const dialogo = within(await screen.findByRole('dialog'))
    await dialogo.findByRole('option', { name: 'Curación' })
    expect(dialogo.queryByRole('button', { name: /Otro caso del tratamiento 1/ })).not.toBeInTheDocument()
    await user.selectOptions(dialogo.getByLabelText('Tratamiento 1'), 'srv-curacion')
    await user.selectOptions(dialogo.getByLabelText('Gravedad 1'), 'LEVE')

    await user.click(dialogo.getByRole('button', { name: /Otro caso del tratamiento 1/ }))
    expect(dialogo.getByLabelText('Tratamiento 2')).toHaveValue('srv-curacion')
    await user.selectOptions(dialogo.getByLabelText('Gravedad 2'), 'SEVERO')
    await user.clear(dialogo.getByLabelText('Piezas 2'))
    await user.type(dialogo.getByLabelText('Piezas 2'), '2')

    // 1 × 50 + 2 × 120
    expect(dialogo.getByText('Total del presupuesto: S/ 290.00')).toBeInTheDocument()
    await user.click(dialogo.getByRole('button', { name: 'Guardar presupuesto' }))
    await waitFor(() =>
      expect(cuerpo?.items).toEqual([
        expect.objectContaining({ cantidad: 1, precioUnitario: 50, gravedad: 'LEVE' }),
        expect.objectContaining({ cantidad: 2, precioUnitario: 120, gravedad: 'SEVERO' }),
      ]),
    )
  })

  it('el documento impreso lleva el nombre completo, escapa el texto y muestra el total', () => {
    const html = documentoPresupuesto({ presupuesto: VIGENTE, pacienteNombre: 'Rosa Quispe Vera', pacienteDni: '40111222', sedeNombre: 'Villadent Chilca' })
    expect(html).toContain('Presupuesto de tratamiento')
    expect(html).toContain('Rosa Quispe Vera')
    expect(html).toContain('Endodoncia &lt;b&gt;')
    expect(html).not.toContain('Endodoncia <b>')
    expect(html).toContain('Total del presupuesto: S/ 510.00')
  })
})
