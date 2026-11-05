import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { NuevoTratamientoModal } from './NuevoTratamientoModal'

const CATALOGO = [
  { id: 'srv-limpieza', nombre: 'Limpieza dental', especialidad: 'Odontología general', precio: 70, activo: true, orden: 1 },
  { id: 'srv-curacion', nombre: 'Curación', especialidad: 'Odontología general', precio: 80, precioLeve: 50, precioModerado: 80, precioSevero: 120, activo: true, orden: 4 },
  { id: 'srv-resina', nombre: 'Resina compuesta', especialidad: 'Estética Dental', activo: true, orden: 2 },
  { id: 'srv-viejo', nombre: 'Servicio retirado', precio: 10, activo: false, orden: 3 },
]

let cuerpos: Record<string, unknown>[] = []
let pagos: Record<string, unknown>[] = []
let adelantosFallan = 0

function api(opciones: { error?: boolean; adelantosFallan?: number } = {}) {
  cuerpos = []
  pagos = []
  adelantosFallan = opciones.adelantosFallan ?? 0
  servidor.use(
    http.get(`${API}/especialistas`, () =>
      HttpResponse.json([{ id: 'e-1', nombre: 'Dra. Elena Paucar', especialidad: 'Ortodoncia', sedes: ['sede-chilca'], sillonesPorSede: { 'sede-chilca': 2 }, activo: true }]),
    ),
    http.get(`${API}/servicios`, () => HttpResponse.json(CATALOGO)),
    http.get(`${API}/plantillas-plan`, () => HttpResponse.json([])),
    http.post(`${API}/tratamientos`, async ({ request }) => {
      cuerpos.push((await request.json()) as Record<string, unknown>)
      if (opciones.error) return errorBackend(400, 'El costo total no coincide con los ítems')
      return HttpResponse.json({
        id: 't-nuevo',
        pacienteId: 'p-1',
        especialistaId: 'e-1',
        nombre: 'Rehabilitación oral',
        costoTotal: 120,
        saldoPendiente: 120,
        estado: 'EN_PROCESO',
      })
    }),
    http.post(`${API}/pagos`, async ({ request }) => {
      pagos.push((await request.json()) as Record<string, unknown>)
      if (adelantosFallan > 0) {
        adelantosFallan -= 1
        return errorBackend(409, 'No se pudo registrar el adelanto')
      }
      return HttpResponse.json({ id: 'pg-nuevo', comprobante: 'R001-000123' })
    }),
  )
}

function montar() {
  const alCerrar = vi.fn()
  const alGuardar = vi.fn()
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <NuevoTratamientoModal
        abierto
        alCerrar={alCerrar}
        pacienteId="p-1"
        pacienteNombre="Luciana Meza Cárdenas"
        sedeId="sede-chilca"
        alGuardar={alGuardar}
      />
    </QueryClientProvider>,
  )
  return { alCerrar, alGuardar }
}

let user: ReturnType<typeof userEvent.setup>

const escribir = async (etiqueta: string, texto: string) => {
  const campo = screen.getByLabelText(etiqueta)
  await user.clear(campo)
  await user.type(campo, texto)
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  user = userEvent.setup({ delay: null })
})

describe('Descuento por recomendación', { timeout: 15_000 }, () => {
  const PENDIENTE = { id: 'd-1', referidoId: 'p-9', referidoNombre: 'Pedro Ruiz', estado: 'PENDIENTE', creadoEn: '2026-10-20T10:00:00Z' }

  it('avisa que el paciente recomendó a alguien y, al darlo, deja el motivo y marca el descuento como aplicado', async () => {
    api()
    let aplicado: unknown
    servidor.use(
      http.get(`${API}/pacientes/:id/descuentos-recomendacion`, () => HttpResponse.json([PENDIENTE])),
      http.post(`${API}/pacientes/:id/descuentos-recomendacion/:d/aplicar`, async ({ request }) => {
        aplicado = await request.json()
        return HttpResponse.json({ ...PENDIENTE, estado: 'APLICADO' })
      }),
    )
    guardarSesion({ token: 't', usuario: { id: 'u-1', nombre: 'Administrador', rol: 'ADMIN_GENERAL', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] }, sedeActivaId: 'sede-chilca' })
    _reiniciarCacheSesion()
    montar()

    const aviso = await screen.findByRole('region', { name: 'Descuento por recomendación' })
    expect(within(aviso).getByText(/recomendó a Pedro Ruiz/)).toBeInTheDocument()
    await screen.findByRole('option', { name: /Limpieza dental/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-limpieza')
    await user.selectOptions(await screen.findByLabelText('Doctor responsable'), 'e-1')
    await user.click(within(aviso).getByLabelText(/Dar el descuento en este plan/))
    await escribir('Presupuesto total (S/) *', '60')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0]).toMatchObject({ costoTotal: 60, motivoAjustePrecio: 'Descuento por recomendación de Pedro Ruiz' })
    await waitFor(() => expect(aplicado).toEqual({ tratamientoId: 't-nuevo' }))
  })

  it('no deja dar el descuento si el presupuesto no baja del precio del catálogo', async () => {
    api()
    servidor.use(http.get(`${API}/pacientes/:id/descuentos-recomendacion`, () => HttpResponse.json([PENDIENTE])))
    guardarSesion({ token: 't', usuario: { id: 'u-1', nombre: 'Administrador', rol: 'ADMIN_GENERAL', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] }, sedeActivaId: 'sede-chilca' })
    _reiniciarCacheSesion()
    montar()

    const aviso = await screen.findByRole('region', { name: 'Descuento por recomendación' })
    await screen.findByRole('option', { name: /Limpieza dental/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-limpieza')
    await user.selectOptions(await screen.findByLabelText('Doctor responsable'), 'e-1')
    await user.click(within(aviso).getByLabelText(/Dar el descuento en este plan/))
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    expect(await screen.findByText(/baja el presupuesto por debajo del precio del catálogo/)).toBeInTheDocument()
    expect(cuerpos).toHaveLength(0)
  })
})

describe('Costo por pieza y gravedad', { timeout: 15_000 }, () => {
  it('el total es piezas × costo por pieza y la gravedad cambia el costo del catálogo', async () => {
    api()
    montar()
    await screen.findByRole('dialog')
    await screen.findByRole('option', { name: /Curación/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-curacion')
    await user.selectOptions(screen.getByLabelText('Gravedad del caso'), 'SEVERO')
    expect(screen.getByLabelText('Costo por pieza (S/)')).toHaveValue(120)

    await escribir('Piezas o unidades', '3')
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(360)

    await user.selectOptions(screen.getByLabelText('Gravedad del caso'), 'LEVE')
    expect(screen.getByLabelText('Costo por pieza (S/)')).toHaveValue(50)
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(150)
  })

  it('manda al plan las piezas, el costo unitario y la gravedad', async () => {
    api()
    montar()
    await screen.findByRole('dialog')
    await screen.findByRole('option', { name: /Curación/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-curacion')
    await user.selectOptions(screen.getByLabelText('Gravedad del caso'), 'MODERADO')
    await escribir('Piezas o unidades', '2')
    await user.selectOptions(await screen.findByLabelText('Doctor responsable'), 'e-1')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0]).toMatchObject({ costoTotal: 160 })
    expect(cuerpos[0].items).toEqual([
      expect.objectContaining({ procedimientoId: 'srv-curacion', cantidad: 2, precioBase: 80, gravedad: 'MODERADO' }),
    ])
  })
})

describe('Varios casos del mismo tratamiento', { timeout: 15_000 }, () => {
  it('permite sumar casos con otra gravedad: el total los suma y cada uno va como su propio ítem', async () => {
    api()
    montar()
    await screen.findByRole('dialog')
    await screen.findByRole('option', { name: /Curación/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-curacion')
    await user.selectOptions(screen.getByLabelText('Gravedad del caso'), 'LEVE')

    await user.click(screen.getByRole('button', { name: /Agregar otro caso/ }))
    await user.selectOptions(screen.getByLabelText('Gravedad del caso 2'), 'MODERADO')
    await user.click(screen.getByRole('button', { name: /Agregar otro caso/ }))
    await user.selectOptions(screen.getByLabelText('Gravedad del caso 3'), 'SEVERO')
    await escribir('Piezas del caso 3', '2')

    // 1 × 50 + 1 × 80 + 2 × 120
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(370)

    await user.click(screen.getByRole('button', { name: 'Quitar el caso 2' }))
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(290)
    await user.click(screen.getByRole('button', { name: /Agregar otro caso/ }))
    await user.selectOptions(screen.getByLabelText('Gravedad del caso 3'), 'MODERADO')
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(370)

    await user.selectOptions(await screen.findByLabelText('Doctor responsable'), 'e-1')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0]).toMatchObject({ costoTotal: 370 })
    expect(cuerpos[0].items).toEqual([
      expect.objectContaining({ cantidad: 1, precioBase: 50, gravedad: 'LEVE' }),
      expect.objectContaining({ cantidad: 2, precioBase: 120, gravedad: 'SEVERO' }),
      expect.objectContaining({ cantidad: 1, precioBase: 80, gravedad: 'MODERADO' }),
    ])
  })

  it('un servicio sin costo por gravedad no ofrece agregar casos', async () => {
    api()
    montar()
    await screen.findByRole('dialog')
    await screen.findByRole('option', { name: /Limpieza dental/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-limpieza')
    expect(screen.queryByRole('button', { name: /Agregar otro caso/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Gravedad del caso')).not.toBeInTheDocument()
  })
})

describe('Plantillas de plan', { timeout: 15_000 }, () => {
  const PLANTILLA = { id: 'pl-1', nombre: 'Curación de molares', procedimientoId: 'srv-curacion', servicioNombre: 'Curación', piezas: 3, gravedad: 'MODERADO', sesiones: 2, notas: 'Con anestesia' }

  it('al elegir una plantilla precarga servicio, piezas, gravedad, sesiones y el precio del catálogo', async () => {
    api()
    servidor.use(http.get(`${API}/plantillas-plan`, () => HttpResponse.json([PLANTILLA])))
    montar()
    await screen.findByRole('dialog')
    await screen.findByRole('option', { name: /^Curación · S/ })
    await user.selectOptions(await screen.findByLabelText('Usar una plantilla'), 'pl-1')

    expect(screen.getByLabelText('Tratamiento del catálogo')).toHaveValue('srv-curacion')
    expect(screen.getByLabelText('Nombre del plan *')).toHaveValue('Curación de molares')
    expect(screen.getByLabelText('Gravedad del caso', { exact: true })).toHaveValue('MODERADO')
    expect(screen.getByLabelText('Piezas o unidades')).toHaveValue(3)
    expect(screen.getByLabelText('Costo por pieza (S/)')).toHaveValue(80)
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(240)
    expect(screen.getByLabelText('Número de sesiones estimadas *')).toHaveValue(2)
    expect(screen.getByLabelText('Notas (opcional)')).toHaveValue('Con anestesia')
  })

  it('con un nombre escrito, al crear el plan también guarda la plantilla', async () => {
    api()
    const plantillasCreadas: Record<string, unknown>[] = []
    servidor.use(
      http.get(`${API}/plantillas-plan`, () => HttpResponse.json([])),
      http.post(`${API}/plantillas-plan`, async ({ request }) => {
        plantillasCreadas.push((await request.json()) as Record<string, unknown>)
        return HttpResponse.json({ id: 'pl-nueva' }, { status: 201 })
      }),
    )
    montar()
    await screen.findByRole('dialog')
    await screen.findByRole('option', { name: /Curación/ })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-curacion')
    await user.selectOptions(screen.getByLabelText('Gravedad del caso', { exact: true }), 'SEVERO')
    await escribir('Piezas o unidades', '2')
    await user.type(screen.getByLabelText('Guardar como plantilla (opcional)'), 'Curación severa x2')
    await user.selectOptions(await screen.findByLabelText('Doctor responsable'), 'e-1')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(plantillasCreadas).toHaveLength(1))
    expect(plantillasCreadas[0]).toMatchObject({ nombre: 'Curación severa x2', procedimientoId: 'srv-curacion', piezas: 2, gravedad: 'SEVERO', sesiones: 1 })
    expect(cuerpos).toHaveLength(1)
  })
})

describe('Nuevo plan de tratamiento', { timeout: 15_000 }, () => {
  it('muestra el paciente y los campos simples del plan', async () => {
    api()
    montar()
    const dialogo = await screen.findByRole('dialog', { name: 'Nuevo Plan de Tratamiento' })
    const d = within(dialogo)
    expect(d.getByText(/Luciana Meza Cárdenas/)).toBeInTheDocument()
    expect(d.getByLabelText('Tratamiento del catálogo')).toBeInTheDocument()
    expect(d.getByLabelText('Nombre del plan *')).toBeInTheDocument()
    expect(d.getByLabelText('Doctor responsable')).toBeInTheDocument()
    expect(d.getByLabelText('Presupuesto total (S/) *')).toBeInTheDocument()
    expect(d.getByLabelText('Número de sesiones estimadas *')).toHaveValue(1)
    expect(d.getByLabelText('Adelanto hoy (S/, opcional)')).toBeInTheDocument()
    expect(d.getByLabelText('Notas (opcional)')).toBeInTheDocument()
    expect(d.queryByLabelText(/Diagnóstico|Modalidad|Fases|Pieza FDI|Descuento/i)).not.toBeInTheDocument()
    expect(await d.findByRole('option', { name: 'Dra. Elena Paucar' })).toBeInTheDocument()
  })

  it('permite editar el presupuesto y el número de sesiones estimadas', async () => {
    api()
    montar()
    await screen.findByRole('dialog')
    await escribir('Presupuesto total (S/) *', '200')
    await escribir('Número de sesiones estimadas *', '5')
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(200)
    expect(screen.getByLabelText('Número de sesiones estimadas *')).toHaveValue(5)
  })

  it('ofrece el catálogo de servicios activos y autocompleta el precio, que sigue siendo editable', async () => {
    api()
    montar()
    await screen.findByRole('dialog')
    const selector = screen.getByLabelText('Tratamiento del catálogo')
    await waitFor(() => expect(within(selector).getAllByRole('option')).toHaveLength(4))
    expect(within(selector).queryByRole('option', { name: /Servicio retirado/ })).not.toBeInTheDocument()
    await user.selectOptions(selector, 'srv-limpieza')
    expect(screen.getByLabelText('Nombre del plan *')).toHaveValue('Limpieza dental')
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(70)
    await escribir('Presupuesto total (S/) *', '55')
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(55)
    await user.selectOptions(selector, 'srv-resina')
    expect(screen.getByLabelText('Nombre del plan *')).toHaveValue('Resina compuesta')
    expect(screen.getByLabelText('Presupuesto total (S/) *')).toHaveValue(55)
  })

  it('envía el payload simple del plan, avisa con alGuardar y cierra el modal', async () => {
    api()
    const { alCerrar, alGuardar } = montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-limpieza')
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await escribir('Número de sesiones estimadas *', '5')

    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(alGuardar).toHaveBeenCalledTimes(1))
    expect(cuerpos).toEqual([
      {
        pacienteId: 'p-1',
        sedeId: 'sede-chilca',
        especialistaId: 'e-1',
        nombre: 'Limpieza dental',
        modalidadPago: 'CUOTAS',
        costoTotal: 70,
        descuentoTotal: 0,
        totalSesiones: 5,
        pagadoAntesSistema: 0,
        items: [
          { procedimientoId: 'srv-limpieza', nombreProcedimiento: 'Limpieza dental', precioBase: 70, cantidad: 1, descuento: 0 },
        ],
      },
    ])
    expect(alGuardar).toHaveBeenCalledWith(expect.objectContaining({ id: 't-nuevo' }))
    expect(alCerrar).toHaveBeenCalledTimes(1)
  })

  it('oculta el pago previo del sistema y liga el adelanto al plan creado', async () => {
    api()
    montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    expect(screen.queryByLabelText(/Monto ya cancelado antes del sistema/)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Nombre del plan *'), 'Ortodoncia con brackets')
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await user.type(screen.getByLabelText('Presupuesto total (S/) *'), '300')
    await user.type(screen.getByLabelText('Adelanto hoy (S/, opcional)'), '100')

    await user.click(screen.getByRole('button', { name: 'Crear plan' }))
    await waitFor(() => expect(cuerpos).toHaveLength(1))
    await waitFor(() => expect(pagos).toHaveLength(1))
    expect(cuerpos[0].pagadoAntesSistema).toBe(0)
    expect(pagos[0]).toMatchObject({ pacienteId: 'p-1', tratamientoId: 't-nuevo', monto: 100, metodoPago: 'EFECTIVO' })
  })

  it('reintenta un adelanto fallido sin duplicar el plan', async () => {
    api({ adelantosFallan: 1 })
    montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    await user.type(screen.getByLabelText('Nombre del plan *'), 'Ortodoncia')
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await user.type(screen.getByLabelText('Presupuesto total (S/) *'), '300')
    await user.type(screen.getByLabelText('Adelanto hoy (S/, opcional)'), '100')

    await user.click(screen.getByRole('button', { name: 'Crear plan' }))
    expect(await screen.findByText(/El plan ya está creado; al reintentar se usará el mismo plan/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar adelanto' }))

    await waitFor(() => expect(pagos).toHaveLength(2))
    expect(cuerpos).toHaveLength(1)
    expect(pagos.map((pago) => pago.tratamientoId)).toEqual(['t-nuevo', 't-nuevo'])
  })

  it('sin nombre del plan no se envía nada', async () => {
    api()
    const { alCerrar, alGuardar } = montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await user.type(screen.getByLabelText('Presupuesto total (S/) *'), '100')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))
    expect(cuerpos).toEqual([])
    expect(alGuardar).not.toHaveBeenCalled()
    expect(alCerrar).not.toHaveBeenCalled()
  })

  it('si el backend rechaza el plan muestra su mensaje y deja el modal abierto', async () => {
    api({ error: true })
    const { alCerrar, alGuardar } = montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    await user.type(screen.getByLabelText('Nombre del plan *'), 'Plan con error')
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await user.type(screen.getByLabelText('Presupuesto total (S/) *'), '100')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))
    expect(await screen.findByText('El costo total no coincide con los ítems')).toBeInTheDocument()
    expect(alGuardar).not.toHaveBeenCalled()
    expect(alCerrar).not.toHaveBeenCalled()
  })

  it('plan en cuotas: el presupuesto es la suma del cronograma y se envían las cuotas', async () => {
    api()
    montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await escribir('Nombre del plan *', 'Ortodoncia convencional')
    await user.click(screen.getByLabelText('Pagar en cuotas (ortodoncia)'))
    await escribir('Cuota inicial (S/)', '600')
    await escribir('Cuota mensual (S/)', '120')
    await escribir('N.º de cuotas mensuales', '3')
    await escribir('Primer vencimiento', '2026-11-01')

    expect(screen.getByLabelText('Presupuesto total (suma de las cuotas)')).toHaveValue(960)

    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0].cuotas).toEqual({ montoInicial: 600, montoMensual: 120, cantidadMensuales: 3, primerVencimiento: '2026-11-01' })
  })

  it('precio acordado distinto al del catálogo: pide el motivo y lo envía junto al precio de catálogo', async () => {
    api()
    montar()
    await screen.findByRole('option', { name: 'Dra. Elena Paucar' })
    await user.selectOptions(screen.getByLabelText('Tratamiento del catálogo'), 'srv-limpieza')
    await user.selectOptions(screen.getByLabelText('Doctor responsable'), 'e-1')
    await escribir('Presupuesto total (S/) *', '90')

    await user.click(screen.getByRole('button', { name: 'Crear plan' }))
    expect(await screen.findByText(/indica el motivo/i)).toBeInTheDocument()
    expect(cuerpos).toHaveLength(0)

    await user.type(screen.getByLabelText(/Motivo del precio acordado/), 'Pieza con mucha pérdida de estructura')
    await user.click(screen.getByRole('button', { name: 'Crear plan' }))

    await waitFor(() => expect(cuerpos).toHaveLength(1))
    expect(cuerpos[0].precioCatalogo).toBe(70)
    expect(cuerpos[0].motivoAjustePrecio).toBe('Pieza con mucha pérdida de estructura')
  })

  it('un odontólogo solo puede crear el plan a su propio nombre y no ve a los demás doctores', async () => {
    api()
    servidor.use(
      http.get(`${API}/especialistas`, () =>
        HttpResponse.json([
          { id: 'e-1', usuarioId: 'u-odo', nombre: 'Doctor B', sedes: ['sede-chilca'], sillonesPorSede: { 'sede-chilca': 2 }, activo: true },
          { id: 'e-2', usuarioId: 'u-otro', nombre: 'Doctor C', sedes: ['sede-chilca'], sillonesPorSede: { 'sede-chilca': 3 }, activo: true },
        ]),
      ),
    )
    guardarSesion({
      token: 't',
      usuario: { id: 'u-odo', nombre: 'Doctor B', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
      sedeActivaId: 'sede-chilca',
    })
    _reiniciarCacheSesion()
    montar()
    const d = within(await screen.findByRole('dialog', { name: 'Nuevo Plan de Tratamiento' }))

    const doctor = await d.findByLabelText('Doctor responsable')
    await waitFor(() => expect(doctor).toHaveValue('e-1'))
    expect(doctor).toBeDisabled()
    expect(d.queryByRole('option', { name: 'Doctor C' })).not.toBeInTheDocument()
  })
})
