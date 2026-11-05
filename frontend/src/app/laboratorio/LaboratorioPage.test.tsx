import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { LaboratorioCuenta, LaboratorioExterno, OrdenLaboratorio } from './types'

const API = 'http://localhost:3000/api/v1'

const LAB_HUANCAYO: LaboratorioExterno = {
  id: '50000000-0000-0000-0000-000000000001',
  nombre: 'Lab Dental Real Huancayo',
  contacto: 'Téc. Juan Pérez Huamán',
  telefono: '964 112 233',
  email: 'labreal@odontomax.pe',
  direccion: 'Jr. Puno 340, Huancayo',
  tiempoEntregaPromedioDias: 4,
  activo: true,
}

const LAB_CERAM: LaboratorioExterno = {
  id: '50000000-0000-0000-0000-000000000002',
  nombre: 'CeramLab 3D Especialistas',
  contacto: 'Dra. Carmen Salazar',
  telefono: '954 889 900',
  email: 'ceramlab3d@odontomax.pe',
  direccion: 'Av. Giráldez 512, Huancayo',
  tiempoEntregaPromedioDias: 5,
  activo: true,
}

const ORDEN_CORONA: OrdenLaboratorio = {
  id: '60000000-0000-0000-0000-000000000001',
  codigo: 'LAB-2026-0001',
  pacienteId: '30000000-0000-0000-0000-000000000001',
  pacienteNombre: 'Lucía Quispe Mayta',
  pacienteDni: '44556677',
  especialistaId: '10000000-0000-0000-0000-000000000001',
  especialistaNombre: 'Dr. Alex Ríos',
  sedeId: 'sede-chilca-1',
  laboratorioId: LAB_HUANCAYO.id,
  laboratorioNombre: LAB_HUANCAYO.nombre,
  tipoTrabajo: 'Corona Zirconio Monolítico',
  colorTono: 'A2',
  piezasDentales: '11',
  estado: 'ENVIADO',
  fechaSolicitud: '2026-10-08',
  fechaEstimadaEntrega: '2026-10-12',
  costoLaboratorio: 180,
  precioPaciente: 450,
  guiaRemision: 'G-2026-041',
  indicacionesTecnicas: 'Bisel subgingival 0.5mm, oclusión cúspide a fosa',
  observaciones: 'Paciente solicita estética natural',
  registradoPor: 'Recepcionista Turno Mañana',
  creadoEn: '2026-10-08T10:00:00Z',
  historial: [
    {
      id: 1,
      ordenId: '60000000-0000-0000-0000-000000000001',
      estadoNuevo: 'ENVIADO',
      usuarioNombre: 'Katherin Mendoza',
      usuarioRol: 'RECEPCION',
      motivo: 'Creación de orden',
      creadoEn: '2026-10-08T10:00:00Z',
    },
    {
      id: 2,
      ordenId: '60000000-0000-0000-0000-000000000001',
      estadoAnterior: 'ENVIADO',
      estadoNuevo: 'ENVIADO',
      usuarioNombre: 'Katherin Mendoza',
      usuarioRol: 'RECEPCION',
      motivo: 'Despacho con motorizado guía G-2026-041',
      creadoEn: '2026-10-08T11:30:00Z',
    },
  ],
}

const ORDEN_PROTESIS: OrdenLaboratorio = {
  id: '60000000-0000-0000-0000-000000000002',
  codigo: 'LAB-2026-0002',
  pacienteId: '30000000-0000-0000-0000-000000000002',
  pacienteNombre: 'Carlos Huamán Rojas',
  pacienteDni: '70889900',
  especialistaId: '10000000-0000-0000-0000-000000000002',
  especialistaNombre: 'Dra. Thalía Vilcapoma',
  sedeId: 'sede-chilca-1',
  laboratorioId: LAB_CERAM.id,
  laboratorioNombre: LAB_CERAM.nombre,
  tipoTrabajo: 'Prótesis Total Acrílico Lucitone',
  colorTono: 'A3',
  piezasDentales: 'Maxilar Superior Completo',
  estado: 'EN_PRUEBA',
  fechaSolicitud: '2026-10-05',
  fechaEstimadaEntrega: '2026-10-10',
  fechaRecepcionClinica: '2026-10-10T09:00:00Z',
  costoLaboratorio: 350,
  precioPaciente: 900,
  guiaRemision: 'T-980',
  indicacionesTecnicas: 'Caracterización gingival tono medio',
  registradoPor: 'Recepcionista Turno Mañana',
  creadoEn: '2026-10-05T08:00:00Z',
  historial: [],
}

function mockApiLaboratorio(
  ordenes = [ORDEN_CORONA, ORDEN_PROTESIS],
  proveedores = [LAB_HUANCAYO, LAB_CERAM],
  cuentas: LaboratorioCuenta[] = [],
) {
  servidor.use(
    http.get(`${API}/laboratorio/ordenes`, () => {
      return HttpResponse.json<OrdenLaboratorio[]>(ordenes)
    }),
    http.get(`${API}/laboratorio/ordenes/:id`, ({ params }) => {
      const encontrada = ordenes.find((o) => o.id === params.id)
      if (!encontrada) return new HttpResponse(null, { status: 404 })
      return HttpResponse.json<OrdenLaboratorio>(encontrada)
    }),
    http.get(`${API}/laboratorio/proveedores`, () => {
      return HttpResponse.json<LaboratorioExterno[]>(proveedores)
    }),
    http.get(`${API}/laboratorio/ordenes/:id/pagos`, () => HttpResponse.json([])),
    http.get(`${API}/laboratorio/cuentas`, () => HttpResponse.json<LaboratorioCuenta[]>(cuentas)),
    http.patch(`${API}/laboratorio/ordenes/:id/estado`, async ({ params, request }) => {
      const cuerpo = (await request.json()) as { nuevoEstado: string; motivo?: string }
      const orden = ordenes.find((o) => o.id === params.id)
      if (!orden) return new HttpResponse(null, { status: 404 })
      const actualizada = { ...orden, estado: cuerpo.nuevoEstado as OrdenLaboratorio['estado'] }
      return HttpResponse.json<OrdenLaboratorio>(actualizada)
    }),
  )
}

describe('Módulo de Laboratorio Dental & Prótesis (/app/laboratorio)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'jwt-token-test',
      usuario: {
        nombre: 'Dra. Administradora',
        rol: 'ADMIN_GENERAL',
        sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca-1',
    })
  })

  afterEach(() => {
    limpiarSesion()
  })

  it('renderiza título, KPIs y lista de órdenes con estado y colorimetría VITA', async () => {
    mockApiLaboratorio()
    await renderRuta('/app/laboratorio')

    expect(await screen.findByRole('heading', { name: /Laboratorio Dental & Prótesis/i })).toBeInTheDocument()

    // KPIs
    expect(screen.getAllByText('En laboratorio').length).toBeGreaterThan(0)
    expect(screen.getAllByText('En Clínica / Pruebas').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Instaladas / Entregadas').length).toBeGreaterThan(0)

    // Órdenes en la tabla
    expect(screen.getByText('LAB-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('Lucía Quispe Mayta')).toBeInTheDocument()
    expect(screen.getByText('Corona Zirconio Monolítico')).toBeInTheDocument()
    expect(screen.getAllByText('A2').length).toBeGreaterThan(0)

    expect(screen.getByText('LAB-2026-0002')).toBeInTheDocument()
    expect(screen.getByText('Carlos Huamán Rojas')).toBeInTheDocument()
    expect(screen.getAllByText('A3').length).toBeGreaterThan(0)
  })

  it('permite filtrar por término de búsqueda en la tabla', async () => {
    mockApiLaboratorio()
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')

    await screen.findByText('LAB-2026-0001')

    const buscador = screen.getByPlaceholderText(/Buscar por paciente, código LAB/i)
    await user.type(buscador, 'Lucía')

    expect(screen.getByText('LAB-2026-0001')).toBeInTheDocument()
    expect(screen.queryByText('LAB-2026-0002')).not.toBeInTheDocument()
  })

  it('permite abrir el modal de detalle y ver el timeline stepper de la orden', async () => {
    mockApiLaboratorio()
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')

    await screen.findByText('LAB-2026-0001')

    const botonesVer = screen.getAllByRole('button', { name: /Ver/i })
    await user.click(botonesVer[0])

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /LAB-2026-0001/i })).toBeInTheDocument()
    expect(within(dialog).getByText(/Especificaciones Técnicas Protésicas/i)).toBeInTheDocument()
    expect(within(dialog).getByText(/Bisel subgingival 0.5mm/i)).toBeInTheDocument()
    expect(within(dialog).getByText(/Despacho con motorizado/i)).toBeInTheDocument()
  })

  it('registra la prueba 1 con ajuste desde el detalle de la orden', async () => {
    mockApiLaboratorio([{ ...ORDEN_CORONA, estado: 'EN_PRUEBA' }])
    const cuerpos: { resultado: string; notas?: string }[] = []
    servidor.use(
      http.post(`${API}/laboratorio/ordenes/:id/pruebas`, async ({ request }) => {
        cuerpos.push((await request.json()) as { resultado: string; notas?: string })
        return HttpResponse.json<OrdenLaboratorio>({ ...ORDEN_CORONA, estado: 'ENVIADO' })
      }),
    )
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')
    await screen.findByText('LAB-2026-0001')
    await user.click(screen.getAllByRole('button', { name: /Ver/i })[0])
    const dialog = within(await screen.findByRole('dialog'))

    expect(dialog.getByText(/Todavía no hay pruebas/)).toBeInTheDocument()
    expect(dialog.getByText(/Prueba 1: en curso con el paciente/)).not.toBeNull()
    await user.click(dialog.getByLabelText(/Necesita ajuste/))
    await user.type(dialog.getByLabelText(/Qué se observó/), 'Alto en la cúspide')
    await user.click(dialog.getByRole('button', { name: 'Registrar prueba 1' }))

    await vi.waitFor(() =>
      expect(cuerpos).toEqual([expect.objectContaining({ resultado: 'NECESITA_AJUSTE', notas: 'Alto en la cúspide' })]),
    )
  })

  it('muestra las pruebas anteriores y numera la siguiente', async () => {
    mockApiLaboratorio([
      {
        ...ORDEN_CORONA,
        estado: 'EN_PRUEBA',
        pruebas: [{ id: 'pr-1', numero: 1, fecha: '2026-10-20', resultado: 'NECESITA_AJUSTE', notas: 'Falta color' }],
      },
    ])
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')
    await screen.findByText('LAB-2026-0001')
    await user.click(screen.getAllByRole('button', { name: /Ver/i })[0])
    const dialog = within(await screen.findByRole('dialog'))

    expect(await dialog.findByText('Prueba 1')).toBeInTheDocument()
    expect(dialog.getByText('Necesitó ajuste')).toBeInTheDocument()
    expect(dialog.getByText('Falta color')).toBeInTheDocument()
    expect(dialog.getByRole('button', { name: 'Registrar prueba 2' })).toBeInTheDocument()
  })

  it('un trabajo que está en el laboratorio se marca como llegado para empezar la prueba', async () => {
    mockApiLaboratorio()
    const cambios: { nuevoEstado: string; motivo?: string }[] = []
    servidor.use(
      http.patch(`${API}/laboratorio/ordenes/:id/estado`, async ({ request }) => {
        cambios.push((await request.json()) as { nuevoEstado: string; motivo?: string })
        return HttpResponse.json<OrdenLaboratorio>({ ...ORDEN_CORONA, estado: 'EN_PRUEBA' })
      }),
    )
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')
    await screen.findByText('LAB-2026-0001')
    await user.click(screen.getAllByRole('button', { name: /Ver/i })[0])
    const dialog = within(await screen.findByRole('dialog'))

    expect(dialog.queryByRole('button', { name: /Registrar prueba/ })).not.toBeInTheDocument()
    await user.click(dialog.getByRole('button', { name: /Llegó del laboratorio: empezar prueba 1/ }))

    await vi.waitFor(() => expect(cambios).toEqual([expect.objectContaining({ nuevoEstado: 'EN_PRUEBA' })]))
  })

  it('muestra finanzas y permite abrir el formulario con saldo y métodos de pago', async () => {
    const orden = {
      ...ORDEN_CORONA,
      estadoPago: 'PARCIAL' as const,
      montoPagado: 50,
      saldoLaboratorio: 130,
      ganancia: 270,
    }
    mockApiLaboratorio([orden])
    let solicitudesPago = 0
    servidor.use(
      http.post(`${API}/laboratorio/ordenes/:id/pagos`, () => {
        solicitudesPago += 1
        return HttpResponse.json({})
      }),
    )
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')
    await user.click((await screen.findAllByRole('button', { name: /Ver/i }))[0])

    const detalle = await screen.findByRole('region', { name: 'Finanzas del laboratorio' })
    expect(within(detalle).getByText('Costo del laboratorio')).toBeInTheDocument()
    expect(within(detalle).getByText('Ganancia')).toBeInTheDocument()
    expect(within(detalle).getByText('Parcial')).toBeInTheDocument()
    expect(within(detalle).getByText('S/ 130.00')).toBeInTheDocument()

    await user.click(within(detalle).getByRole('button', { name: 'Registrar pago al laboratorio' }))
    const pagoDialog = await screen.findByRole('heading', { name: 'Registrar pago al laboratorio' })
    const pagoForm = within(pagoDialog.closest('[role="dialog"]') as HTMLElement)
    expect(pagoForm.getByLabelText('Monto')).toHaveValue(130)
    await user.selectOptions(pagoForm.getByLabelText('Método de pago'), 'YAPE')
    await user.clear(pagoForm.getByLabelText('Monto'))
    await user.type(pagoForm.getByLabelText('Monto'), '131')
    await user.click(pagoForm.getByRole('button', { name: 'Registrar pago' }))

    expect(await pagoForm.findByRole('alert')).toHaveTextContent('no mayor a S/ 130.00')
    expect(solicitudesPago).toBe(0)
  })

  it('oculta costos, ganancia y pagos para odontólogo en lista y detalle', async () => {
    limpiarSesion()
    guardarSesion({
      token: 'jwt-doctor',
      usuario: { nombre: 'Doctor', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }] },
      sedeActivaId: 'sede-chilca-1',
    })
    _reiniciarCacheSesion()
    mockApiLaboratorio([{ ...ORDEN_CORONA, estadoPago: 'PARCIAL', montoPagado: 50, saldoLaboratorio: 130, ganancia: 270 }])
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')

    expect(await screen.findByText('LAB-2026-0001')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Pago' })).not.toBeInTheDocument()
    expect(screen.queryByText('S/ 180.00')).not.toBeInTheDocument()
    await user.click((await screen.findAllByRole('button', { name: /Ver/i }))[0])
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByRole('region', { name: 'Finanzas del laboratorio' })).not.toBeInTheDocument()
    expect(within(dialog).queryByText('Precio al paciente')).not.toBeInTheDocument()
  })

  it('no muestra costos ni precio al paciente en el formulario de odontólogo', async () => {
    limpiarSesion()
    guardarSesion({
      token: 'jwt-doctor',
      usuario: { nombre: 'Doctor', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca-1', nombre: 'Sede Chilca 1', marca: 'VILLADENT' }] },
      sedeActivaId: 'sede-chilca-1',
    })
    _reiniciarCacheSesion()
    mockApiLaboratorio()
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')

    await user.click(await screen.findByRole('button', { name: /Nueva Orden Protésica/i }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByLabelText('Costo que nos cobra el laboratorio (S/)')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Precio al paciente (S/)')).not.toBeInTheDocument()
  })

  it('muestra facturado, pagado y por pagar en cuentas con laboratorios', async () => {
    mockApiLaboratorio([ORDEN_CORONA], [LAB_HUANCAYO], [{
      laboratorioId: LAB_HUANCAYO.id,
      laboratorioNombre: LAB_HUANCAYO.nombre,
      totalFacturado: 700,
      totalPagado: 250,
      saldoPorPagar: 450,
      ordenes: [{
        ordenId: ORDEN_CORONA.id,
        codigo: ORDEN_CORONA.codigo,
        sedeId: ORDEN_CORONA.sedeId,
        costoLaboratorio: 500,
        montoPagado: 250,
        saldoLaboratorio: 250,
        estadoPago: 'PARCIAL',
      }],
    }])
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')
    await user.click(await screen.findByRole('button', { name: 'Cuentas con laboratorios' }))

    expect(await screen.findByRole('heading', { name: 'Cuentas con laboratorios' })).toBeInTheDocument()
    expect(screen.getByText('Lab Dental Real Huancayo')).toBeInTheDocument()
    expect(screen.getByText('S/ 700.00')).toBeInTheDocument()
    expect(screen.getAllByText('S/ 250.00')).toHaveLength(3)
    expect(screen.getByText('S/ 450.00')).toBeInTheDocument()
  })

  it('permite abrir el directorio de laboratorios dentales externos', async () => {
    mockApiLaboratorio()
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')

    const botonDirectorio = await screen.findByRole('button', { name: /Directorio Laboratorios/i })
    await user.click(botonDirectorio)

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /Directorio de Laboratorios Dentales Externos/i })).toBeInTheDocument()
    expect(within(dialog).getByText('Lab Dental Real Huancayo')).toBeInTheDocument()
    expect(within(dialog).getByText('CeramLab 3D Especialistas')).toBeInTheDocument()
  })

  it('la nueva orden es un formulario simple: sin los botones de colorimetría, con el color como texto opcional', async () => {
    mockApiLaboratorio()
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')

    await user.click(await screen.findByRole('button', { name: /Nueva Orden Protésica/i }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: /Nueva Orden de Laboratorio Dental/i })).toBeInTheDocument()

    // Ya no está el bloque de 30 botones de la guía VITA.
    expect(within(dialog).queryByText(/Colorimetría Protésica/i)).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'BL1' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'A2' })).not.toBeInTheDocument()
    expect(within(dialog).getByLabelText('Color / Tono dental (opcional, ej. A2)')).toHaveValue('')

    // Lo administrativo y financiero, a la vista.
    expect(within(dialog).getByLabelText(/Tratamiento \/ Trabajo protésico/)).toBeInTheDocument()
    expect(within(dialog).getByLabelText(/Laboratorio externo/)).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Costo que nos cobra el laboratorio (S/)')).toHaveAccessibleDescription(
      'Gasto directo de la clínica con el taller.',
    )
    expect(within(dialog).getByLabelText('Fecha estimada de entrega')).toBeInTheDocument()
    expect(within(dialog).getByText(/recibido en clínica, instalado\) se marca desde el detalle de la orden/)).toBeInTheDocument()
    // Lo secundario queda plegado.
    expect(within(dialog).getByText('Más datos (opcional)').closest('details')).not.toHaveAttribute('open')
  })

  it('emite la orden con trabajo, laboratorio, costo, fecha y el color escrito', async () => {
    mockApiLaboratorio()
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.get(`${API}/especialistas`, () =>
        HttpResponse.json([{ id: '9d0e1f2a-0000-4000-8000-000000000001', nombre: 'Dra. Elena Paucar', activo: true }]),
      ),
      http.get(`${API}/pacientes`, () =>
        HttpResponse.json([{ id: 'p-lab-1', dni: '45908123', nombres: 'Luciana', apellidos: 'Meza', nombreCompleto: 'Luciana Meza', sedeId: 'sede-chilca-1' }]),
      ),
      http.post(`${API}/laboratorio/ordenes`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...cuerpo, id: 'o-nueva', codigo: 'OL-0099', estado: 'ENVIADO' })
      }),
    )
    const user = userEvent.setup({ delay: null })
    await renderRuta('/app/laboratorio')
    await user.click(await screen.findByRole('button', { name: /Nueva Orden Protésica/i }))
    const dialog = within(await screen.findByRole('dialog'))

    await user.selectOptions(await dialog.findByLabelText(/Odontólogo Tratante/), '9d0e1f2a-0000-4000-8000-000000000001')
    await user.type(dialog.getByRole('searchbox', { name: 'Paciente' }), 'Luci')
    await user.click(await dialog.findByRole('button', { name: /Luciana Meza/ }))
    await user.type(dialog.getByLabelText(/Tratamiento \/ Trabajo protésico/), 'Corona Zirconio Monolítico')
    await user.selectOptions(dialog.getByLabelText(/Laboratorio externo/), LAB_HUANCAYO.id)
    await user.type(dialog.getByLabelText('Costo que nos cobra el laboratorio (S/)'), '180')
    await user.clear(dialog.getByLabelText('Fecha estimada de entrega'))
    await user.type(dialog.getByLabelText('Fecha estimada de entrega'), '2026-10-30')
    await user.type(dialog.getByLabelText('Color / Tono dental (opcional, ej. A2)'), 'A3')
    await user.click(dialog.getByRole('button', { name: 'Emitir Orden a Laboratorio' }))

    await vi.waitFor(() => expect(cuerpo).not.toBeNull())
    expect(cuerpo).toMatchObject({
      pacienteId: 'p-lab-1',
      laboratorioId: LAB_HUANCAYO.id,
      tipoTrabajo: 'Corona Zirconio Monolítico',
      costoLaboratorio: 180,
      fechaEstimadaEntrega: '2026-10-30',
      colorTono: 'A3',
    })
  })
})
