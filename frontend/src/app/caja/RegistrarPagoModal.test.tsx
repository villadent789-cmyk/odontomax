import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { RegistrarPagoModal } from './RegistrarPagoModal'

const catalogo = [
  { id: 'srv-consulta', nombre: 'Consulta odontológica', precio: 80, activo: true },
  { id: 'srv-limpieza', nombre: 'Limpieza dental', precio: 120, activo: true },
]
const plan = {
  id: 't-1',
  pacienteId: 'p-1',
  nombre: 'Ortodoncia',
  especialistaId: 'e-1',
  especialistaNombre: 'Dra. Elena Paucar',
  totalSesiones: 5,
  sesionesRealizadas: 2,
  costoTotal: 1000,
  montoPagado: 400,
  saldoPendiente: 600,
  estado: 'EN_PROCESO',
}

let cuerposPago: Record<string, unknown>[] = []
let cuerposPlan: Record<string, unknown>[] = []
let orden: string[] = []
let pagosFallan = 0

function api(opciones: { planes?: unknown[]; pagosFallan?: number } = {}) {
  cuerposPago = []
  cuerposPlan = []
  orden = []
  pagosFallan = opciones.pagosFallan ?? 0
  servidor.use(
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json(opciones.planes ?? [])),
    http.get(`${API}/servicios`, () => HttpResponse.json(catalogo)),
    http.get(`${API}/especialistas`, () => HttpResponse.json([
      { id: 'e-1', nombre: 'Dra. Elena Paucar', especialidad: 'Ortodoncia', sedes: ['sede-chilca'], sillonesPorSede: { 'sede-chilca': 2 }, activo: true },
    ])),
    http.post(`${API}/tratamientos`, async ({ request }) => {
      const cuerpo = await request.json() as Record<string, unknown>
      cuerposPlan.push(cuerpo)
      orden.push('plan')
      return HttpResponse.json({
        id: 't-nuevo',
        pacienteId: 'p-1',
        especialistaId: 'e-1',
        especialistaNombre: 'Dra. Elena Paucar',
        nombre: cuerpo.nombre,
        costoTotal: cuerpo.costoTotal,
        saldoPendiente: cuerpo.costoTotal,
        estado: 'EN_PROCESO',
      })
    }),
    http.post(`${API}/pagos`, async ({ request }) => {
      const cuerpo = await request.json() as Record<string, unknown>
      cuerposPago.push(cuerpo)
      orden.push('pago')
      if (pagosFallan > 0) {
        pagosFallan -= 1
        return HttpResponse.json({ mensaje: 'No se pudo registrar el cobro' }, { status: 409 })
      }
      return HttpResponse.json({
        id: `pg-${cuerposPago.length}`,
        pacienteId: 'p-1',
        tratamientoId: cuerpo.tratamientoId,
        monto: cuerpo.monto,
        metodoPago: cuerpo.metodoPago,
        comprobante: 'R001-000123',
      })
    }),
  )
}

function montar() {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <RegistrarPagoModal
        abierto
        alCerrar={() => {}}
        sedeId="sede-chilca"
        pacienteInicialId="p-1"
        pacienteInicialNombre="Luciana Meza"
      />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Registrar cobro ligado a un plan', () => {
  it('no pregunta el tipo de comprobante y siempre emite el recibo interno', async () => {
    api({ planes: [plan] })
    montar()

    expect(await screen.findByText('Ortodoncia')).toBeInTheDocument()
    expect(screen.queryByText('Tipo de Comprobante')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    await waitFor(() => expect(cuerposPago).toHaveLength(1))
    expect(cuerposPago[0]).toMatchObject({
      pacienteId: 'p-1',
      tratamientoId: 't-1',
      monto: 600,
      metodoPago: 'EFECTIVO',
      comprobanteTipo: 'RECIBO',
    })
    expect(await screen.findByText('R001-000123')).toBeInTheDocument()
  })

  it('en efectivo no pide datos bancarios ni envía número de operación', async () => {
    api({ planes: [plan] })
    montar()

    expect(await screen.findByText('Ortodoncia')).toBeInTheDocument()
    expect(screen.queryByLabelText(/N° de Operación/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    await waitFor(() => expect(cuerposPago).toHaveLength(1))
    expect(cuerposPago[0]).toMatchObject({ metodoPago: 'EFECTIVO', comprobanteTipo: 'RECIBO' })
    expect(cuerposPago[0].numeroOperacion).toBeUndefined()
  })

  it('con Yape no pide número de operación y registra el pago', async () => {
    api({ planes: [plan] })
    montar()

    expect(await screen.findByText('Ortodoncia')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Yape' }))
    expect(screen.queryByLabelText(/N° de Operación/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    await waitFor(() => expect(cuerposPago).toHaveLength(1))
    expect(cuerposPago[0]).toMatchObject({
      metodoPago: 'YAPE',
      comprobanteTipo: 'RECIBO',
    })
    expect(cuerposPago[0].numeroOperacion).toBeUndefined()
  })

  it('con Plin no pide número de operación y registra el pago', async () => {
    api({ planes: [plan] })
    montar()

    expect(await screen.findByText('Ortodoncia')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Plin' }))
    expect(screen.queryByLabelText(/N° de Operación/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    await waitFor(() => expect(cuerposPago).toHaveLength(1))
    expect(cuerposPago[0]).toMatchObject({ metodoPago: 'PLIN' })
    expect(cuerposPago[0].numeroOperacion).toBeUndefined()
  })

  it('distingue la tarjeta de débito de la de crédito al registrar el cobro', async () => {
    api({ planes: [plan] })
    montar()

    expect(await screen.findByText('Ortodoncia')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tarjeta de crédito' }))
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    await waitFor(() => expect(cuerposPago).toHaveLength(1))
    expect(cuerposPago[0]).toMatchObject({ metodoPago: 'TARJETA_CREDITO' })
  })

  it('muestra doctor, sesión y saldo y no permite cobrar por encima del monto pendiente', async () => {
    api({ planes: [plan] })
    montar()

    expect(await screen.findByText('Ortodoncia')).toBeInTheDocument()
    expect(screen.getByText('Doctor: Dra. Elena Paucar')).toBeInTheDocument()
    expect(screen.getByText('Sesión 3 de 5')).toBeInTheDocument()
    expect(screen.getByText('Presupuesto S/ 1,000.00 · Pagado S/ 400.00 · Falta S/ 600.00')).toBeInTheDocument()
    expect(screen.queryByText(/Atendido por/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Abono a cuenta/i)).not.toBeInTheDocument()

    const monto = screen.getByLabelText('Monto a cobrar (S/) *')
    await userEvent.clear(monto)
    await userEvent.type(monto, '601')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    expect(monto).not.toBeValid()
    expect(cuerposPago).toHaveLength(0)
  })

  it('permite elegir entre varios planes y actualiza el saldo sugerido', async () => {
    api({ planes: [plan, { ...plan, id: 't-2', nombre: 'Implante', especialistaNombre: 'Dr. Mario Rivas', saldoPendiente: 250 }] })
    montar()

    const selector = await screen.findByLabelText('Plan de tratamiento *')
    await userEvent.selectOptions(selector, 't-2')

    expect(screen.getAllByText('Implante')).toHaveLength(2)
    expect(screen.getByText('Doctor: Dr. Mario Rivas')).toBeInTheDocument()
    expect(screen.getByLabelText('Monto a cobrar (S/) *')).toHaveValue(250)
  })

  it('sin planes crea un plan de una sesión y luego cobra contra ese plan', async () => {
    api()
    montar()

    const formulario = await screen.findByRole('region', { name: 'Cobrar consulta o procedimiento' })
    await userEvent.selectOptions(within(formulario).getByLabelText('Tratamiento del catálogo'), 'srv-consulta')
    await userEvent.selectOptions(within(formulario).getByLabelText('Doctor responsable'), 'e-1')
    expect(within(formulario).getByText('Sillón 2 · Dra. Elena Paucar')).toBeInTheDocument()
    expect(screen.getByLabelText('Monto a cobrar (S/) *')).toHaveValue(80)

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    await waitFor(() => expect(cuerposPago).toHaveLength(1))

    expect(orden).toEqual(['plan', 'pago'])
    expect(cuerposPlan[0]).toMatchObject({
      pacienteId: 'p-1',
      especialistaId: 'e-1',
      nombre: 'Consulta odontológica',
      costoTotal: 80,
      totalSesiones: 1,
      items: [{ nombreProcedimiento: 'Consulta odontológica', precioBase: 80, cantidad: 1, descuento: 0 }],
    })
    expect(cuerposPago[0]).toMatchObject({ pacienteId: 'p-1', tratamientoId: 't-nuevo', monto: 80 })
  })

  it('sin planes rechaza un monto mayor al presupuesto antes de crear el plan', async () => {
    api()
    montar()

    const formulario = await screen.findByRole('region', { name: 'Cobrar consulta o procedimiento' })
    await userEvent.selectOptions(within(formulario).getByLabelText('Tratamiento del catálogo'), 'srv-consulta')
    await userEvent.selectOptions(within(formulario).getByLabelText('Doctor responsable'), 'e-1')
    const monto = screen.getByLabelText('Monto a cobrar (S/) *')
    await userEvent.clear(monto)
    await userEvent.type(monto, '81')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    expect(await screen.findByText('El monto no puede superar el saldo disponible de S/ 80.00.')).toBeInTheDocument()
    expect(cuerposPlan).toHaveLength(0)
    expect(cuerposPago).toHaveLength(0)
  })

  it('reintentar el cobro fallido reutiliza el plan y no lo duplica', async () => {
    api({ pagosFallan: 1 })
    montar()

    const formulario = await screen.findByRole('region', { name: 'Cobrar consulta o procedimiento' })
    await userEvent.selectOptions(within(formulario).getByLabelText('Tratamiento del catálogo'), 'srv-consulta')
    await userEvent.selectOptions(within(formulario).getByLabelText('Doctor responsable'), 'e-1')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    expect(await screen.findByText(/El plan ya está creado; al reintentar se usará el mismo plan/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    await waitFor(() => expect(cuerposPago).toHaveLength(2))
    expect(cuerposPlan).toHaveLength(1)
    expect(cuerposPago.map((cuerpo) => cuerpo.tratamientoId)).toEqual(['t-nuevo', 't-nuevo'])
  })

  const planNuevo = (saldoPendiente: number) => ({
    id: 't-nuevo', pacienteId: 'p-1', especialistaId: 'e-1', nombre: 'Consulta odontológica', costoTotal: 80, saldoPendiente, estado: 'EN_PROCESO',
  })

  it('al crearse el plan y recargarse la lista conserva el monto que escribió el usuario', async () => {
    api({ pagosFallan: 1 })
    servidor.use(http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json(cuerposPlan.length > 0 ? [planNuevo(80)] : [])))
    montar()

    const formulario = await screen.findByRole('region', { name: 'Cobrar consulta o procedimiento' })
    await userEvent.selectOptions(within(formulario).getByLabelText('Tratamiento del catálogo'), 'srv-consulta')
    await userEvent.selectOptions(within(formulario).getByLabelText('Doctor responsable'), 'e-1')
    const monto = screen.getByLabelText('Monto a cobrar (S/) *')
    await userEvent.clear(monto)
    await userEvent.type(monto, '50')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    expect(await screen.findByText(/El plan ya está creado/)).toBeInTheDocument()
    expect(screen.getByLabelText('Monto a cobrar (S/) *')).toHaveValue(50)

    // Y el reintento cobra los 50 contra el mismo plan, sin crear otro.
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))
    await waitFor(() => expect(cuerposPago).toHaveLength(2))
    expect(cuerposPlan).toHaveLength(1)
    expect(cuerposPago[1]).toMatchObject({ tratamientoId: 't-nuevo', monto: 50 })
  })

  it('si el cobro falló pero el plan ya quedó pagado, muestra el comprobante registrado en vez de pedir reintentar', async () => {
    api({ pagosFallan: 1 })
    servidor.use(
      http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json(cuerposPlan.length > 0 ? [planNuevo(0)] : [])),
      http.get(`${API}/pagos/paciente/:id`, () => HttpResponse.json([
        { id: 'pg-previo', pacienteId: 'p-1', tratamientoId: 't-nuevo', sedeId: 'sede-chilca', monto: 80, metodoPago: 'EFECTIVO', comprobante: 'R001-000099', anulado: false, pagadoEn: '2026-11-12T15:00:00Z' },
      ])),
    )
    montar()

    const formulario = await screen.findByRole('region', { name: 'Cobrar consulta o procedimiento' })
    await userEvent.selectOptions(within(formulario).getByLabelText('Tratamiento del catálogo'), 'srv-consulta')
    await userEvent.selectOptions(within(formulario).getByLabelText('Doctor responsable'), 'e-1')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cobro' }))

    expect(await screen.findByText('R001-000099')).toBeInTheDocument()
    expect(screen.queryByText(/El plan ya está creado/)).not.toBeInTheDocument()
  })
})
