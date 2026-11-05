import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { DetalleOrdenModal } from './DetalleOrdenModal'
import type { OrdenLaboratorio, PagoLaboratorio } from '../types'

const ORDEN_MOCK: OrdenLaboratorio = {
  id: '60000000-0000-0000-0000-000000000001',
  codigo: 'LAB-2026-0001',
  pacienteId: '30000000-0000-0000-0000-000000000001',
  pacienteNombre: 'Lucía Quispe Mayta',
  pacienteDni: '44556677',
  especialistaId: '10000000-0000-0000-0000-000000000001',
  especialistaNombre: 'Dr. Alex Ríos',
  sedeId: 'sede-chilca-1',
  laboratorioId: '50000000-0000-0000-0000-000000000001',
  laboratorioNombre: 'Lab Dental Real Huancayo',
  tipoTrabajo: 'Corona Zirconio',
  estado: 'ENVIADO',
  fechaSolicitud: '2026-10-08',
  costoLaboratorio: 200,
  precioPaciente: 500,
  estadoPago: 'PARCIAL',
  montoPagado: 100,
  saldoLaboratorio: 100,
  creadoEn: '2026-10-08T10:00:00Z',
}

const PAGO_ACTIVO: PagoLaboratorio = {
  id: '70000000-0000-0000-0000-000000000001',
  ordenId: ORDEN_MOCK.id,
  laboratorioId: ORDEN_MOCK.laboratorioId,
  sedeId: 'sede-chilca-1',
  monto: 100,
  metodoPago: 'PLIN',
  numeroOperacion: 'OP-1234',
  pagadoEn: '2026-10-09T14:30:00Z',
  anulado: false,
}

const PAGO_ANULADO: PagoLaboratorio = {
  id: '70000000-0000-0000-0000-000000000002',
  ordenId: ORDEN_MOCK.id,
  laboratorioId: ORDEN_MOCK.laboratorioId,
  sedeId: 'sede-chilca-1',
  monto: 50,
  metodoPago: 'EFECTIVO',
  pagadoEn: '2026-10-08T11:00:00Z',
  anulado: true,
  anuladoEn: '2026-10-08T12:00:00Z',
  anuladoPor: 'u-admin',
  motivoAnulacion: 'Error de tipeo en caja',
}

function sesionComo(rol: 'ADMIN_GENERAL' | 'ADMIN_SEDE' | 'RECEPCION' | 'ODONTOLOGO') {
  guardarSesion({
    token: 'token-test',
    usuario: {
      id: 'u-test',
      nombre: 'Usuario Prueba',
      rol,
      sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
    },
    sedeActivaId: 'sede-chilca-1',
  })
}

function montarModal(ordenId = ORDEN_MOCK.id) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={qc}>
      <DetalleOrdenModal abierto={true} onCerrar={() => {}} ordenId={ordenId} />
    </QueryClientProvider>,
  )
}

describe('DetalleOrdenModal · Anulación de pagos de laboratorio', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
  })

  afterEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
  })

  it('muestra botón "Anular" solo para ADMIN_GENERAL y ADMIN_SEDE en pagos no anulados', async () => {
    sesionComo('ADMIN_GENERAL')
    servidor.use(
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}`, () =>
        HttpResponse.json(ORDEN_MOCK),
      ),
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}/pagos`, () =>
        HttpResponse.json([PAGO_ACTIVO, PAGO_ANULADO]),
      ),
    )

    montarModal()

    await waitFor(() => {
      expect(screen.getByText('LAB-2026-0001')).toBeInTheDocument()
    })

    // Debe haber exactamente un botón Anular (solo para PAGO_ACTIVO, no para PAGO_ANULADO)
    const botonesAnular = await screen.findAllByRole('button', { name: /anular/i })
    expect(botonesAnular).toHaveLength(1)
  })

  it('no muestra botón "Anular" para roles no autorizados como RECEPCION', async () => {
    sesionComo('RECEPCION')
    servidor.use(
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}`, () =>
        HttpResponse.json(ORDEN_MOCK),
      ),
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}/pagos`, () =>
        HttpResponse.json([PAGO_ACTIVO]),
      ),
    )

    montarModal()

    await waitFor(() => {
      expect(screen.getByText('LAB-2026-0001')).toBeInTheDocument()
    })

    // Esperar a que cargue el historial de pagos
    await screen.findByText(/PLIN/)

    // No debe existir botón Anular
    expect(screen.queryByRole('button', { name: /^anular$/i })).not.toBeInTheDocument()
  })

  it('muestra badge "Anulado" y el motivo de anulación para pagos anulados', async () => {
    sesionComo('ADMIN_SEDE')
    servidor.use(
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}`, () =>
        HttpResponse.json(ORDEN_MOCK),
      ),
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}/pagos`, () =>
        HttpResponse.json([PAGO_ANULADO]),
      ),
    )

    montarModal()

    await waitFor(() => {
      expect(screen.getByText('LAB-2026-0001')).toBeInTheDocument()
    })

    // Insignia Anulado
    const badgeAnulado = await screen.findByText('Anulado')
    expect(badgeAnulado).toBeInTheDocument()

    // Motivo de anulación visible
    expect(screen.getByText(/Motivo: Error de tipeo en caja/i)).toBeInTheDocument()

    // No botón Anular para pago ya anulado
    expect(screen.queryByRole('button', { name: /^anular$/i })).not.toBeInTheDocument()
  })

  it('exige motivo obligatorio para confirmar anulación y envía POST al backend', async () => {
    sesionComo('ADMIN_GENERAL')
    const user = userEvent.setup()
    let peticionAnulacion: Record<string, unknown> | null = null

    servidor.use(
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}`, () =>
        HttpResponse.json(ORDEN_MOCK),
      ),
      http.get(`${API}/laboratorio/ordenes/${ORDEN_MOCK.id}/pagos`, () =>
        HttpResponse.json([PAGO_ACTIVO]),
      ),
      http.post(`${API}/laboratorio/pagos/${PAGO_ACTIVO.id}/anular`, async ({ request }) => {
        peticionAnulacion = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({
          ...PAGO_ACTIVO,
          anulado: true,
          motivoAnulacion: peticionAnulacion.motivoAnulacion,
        })
      }),
    )

    montarModal()

    await waitFor(() => {
      expect(screen.getByText('LAB-2026-0001')).toBeInTheDocument()
    })

    const botonAnular = await screen.findByRole('button', { name: /^anular$/i })
    await user.click(botonAnular)

    // Se abre diálogo accesible de anulación
    expect(screen.getByRole('heading', { name: /anular pago de laboratorio/i })).toBeInTheDocument()

    const botonConfirmar = screen.getByRole('button', { name: /confirmar anulación/i })
    // El botón está deshabilitado si no hay motivo
    expect(botonConfirmar).toBeDisabled()

    const inputMotivo = screen.getByLabelText(/motivo de anulación/i)
    await user.type(inputMotivo, 'Pago registrado por error')
    expect(botonConfirmar).toBeEnabled()

    await user.click(botonConfirmar)

    await waitFor(() => {
      expect(peticionAnulacion).toEqual({
        motivoAnulacion: 'Pago registrado por error',
      })
    })
  })
})
