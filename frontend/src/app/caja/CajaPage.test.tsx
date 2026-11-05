import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as imprimirModulo from '@/shared/imprimir'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { CajaSesionDTO, PagoDTO } from './api-caja'

const API = 'http://localhost:3000/api/v1'

const CAJA_ABIERTA_MOCK: CajaSesionDTO = {
  id: 'c0000000-0000-4000-8000-000000000001',
  sedeId: 'sede-chilca',
  sedeNombre: 'Villadent Chilca',
  abiertaPor: 'u-1',
  abiertaPorNombre: 'Katherin Mendoza',
  abiertaEn: '2026-10-10T08:30:00Z',
  montoApertura: 0,
  montoEsperado: 130.0,
  totalIngresosEfectivo: 150.0,
  totalEgresosEfectivo: 20.0,
  totalIngresosYape: 80.0,
  totalEgresosYape: 10.0,
  totalIngresosPlin: 25.0,
  totalEgresosPlin: 5.0,
  totalYape: 80.0,
  montoEsperadoYape: 70.0,
  montoEsperadoPlin: 20.0,
  estado: 'ABIERTA',
  movimientos: [
    {
      id: 'm-1',
      sesionId: 'c0000000-0000-4000-8000-000000000001',
      tipo: 'INGRESO',
      concepto: 'Cobro de consulta (Recibo R001-000001)',
      monto: 150.0,
      metodoPago: 'EFECTIVO',
      registradoPor: 'u-1',
      registradoPorNombre: 'Katherin Mendoza',
      registradoEn: '2026-10-10T09:15:00Z',
    },
    {
      id: 'm-2',
      sesionId: 'c0000000-0000-4000-8000-000000000001',
      tipo: 'EGRESO',
      concepto: 'Compra de cinta testigo para autoclave',
      monto: 20.0,
      metodoPago: 'EFECTIVO',
      registradoPor: 'u-1',
      registradoPorNombre: 'Katherin Mendoza',
      registradoEn: '2026-10-10T10:00:00Z',
    },
  ],
}

const PAGOS_MOCK: PagoDTO[] = [
  {
    id: 'pago-1',
    sedeId: 'sede-chilca',
    pacienteId: 'pac-1',
    pacienteNombre: 'Lucía Quispe Rojas',
    pacienteDni: '70000001',
    monto: 150.0,
    metodoPago: 'EFECTIVO',
    comprobante: 'R001-000001',
    comprobanteTipo: 'RECIBO',
    pagadoEn: '2026-10-10T09:15:00Z',
    registradoPor: 'u-1',
    creadoEn: '2026-10-10T09:15:00Z',
    anulado: false,
  },
  {
    id: 'pago-2',
    sedeId: 'sede-chilca',
    pacienteId: 'pac-2',
    pacienteNombre: 'Carlos Huamán',
    pacienteDni: '70000002',
    monto: 80.0,
    metodoPago: 'YAPE',
    numeroOperacion: '19482039',
    comprobante: 'R001-000002',
    comprobanteTipo: 'RECIBO',
    pagadoEn: '2026-10-10T11:00:00Z',
    registradoPor: 'u-1',
    creadoEn: '2026-10-10T11:00:00Z',
    anulado: false,
  },
]

describe('B8 · Módulo de Finanzas & Caja Chica', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'token-recepcion',
      usuario: {
        id: 'u-1',
        nombre: 'Katherin Mendoza',
        rol: 'RECEPCION',
        sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca',
    })
    servidor.use(
      http.get(`${API}/caja/historial`, () => HttpResponse.json([])),
      http.get(`${API}/caja/resumen-diario`, () => HttpResponse.json({
        fecha: '2026-10-10',
        sedeId: 'sede-chilca',
        ingresosEfectivo: 0,
        ingresosYape: 0,
        ingresosPlin: 0,
        egresosEfectivo: 0,
        egresosYape: 0,
        egresosPlin: 0,
      })),
    )
  })

  afterEach(() => {
    limpiarSesion()
  })

  it('muestra el estado vacío y abre el turno siempre en cero', async () => {
    const user = userEvent.setup({ delay: null })

    servidor.use(
      http.get(`${API}/caja/activa`, () => {
        return new HttpResponse(null, { status: 204 })
      }),
      http.get(`${API}/pagos/sede/:sedeId`, () => {
        return HttpResponse.json([])
      }),
      http.post(`${API}/caja/abrir`, async ({ request }) => {
        const body = (await request.json()) as { sedeId: string; montoApertura: number }
        expect(body.sedeId).toBe('sede-chilca')
        expect(body.montoApertura).toBe(0)
        return HttpResponse.json(
          {
            ...CAJA_ABIERTA_MOCK,
            montoApertura: 0,
            montoEsperado: 0,
            movimientos: [],
          },
          { status: 201 }
        )
      })
    )

    await renderRuta('/app/caja')

    // Título y aviso de que no hay turno
    expect(await screen.findByRole('heading', { name: 'Caja' })).toBeInTheDocument()
    expect(screen.getByText(/Sin Turno Activo/i)).toBeInTheDocument()
    expect(
      screen.getByText(/No hay un turno de caja abierto en esta sede/i)
    ).toBeInTheDocument()

    // Abrir modal de apertura
    const btnAbrir = screen.getByRole('button', { name: /Abrir Turno de Caja Ahora/i })
    await user.click(btnAbrir)

    expect(await screen.findByRole('heading', { name: 'Abrir Turno de Caja' })).toBeInTheDocument()

    expect(screen.queryByLabelText(/Monto Inicial en Efectivo/i)).not.toBeInTheDocument()

    const btnConfirmar = screen.getByRole('button', { name: /Confirmar y Abrir Turno/i })
    await user.click(btnConfirmar)

    // Modal se cierra
    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: 'Abrir Turno de Caja' })).not.toBeInTheDocument()
    })
  })

  it('muestra las 5 KPI cards y la lista de movimientos cuando la caja está abierta', async () => {
    servidor.use(
      http.get(`${API}/caja/activa`, () => {
        return HttpResponse.json(CAJA_ABIERTA_MOCK)
      }),
      http.get(`${API}/pagos/sede/:sedeId`, () => {
        return HttpResponse.json(PAGOS_MOCK)
      })
    )

    await renderRuta('/app/caja')

    expect(await screen.findByRole('heading', { name: 'Caja' })).toBeInTheDocument()
    expect(screen.getByText(/Turno Abierto/i)).toBeInTheDocument()

    expect(screen.getByText('Neto esperado · Efectivo')).toBeInTheDocument()
    expect(screen.getByText('Neto esperado · Yape')).toBeInTheDocument()
    expect(screen.getByText('Neto esperado · Plin')).toBeInTheDocument()
    expect(screen.queryByText(/Fondo Apertura|Saldo Esperado en Gaveta/i)).not.toBeInTheDocument()

    // Valores monetarios
    expect(screen.getAllByText('S/ 150.00').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('S/ 80.00').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('S/ 25.00').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('S/ 130.00')).toBeInTheDocument()
    expect(screen.getByText('S/ 70.00')).toBeInTheDocument()
    expect(screen.getByText('S/ 20.00')).toBeInTheDocument()

    // Tabla de movimientos
    expect(screen.getByText(/Compra de cinta testigo para autoclave/i)).toBeInTheDocument()
    expect(screen.getByText(/Cobro de consulta/i)).toBeInTheDocument()
  })

  it('permite registrar un egreso manual con validación de tope de S/ 200', async () => {
    const user = userEvent.setup({ delay: null })

    servidor.use(
      http.get(`${API}/caja/activa`, () => {
        return HttpResponse.json(CAJA_ABIERTA_MOCK)
      }),
      http.get(`${API}/pagos/sede/:sedeId`, () => {
        return HttpResponse.json(PAGOS_MOCK)
      }),
      http.post(`${API}/caja/:id/movimientos`, async ({ request }) => {
        const body = (await request.json()) as { tipo: string; monto: number; concepto: string; metodoPago: string }
        expect(body.tipo).toBe('EGRESO')
        expect(body.monto).toBe(45.0)
        expect(body.metodoPago).toBe('PLIN')
        return HttpResponse.json(
          {
            id: 'm-3',
            sesionId: CAJA_ABIERTA_MOCK.id,
            tipo: 'EGRESO',
            concepto: body.concepto,
            monto: body.monto,
            metodoPago: 'EFECTIVO',
            registradoPor: 'u-1',
            registradoEn: '2026-10-10T12:00:00Z',
          },
          { status: 201 }
        )
      })
    )

    await renderRuta('/app/caja')

    const btnEgreso = await screen.findByRole('button', { name: /Egreso Manual/i })
    await user.click(btnEgreso)

    expect(await screen.findByRole('heading', { name: 'Registrar Movimiento de Caja' })).toBeInTheDocument()

    // Intentar ingresar más de S/ 200
    const inputConcepto = screen.getByLabelText(/Concepto \/ Motivo/i)
    await user.type(inputConcepto, 'Compra de insumos no autorizados')

    const inputMonto = screen.getByLabelText(/Monto \(S\/\)/i)
    await user.type(inputMonto, '250.00')
    await user.selectOptions(screen.getByLabelText('Medio de Pago'), 'PLIN')

    const btnSubmit = screen.getByRole('button', { name: /Registrar Egreso/i })
    await user.click(btnSubmit)

    // Alerta de tope
    expect(await screen.findByText(/Tope de seguridad: El monto de un egreso manual no puede exceder S\/ 200.00/i)).toBeInTheDocument()

    // Corregir a 45.00
    await user.clear(inputMonto)
    await user.type(inputMonto, '45.00')
    await user.click(btnSubmit)

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: 'Registrar Movimiento de Caja' })).not.toBeInTheDocument()
    })
  })

  it('si los egresos superan a los ingresos, el cierre no muestra negativos: espera 0 y lo avisa', async () => {
    const user = userEvent.setup({ delay: null })
    servidor.use(
      http.get(`${API}/caja/activa`, () => HttpResponse.json({ ...CAJA_ABIERTA_MOCK, montoEsperado: -200, montoEsperadoYape: -50, montoEsperadoPlin: 0 })),
      http.get(`${API}/pagos/sede/:sedeId`, () => HttpResponse.json(PAGOS_MOCK)),
    )
    await renderRuta('/app/caja')
    await user.click(await screen.findByRole('button', { name: 'Cerrar periodo' }))

    expect(await screen.findByLabelText('Efectivo contado')).toHaveValue(0)
    expect(screen.getByLabelText('Yape contado')).toHaveValue(0)
    expect(screen.getByText('Los egresos superan a los ingresos')).toBeInTheDocument()
    const dialogo = screen.getByRole('dialog')
    expect(dialogo.textContent).not.toMatch(/-S\/|-\s*S\//)
    await user.clear(screen.getByLabelText('Efectivo contado'))
    await user.type(screen.getByLabelText('Efectivo contado'), '200')
    expect(within(dialogo).getAllByText(/Sobran/).length).toBeGreaterThan(0)
  })

  it('muestra el cierre por método y exige observaciones si hay diferencias', async () => {
    const user = userEvent.setup({ delay: null })

    servidor.use(
      http.get(`${API}/caja/activa`, () => {
        return HttpResponse.json(CAJA_ABIERTA_MOCK)
      }),
      http.get(`${API}/pagos/sede/:sedeId`, () => {
        return HttpResponse.json(PAGOS_MOCK)
      }),
      http.post(`${API}/caja/:id/cerrar`, async ({ request }) => {
        const body = (await request.json()) as {
          montoContado: number
          montoContadoYape: number
          montoContadoPlin: number
          observaciones: string
        }
        expect(body).toMatchObject({
          montoContado: 130,
          montoContadoYape: 60,
          montoContadoPlin: 20,
          observaciones: 'Diferencia de Yape revisada',
        })
        return HttpResponse.json(
          {
            ...CAJA_ABIERTA_MOCK,
            estado: 'CERRADA',
            montoContado: body.montoContado,
            diferencia: 0.0,
            cerradaEn: '2026-10-10T20:00:00Z',
          },
          { status: 200 }
        )
      })
    )

    await renderRuta('/app/caja')

    const btnCerrar = await screen.findByRole('button', { name: 'Cerrar periodo' })
    await user.click(btnCerrar)

    expect(await screen.findByRole('heading', { name: 'Cierre de caja' })).toBeInTheDocument()
    expect(screen.getByText(/Periodo: desde 10\/10\/2026 hasta hoy/)).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Sistema' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Tú cuentas' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Diferencia' })).toBeInTheDocument()
    expect(screen.getByLabelText('Yape contado')).toHaveValue(70)
    expect(screen.getByLabelText('Plin contado')).toHaveValue(20)

    const inputYape = screen.getByLabelText('Yape contado')
    await user.clear(inputYape)
    await user.type(inputYape, '60')
    const btnConfirmar = screen.getByRole('button', { name: 'Confirmar cierre' })
    await user.click(btnConfirmar)
    expect(await screen.findByText('Escribe las observaciones para explicar las diferencias del cierre.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Cierre de caja' })).toBeInTheDocument()

    await user.type(screen.getByLabelText(/Observaciones de Cierre/), 'Diferencia de Yape revisada')
    await user.click(btnConfirmar)

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: 'Cierre de caja' })).not.toBeInTheDocument()
    })
  })

  it('muestra el resumen diario por método', async () => {
    const user = userEvent.setup({ delay: null })
    servidor.use(
      http.get(`${API}/caja/activa`, () => HttpResponse.json(CAJA_ABIERTA_MOCK)),
      http.get(`${API}/pagos/sede/:sedeId`, () => HttpResponse.json(PAGOS_MOCK)),
      http.get(`${API}/caja/resumen-diario`, () => HttpResponse.json({
        fecha: '2026-10-10',
        sedeId: 'sede-chilca',
        ingresosEfectivo: 150,
        ingresosYape: 80,
        ingresosPlin: 25,
        egresosEfectivo: 20,
        egresosYape: 10,
        egresosPlin: 5,
      }))
    )
    await renderRuta('/app/caja')
    await user.click(await screen.findByRole('tab', { name: 'Resumen del día' }))
    expect(await screen.findByRole('heading', { name: 'Resumen del día' })).toBeInTheDocument()
    expect(screen.getByText('Efectivo')).toBeInTheDocument()
    expect(screen.getByText('Yape')).toBeInTheDocument()
    expect(screen.getByText('Plin')).toBeInTheDocument()
    expect(screen.getByText('S/ 25.00')).toBeInTheDocument()
  })

  it('imprime el reporte del día con las diferencias de cierre y escapa el texto del servidor', async () => {
    const user = userEvent.setup({ delay: null })
    const imprimir = vi.spyOn(imprimirModulo, 'imprimirDocumento').mockResolvedValue(undefined)
    servidor.use(
      http.get(`${API}/caja/activa`, () => HttpResponse.json(CAJA_ABIERTA_MOCK)),
      http.get(`${API}/pagos/sede/:sedeId`, () => HttpResponse.json(PAGOS_MOCK)),
      http.get(`${API}/caja/resumen-diario`, () => HttpResponse.json({ fecha: '2026-10-10', sedeId: 'sede-chilca' })),
      http.get(`${API}/caja/reporte-diario`, () => HttpResponse.json({
        fecha: '2026-10-10',
        sedeNombre: 'Villadent <Chilca>',
        ingresos: { efectivo: 150, yape: 80, plin: 25, total: 255 },
        egresos: { efectivo: 20, yape: 10, plin: 5, total: 35 },
        balance: 220,
        egresosPorCategoria: [{ categoria: 'Laboratorio', monto: 35 }],
        cierres: [{ cerradaEn: '2026-10-10T23:00:00Z', efectivo: { diferencia: -5 }, yape: { diferencia: 0 }, plin: { diferencia: 0 }, observaciones: '<b>cambio</b>' }],
      })),
    )
    await renderRuta('/app/caja')
    await user.click(await screen.findByRole('tab', { name: 'Resumen del día' }))
    await user.click(await screen.findByRole('button', { name: 'Imprimir reporte del día' }))

    await waitFor(() => expect(imprimir).toHaveBeenCalledTimes(1))
    const html = imprimir.mock.calls[0][0]
    expect(html).toContain('Falta S/ 5.00')
    expect(html).toContain('Laboratorio')
    expect(html).toContain('Villadent &lt;Chilca&gt;')
    expect(html).not.toContain('<b>cambio</b>')
  })

  it('permite alternar a la pestaña Cobros Realizados para ver comprobantes emitidos', async () => {
    const user = userEvent.setup({ delay: null })

    servidor.use(
      http.get(`${API}/caja/activa`, () => {
        return HttpResponse.json(CAJA_ABIERTA_MOCK)
      }),
      http.get(`${API}/pagos/sede/:sedeId`, () => {
        return HttpResponse.json(PAGOS_MOCK)
      })
    )

    await renderRuta('/app/caja')

    const tabPagos = await screen.findByRole('tab', { name: /Cobros Realizados/i })
    await user.click(tabPagos)

    expect(await screen.findByText('R001-000001')).toBeInTheDocument()
    expect(screen.getByText('Lucía Quispe Rojas')).toBeInTheDocument()
    expect(screen.getByText('R001-000002')).toBeInTheDocument()
    expect(screen.getByText('Carlos Huamán')).toBeInTheDocument()
  })

  it('permite filtrar los cobros realizados por buscador y método de pago', async () => {
    const user = userEvent.setup({ delay: null })

    servidor.use(
      http.get(`${API}/caja/activa`, () => HttpResponse.json(CAJA_ABIERTA_MOCK)),
      http.get(`${API}/pagos/sede/:sedeId`, () => HttpResponse.json(PAGOS_MOCK)),
    )

    await renderRuta('/app/caja')

    const tabPagos = await screen.findByRole('tab', { name: /Cobros Realizados/i })
    await user.click(tabPagos)

    expect(await screen.findByText('Lucía Quispe Rojas')).toBeInTheDocument()
    expect(screen.getByText('Carlos Huamán')).toBeInTheDocument()
    expect(screen.getByText((_, el) => el?.tagName.toLowerCase() === 'span' && /comprobante/i.test(el?.textContent ?? '') && !el?.hasAttribute('data-testid'))).toHaveTextContent('Mostrando 2 comprobantes')

    // Buscar "Carlos"
    const inputBuscar = screen.getByPlaceholderText(/Buscar por paciente, DNI, comprobante/i)
    await user.type(inputBuscar, 'Carlos')

    expect(screen.getByText('Carlos Huamán')).toBeInTheDocument()
    expect(screen.queryByText('Lucía Quispe Rojas')).not.toBeInTheDocument()
    expect(screen.getByText((_, el) => el?.tagName.toLowerCase() === 'span' && /comprobante/i.test(el?.textContent ?? '') && !el?.hasAttribute('data-testid'))).toHaveTextContent('Mostrando 1 comprobante')

    // Limpiar búsqueda
    await user.clear(inputBuscar)
    expect(screen.getByText('Lucía Quispe Rojas')).toBeInTheDocument()

    // Filtrar por método Yape
    const selectMetodo = screen.getByRole('combobox')
    await user.selectOptions(selectMetodo, 'YAPE')

    expect(screen.getByText('Carlos Huamán')).toBeInTheDocument()
    expect(screen.queryByText('Lucía Quispe Rojas')).not.toBeInTheDocument()
  })

  it('se llama simplemente "Caja" (menú y título) y sin turno invita a abrirlo', async () => {
    servidor.use(
      http.get(`${API}/caja/activa`, () => new HttpResponse(null, { status: 204 })),
      http.get(`${API}/pagos/sede/:sedeId`, () => HttpResponse.json([])),
    )
    await renderRuta('/app/caja')

    const titulo = await screen.findByRole('heading', { level: 1, name: 'Caja' })
    expect(titulo).toHaveTextContent(/^Caja/)
    expect(screen.queryByText(/Facturación/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Caja' })).toHaveAttribute('href', '/app/caja')
    expect(screen.getByText('Sin Turno Activo')).toBeInTheDocument()
    expect(screen.getByText(/No hay un turno de caja abierto en esta sede/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Abrir Turno de Caja Ahora/i })).toBeInTheDocument()
  })
})
