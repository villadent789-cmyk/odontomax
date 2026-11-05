import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { ComisionRadiologicaDTO, DoctorDerivadorDTO, ResumenComisionesDTO } from './types'

const API = 'http://localhost:3000/api/v1'

const DOCTOR_PALOMINO: DoctorDerivadorDTO = {
  id: 'd0000001-0000-4000-8000-000000000001',
  nombre: 'Dr. Carlos Palomino Quispe',
  cop: 'COP 28410',
  clinicaOrigen: 'Clínica Dental Sonrisas (El Tambo)',
  telefono: '964123456',
  banco: 'BCP / Yape',
  numeroCuentaOYape: '964123456',
  porcentajeOTarifaFija: 10,
  activo: true,
  totalComisionesPendientes: 10,
  cantidadEstudiosDerivados: 2,
}

const DOCTOR_HUAMAN: DoctorDerivadorDTO = {
  id: 'd0000002-0000-4000-8000-000000000002',
  nombre: 'Dra. Patricia Huamán Ramos',
  cop: 'COP 31055',
  clinicaOrigen: 'Consultorio Dental Art (Centro)',
  telefono: '954789123',
  banco: 'Interbank / Plin',
  numeroCuentaOYape: '954789123',
  porcentajeOTarifaFija: 35,
  activo: true,
  totalComisionesPendientes: 35,
  cantidadEstudiosDerivados: 1,
}

const COMISION_PANORAMICA: ComisionRadiologicaDTO = {
  id: 'c0000001-0000-4000-8000-000000000001',
  doctorDerivadorId: DOCTOR_PALOMINO.id,
  doctorDerivadorNombre: DOCTOR_PALOMINO.nombre,
  doctorDerivadorCop: DOCTOR_PALOMINO.cop,
  doctorDerivadorClinica: DOCTOR_PALOMINO.clinicaOrigen,
  doctorDerivadorTelefono: DOCTOR_PALOMINO.telefono,
  doctorDerivadorCuenta: DOCTOR_PALOMINO.numeroCuentaOYape,
  pacienteNombre: 'Luciana Meza Cárdenas',
  tipoEstudio: 'PANORAMICA',
  precioCobrado: 50,
  montoComision: 10,
  estado: 'PENDIENTE',
  fechaEmision: '2026-10-10T10:00:00Z',
  sedeId: 'sede-villamax-chilca',
  creadoEn: '2026-10-10T10:00:00Z',
}

const COMISION_CONEBEAM: ComisionRadiologicaDTO = {
  id: 'c0000002-0000-4000-8000-000000000002',
  doctorDerivadorId: DOCTOR_HUAMAN.id,
  doctorDerivadorNombre: DOCTOR_HUAMAN.nombre,
  doctorDerivadorCop: DOCTOR_HUAMAN.cop,
  doctorDerivadorClinica: DOCTOR_HUAMAN.clinicaOrigen,
  doctorDerivadorTelefono: DOCTOR_HUAMAN.telefono,
  doctorDerivadorCuenta: DOCTOR_HUAMAN.numeroCuentaOYape,
  pacienteNombre: 'Gonzalo Astuvilca Ramos',
  tipoEstudio: 'TOMOGRAFIA_3D_CONE_BEAM',
  precioCobrado: 180,
  montoComision: 35,
  estado: 'PENDIENTE',
  fechaEmision: '2026-10-11T11:00:00Z',
  sedeId: 'sede-villamax-chilca',
  creadoEn: '2026-10-11T11:00:00Z',
}

const RESUMEN_MOCK: ResumenComisionesDTO = {
  totalMontoPendiente: 45,
  cantidadPendientes: 2,
  totalMontoLiquidado: 12,
  cantidadLiquidados: 1,
  totalDoctoresDerivadoresActivos: 2,
}

describe('Módulo de Radiología & Comisiones (/app/radiologia)', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'token-director-a',
      usuario: {
        nombre: 'Doctor A (Director)',
        correo: 'admin.general@odontomax.test',
        rol: 'ADMIN_GENERAL',
        sedes: [{ id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' }],
      },
      sedeActivaId: 'sede-villamax-chilca',
      negocioActivo: 'VILLAMAX',
    })

    servidor.use(
      http.get(`${API}/radiologia/comisiones`, () => {
        return HttpResponse.json([COMISION_PANORAMICA, COMISION_CONEBEAM])
      }),
      http.get(`${API}/radiologia/derivadores`, () => {
        return HttpResponse.json([DOCTOR_PALOMINO, DOCTOR_HUAMAN])
      }),
      http.get(`${API}/radiologia/comisiones/resumen`, () => {
        return HttpResponse.json(RESUMEN_MOCK)
      }),
      http.get(`${API}/radiologia/comisiones/fallidas`, () => HttpResponse.json([])),
    )
  })

  afterEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
  })

  it('renderiza título, KPIs y lista de comisiones con datos de Villamax Chilca', async () => {
    await renderRuta('/app/radiologia')

    expect(await screen.findByRole('heading', { name: /Villamax · Centro Radiológico/i })).toBeInTheDocument()
    expect(screen.getByText(/Jr. Ricardo Palma 411, Chilca/i)).toBeInTheDocument()

    // KPIs
    expect(screen.getByText('S/ 45.00')).toBeInTheDocument()
    expect(screen.getByText('S/ 12.00')).toBeInTheDocument()

    // Pacientes y doctores en la tabla
    expect(screen.getByText('Luciana Meza Cárdenas')).toBeInTheDocument()
    expect(screen.getByText('Gonzalo Astuvilca Ramos')).toBeInTheDocument()
    expect(screen.getByText('Dr. Carlos Palomino Quispe')).toBeInTheDocument()
  })

  it('permite filtrar por término de búsqueda en la tabla de comisiones', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/radiologia')

    expect(await screen.findByText('Luciana Meza Cárdenas')).toBeInTheDocument()

    const input = screen.getByPlaceholderText(/Buscar paciente o doctor/i)
    await user.type(input, 'Gonzalo')

    expect(screen.queryByText('Luciana Meza Cárdenas')).not.toBeInTheDocument()
    expect(screen.getByText('Gonzalo Astuvilca Ramos')).toBeInTheDocument()
  })

  it('permite alternar a la pestaña Directorio de Doctores Derivadores', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/radiologia')

    const btnTabDoctores = await screen.findByRole('button', { name: /Doctores Derivadores/i })
    await user.click(btnTabDoctores)

    expect(await screen.findByText('Clínica Dental Sonrisas (El Tambo)')).toBeInTheDocument()
    expect(screen.getByText('Consultorio Dental Art (Centro)')).toBeInTheDocument()
    expect(screen.getByText('COP 28410')).toBeInTheDocument()
  })

  it('permite abrir el modal de nuevo doctor derivador', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/radiologia')

    const btnNuevoDoctor = await screen.findByRole('button', { name: /\+ Doctor Derivador/i })
    await user.click(btnNuevoDoctor)

    expect(await screen.findByRole('heading', { name: /Registrar Doctor Derivador/i })).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/Ej. Dr. Carlos Palomino Quispe/i)).toBeInTheDocument()
  })

  it('permite abrir el modal de registrar comisión', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/radiologia')

    const btnRegistrar = await screen.findByRole('button', { name: /\+ Registrar Comisión/i })
    await user.click(btnRegistrar)

    const dialogo = within(await screen.findByRole('dialog'))
    expect(dialogo.getByRole('heading', { name: /Registrar Comisión de Derivación/i })).toBeInTheDocument()
    expect(dialogo.getByLabelText(/Estudio radiológico/i)).toBeInTheDocument()
    expect(dialogo.getByLabelText(/Doctor derivador/i)).toBeInTheDocument()
    // La tarifa y la comisión salen del catálogo: ya no se escriben a mano.
    expect(dialogo.queryByLabelText(/Precio Cobrado/i)).not.toBeInTheDocument()
  })

  it('registra la derivación con el estudio del catálogo y muestra la comisión que se generará', async () => {
    const user = userEvent.setup()
    let cuerpo: unknown
    servidor.use(
      http.get(`${API}/servicios`, () =>
        HttpResponse.json([
          { id: 'vmx-panoramica', nombre: 'Radiografía panorámica', especialidad: 'Radiología', duracionMin: 15, precio: 60, comisionPorcentaje: 10, activo: true, orden: 1 },
        ]),
      ),
      http.get(`${API}/pacientes`, () =>
        HttpResponse.json([{ id: 'p-1', dni: '11112222', nombres: 'Luciana', apellidos: 'Meza', sedeId: 'sede-chilca-1' }]),
      ),
      http.post(`${API}/radiologia/comisiones`, async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...COMISION_PANORAMICA, id: 'c-nueva' }, { status: 201 })
      }),
    )
    await renderRuta('/app/radiologia')

    await user.click(await screen.findByRole('button', { name: /\+ Registrar Comisión/i }))
    const dialogo = within(await screen.findByRole('dialog'))
    await user.type(dialogo.getByRole('searchbox'), 'Luciana')
    await user.click(await dialogo.findByText(/Luciana Meza/))
    await user.selectOptions(dialogo.getByLabelText(/Estudio radiológico/i), 'vmx-panoramica')

    expect(dialogo.getByText(/comisión 10 % = S\/ 6\.00/)).toBeInTheDocument()
    await user.click(dialogo.getByRole('button', { name: 'Registrar derivación' }))

    await waitFor(() =>
      expect(cuerpo).toMatchObject({
        doctorDerivadorId: DOCTOR_PALOMINO.id,
        pacienteId: 'p-1',
        pacienteNombre: 'Luciana Meza',
        estudioId: 'vmx-panoramica',
      }),
    )
    expect(cuerpo).not.toHaveProperty('montoComision')
  })

  it('la comisión fija por estudio se multiplica por la cantidad y el estudiante no genera comisión', async () => {
    const user = userEvent.setup()
    let cuerpo: unknown
    servidor.use(
      http.get(`${API}/servicios`, () =>
        HttpResponse.json([
          { id: 'vmx-periapical', nombre: 'Radiografía periapical', especialidad: 'Radiología', duracionMin: 10, precio: 15, comisionPorcentaje: 10, comisionFija: 10, activo: true, orden: 1 },
        ]),
      ),
      http.get(`${API}/pacientes`, () =>
        HttpResponse.json([{ id: 'p-1', dni: '11112222', nombres: 'Luciana', apellidos: 'Meza', sedeId: 'sede-chilca-1' }]),
      ),
      http.post(`${API}/radiologia/comisiones`, async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...COMISION_PANORAMICA, id: 'c-nueva' }, { status: 201 })
      }),
    )
    await renderRuta('/app/radiologia')

    await user.click(await screen.findByRole('button', { name: /\+ Registrar Comisión/i }))
    const dialogo = within(await screen.findByRole('dialog'))
    await user.type(dialogo.getByRole('searchbox'), 'Luciana')
    await user.click(await dialogo.findByText(/Luciana Meza/))
    await user.selectOptions(dialogo.getByLabelText(/Estudio radiológico/i), 'vmx-periapical')
    await user.clear(dialogo.getByLabelText(/Cantidad de estudios/i))
    await user.type(dialogo.getByLabelText(/Cantidad de estudios/i), '10')

    expect(dialogo.getByText(/comisión S\/ 10\.00 × 10 = S\/ 100\.00/)).toBeInTheDocument()

    await user.click(dialogo.getByLabelText(/Paciente estudiante/i))
    expect(dialogo.getByText(/estudiante con convenio: sin comisión/)).toBeInTheDocument()
    await user.type(dialogo.getByLabelText(/Precio convenido/i), '90')
    await user.click(dialogo.getByRole('button', { name: 'Registrar derivación' }))

    await waitFor(() =>
      expect(cuerpo).toMatchObject({ estudioId: 'vmx-periapical', cantidad: 10, esEstudiante: true, precioCobrado: 90 }),
    )
  })

  it('liquidar ofrece solo Efectivo, Yape y Plin, los métodos de la caja', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/radiologia')

    await user.click((await screen.findAllByRole('button', { name: /Liquidar/i }))[0])
    const metodo = within(await screen.findByRole('dialog')).getByLabelText(/Método de Pago/i)

    expect(within(metodo).getAllByRole('option').map((o) => o.getAttribute('value'))).toEqual(['YAPE', 'PLIN', 'EFECTIVO'])
  })

  it('liquida juntas varias comisiones del mismo doctor y manda los ids elegidos', async () => {
    const user = userEvent.setup()
    const otra = { ...COMISION_PANORAMICA, id: 'c0000003-0000-4000-8000-000000000003', pacienteNombre: 'Renzo Huaylinos Ramos', montoComision: 5 }
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.get(`${API}/radiologia/comisiones`, () => HttpResponse.json([COMISION_PANORAMICA, otra, COMISION_CONEBEAM])),
      http.post(`${API}/radiologia/comisiones/liquidar-lote`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ loteId: 'l-1', cantidad: 2, totalPagado: 15 })
      }),
    )
    await renderRuta('/app/radiologia')

    await user.click(await screen.findByRole('checkbox', { name: /Luciana Meza/ }))
    await user.click(screen.getByRole('checkbox', { name: /Renzo Huaylinos/ }))
    expect(screen.getByText(/2 comisiones elegidas/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Liquidar elegidas' }))
    const dialogo = await screen.findByRole('dialog')
    await user.click(within(dialogo).getByRole('button', { name: /^Liquidar S\/ 15\.00/ }))

    await waitFor(() => expect(cuerpo).not.toBeNull())
    expect(cuerpo).toMatchObject({ comisionIds: [COMISION_PANORAMICA.id, otra.id], metodoPago: 'YAPE' })
  })

  it('no deja liquidar juntas las comisiones de doctores distintos', async () => {
    const user = userEvent.setup()
    await renderRuta('/app/radiologia')

    await user.click(await screen.findByRole('checkbox', { name: /Luciana Meza/ }))
    await user.click(screen.getByRole('checkbox', { name: /Gonzalo Astuvilca/ }))
    await user.click(screen.getByRole('button', { name: 'Liquidar elegidas' }))

    const dialogo = await screen.findByRole('dialog')
    expect(within(dialogo).getByText(/doctores distintos/)).toBeInTheDocument()
    expect(within(dialogo).getByRole('button', { name: /^Liquidar S\// })).toBeDisabled()
  })

  it('avisa de las comisiones que no se pudieron generar y permite reintentarlas', async () => {
    const user = userEvent.setup()
    let reintentos = 0
    servidor.use(
      http.get(`${API}/radiologia/comisiones/fallidas`, () =>
        HttpResponse.json([{ citaId: 'cita-9', pacienteNombre: 'Mateo Ramos', estudio: 'Radiografía panorámica', motivo: 'El doctor derivador está inactivo', intentos: 2 }]),
      ),
      http.post(`${API}/citas/cita-9/reintentar-comision`, () => {
        reintentos += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await renderRuta('/app/radiologia')

    expect(await screen.findByText(/1 comisión no se pudo generar al cobrar/)).toBeInTheDocument()
    expect(screen.getByText(/2 intentos/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    await waitFor(() => expect(reintentos).toBe(1))
  })

  it('el doctor no ve la selección para liquidar ni el aviso de comisiones fallidas', async () => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 't-doctor',
      usuario: { nombre: 'Doctor Villamax', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' }] },
      sedeActivaId: 'sede-villamax-chilca',
      negocioActivo: 'VILLAMAX',
    })
    await renderRuta('/app/radiologia')

    await screen.findByRole('heading', { name: /Villamax · Centro Radiológico/i })
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.queryByText(/no se pudo generar al cobrar/)).not.toBeInTheDocument()
  })
})
