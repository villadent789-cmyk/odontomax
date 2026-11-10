import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { PacienteDTO } from './api-pacientes'

const API = 'http://localhost:3000/api/v1'

const PACIENTE_FICHA: PacienteDTO = {
  id: 'p0000000-0000-4000-8000-000000000001',
  dni: '45908123',
  nombres: 'Luciana',
  apellidos: 'Meza Cárdenas',
  nombreCompleto: 'Luciana Meza Cárdenas',
  fechaNacimiento: '1995-10-14',
  edad: 29,
  telefono: '964128340',
  email: 'luciana.meza@gmail.com',
  sedeId: 'sede-chilca',
  genero: 'FEMENINO',
  fuenteCaptacion: 'TIKTOK',
  numeroHistoriaClinica: 'HC-2024-089',
  saldoPendiente: 140.0,
  tieneDeuda: true,
  totalInasistencias: 2,
  avisoInasistencias: true,
  esCumpleaneroMes: true,
  consentimientoPromociones: true,
  consentimientoPromocionesFecha: '2026-01-15T10:00:00Z',
  alergias: ['Penicilina', 'Látex'],
}

const CITA_PACIENTE = {
  id: 'c-prox',
  pacienteId: PACIENTE_FICHA.id,
  doctorNombre: 'Dra. Elena Paucar',
  motivoConsulta: 'Evaluación Integral',
  fecha: '2026-10-20',
  hora: '09:30:00',
  sillonNumero: 1,
  sedeId: 'sede-chilca',
  estado: 'CONFIRMADA',
  adelantoRequerido: true,
  adelantoMonto: 30,
  adelantoEstado: 'PAGADO',
  adelantoMedioPago: 'YAPE',
}

const PLAN_ACTIVO = {
  id: 't-orto',
  pacienteId: PACIENTE_FICHA.id,
  nombre: 'Ortodoncia Correctiva',
  estado: 'EN_PROCESO',
  aceptadoPorPaciente: true,
  modalidadPago: 'CUOTAS',
  totalSesiones: 5,
  sesionesRealizadas: 2,
  costoTotal: 1500,
  montoPagado: 600,
  pagadoAntesSistema: 300,
  saldoPendiente: 600,
  especialistaNombre: 'Dra. Elena Paucar',
  items: [
    { id: 'i1', nombreProcedimiento: 'Colocación de brackets', piezaFdi: 11, estado: 'PENDIENTE' },
    { id: 'i2', nombreProcedimiento: 'Profilaxis', estado: 'REALIZADO' },
  ],
}

function mockApi() {
  servidor.use(
    http.get(`${API}/sedes`, () => {
      return HttpResponse.json([
        { id: 'sede-chilca', nombre: 'Villadent Chilca', activa: true, unidadDeNegocio: 'VILLADENT' },
        { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', activa: true, unidadDeNegocio: 'VILLAMAX' },
      ])
    }),
    http.get(`${API}/pacientes/:id`, ({ params }) => {
      if (params.id === PACIENTE_FICHA.id) {
        return HttpResponse.json(PACIENTE_FICHA)
      }
      return new HttpResponse(null, { status: 404 })
    }),
    http.get(`${API}/pacientes`, () => {
      return HttpResponse.json([PACIENTE_FICHA])
    }),
    http.get(`${API}/evoluciones/pacientes/:id`, () => {
      return HttpResponse.json([
        {
          id: 'evo-1',
          pacienteId: PACIENTE_FICHA.id,
          autorId: 'u-doc',
          autorNombre: 'Dra. Elena Paucar',
          autorRol: 'ODONTOLOGO',
          tipoNota: 'SOAP',
          subjetivo: 'Paciente acude con dolor en pieza 16',
          objetivo: 'Caries oclusal profunda sin compromiso pulpar',
          analisis: 'Caries dentinaria media en pieza 16',
          plan: 'Restauración con resina compuesta Filtek Z350',
          firmado: true,
          creadoEn: '2026-02-10T11:00:00Z',
        },
      ])
    }),
    http.get(`${API}/odontograma/pacientes/:id`, () => {
      return HttpResponse.json([
        {
          id: 'odo-1',
          pacienteId: PACIENTE_FICHA.id,
          piezaFdi: 16,
          estado: 'caries',
          caras: ['O'],
          observacion: 'Caries por tratar',
          registradoEn: '2026-02-10T11:00:00Z',
        },
      ])
    }),
    http.get(`${API}/radiografias/paciente/:id`, () => {
      return HttpResponse.json([
        {
          id: 'rad-1',
          pacienteId: PACIENTE_FICHA.id,
          tipoEstudio: 'TOMOGRAFIA',
          etapa: 'INICIAL',
          archivoUrl: 'https://storage.villadent.pe/r2/cbct-890.dcm',
          nombreArchivo: 'CBCT_Maxilar_Superior.dcm',
          tamanoBytes: 45000000,
          diagnosticoRadiografico: 'Tomografía CBCT FOV 8x8 sin lesiones quísticas',
          fechaToma: '2026-02-08T15:00:00Z',
        },
      ])
    }),
    http.get(`${API}/consentimientos/pacientes/:id`, () => {
      return HttpResponse.json([
        {
          id: 'con-1',
          pacienteId: PACIENTE_FICHA.id,
          titulo: 'Tratamiento de Datos Personales (Ley 29733)',
          tipo: 'TRATAMIENTO_DATOS',
          nombreFirmante: 'Luciana Meza Cárdenas',
          documentoFirmante: '45908123',
          parentescoFirmante: 'Titular',
          firmadoEn: '2026-01-15T10:00:00Z',
          activo: true,
        },
      ])
    }),
    // Citas del rango: incluye una de otro paciente y una cancelada, que la ficha NO debe mostrar.
    http.get(`${API}/citas`, () =>
      HttpResponse.json([
        { ...CITA_PACIENTE, id: 'c-otra', pacienteId: 'otro-paciente', motivoConsulta: 'Cita de otro paciente' },
        { ...CITA_PACIENTE, id: 'c-cancel', estado: 'CANCELADA', motivoConsulta: 'Cita cancelada' },
        CITA_PACIENTE,
      ]),
    ),
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json([PLAN_ACTIVO])),
    // La ficha también pide los pagos del paciente y registra la apertura de la historia: sin estas respuestas el test
    // dejaba peticiones sin atender que podían retrasar o romper el render bajo carga.
    http.get(`${API}/pagos/paciente/:id`, () => HttpResponse.json([])),
    http.post(`${API}/historia-clinica/pacientes/:id/apertura`, () => HttpResponse.json({})),
    http.get(`${API}/consentimientos/plantillas`, () => {
      return HttpResponse.json([
        {
          id: 'plan-1',
          codigo: 'LPDP-01',
          titulo: 'Tratamiento de Datos Personales',
          tipo: 'TRATAMIENTO_DATOS',
          contenido: 'Conforme a la Ley 29733...',
          version: 1,
          activo: true,
        },
      ])
    })
  )
}

describe('B9 · Ficha Integral del Paciente (Stitch)', () => {
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
    mockApi()
  })

  afterEach(() => {
    limpiarSesion()
  })

  it('renderiza la ficha completa con identidad, badges de alerta y consentimiento Ley 29733', async () => {
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    expect(await screen.findByRole('heading', { name: 'Luciana Meza Cárdenas' })).toBeInTheDocument()
    expect(screen.getByText('HC-2024-089')).toBeInTheDocument()
    expect(screen.getByText(/45908123/)).toBeInTheDocument()
    expect(screen.getByText(/964128340/)).toBeInTheDocument()

    // Badges en la cabecera
    expect(screen.getByText(/2 inasistencias previas/i)).toBeInTheDocument()
    expect(screen.getByText(/Promo Cumpleaños vigente/i)).toBeInTheDocument()
    expect(screen.getByText(/Ley 29733 Aceptado/i)).toBeInTheDocument()

    // Alergias
    expect(screen.getAllByText(/Penicilina/).length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText(/Látex/).length).toBeGreaterThanOrEqual(1)
  })

  it('muestra la próxima cita REAL del paciente (con su adelanto) y el botón de WhatsApp wa.me', async () => {
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    // La próxima cita llega de una consulta propia (rango de 30 días), después del resto de la ficha.
    expect(await screen.findByText('Evaluación Integral', undefined, { timeout: 5_000 })).toBeInTheDocument()
    expect(screen.getByText(/20\/10\/2026 · 09:30/)).toBeInTheDocument()
    expect(screen.getByText(/Adelanto S\/ 30.00 · Pagado \(Yape\)/)).toBeInTheDocument()
    // Solo la cita de este paciente y vigente: ni la de otro paciente ni la cancelada.
    expect(screen.queryByText('Cita de otro paciente')).not.toBeInTheDocument()
    expect(screen.queryByText('Cita cancelada')).not.toBeInTheDocument()
    expect(screen.queryByText(/Limpieza Profunda/)).not.toBeInTheDocument()

    const btnWhatsApp = screen.getByRole('link', { name: /Escribir WhatsApp/i })
    expect(btnWhatsApp).toHaveAttribute('href', expect.stringContaining('https://wa.me/51964128340'))
  })

  it('permite alternar pestañas entre Resumen y Tratamientos', async () => {
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    expect(await screen.findByText(/Tratamiento activo: Ortodoncia Correctiva/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Radiografías y estudios' })).toBeInTheDocument()
    expect(screen.getByText(/Tomografía CBCT FOV 8x8 sin lesiones quísticas/)).toBeInTheDocument()

    // Cambiar a pestaña Tratamientos
    const tabTratamientos = screen.getByRole('tab', { name: /Tratamientos/i })
    await user.click(tabTratamientos)

    expect(await screen.findByRole('heading', { name: /Planes de Tratamiento & Presupuestos/i })).toBeInTheDocument()
  })

  it('permite visualizar Historia Clínica con metodología SOAP y antecedentes médicos', async () => {
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    const tabHistoria = await screen.findByRole('tab', { name: /Historia Clínica/i })
    await user.click(tabHistoria)

    expect(await screen.findByText(/Notas de Evolución Odontológica/i)).toBeInTheDocument()
    expect(screen.getByText(/Dra. Elena Paucar/i)).toBeInTheDocument()
    expect(screen.getByText(/Formato SOAP/i)).toBeInTheDocument()
    expect(screen.getByText(/Paciente acude con dolor en pieza 16/i)).toBeInTheDocument()
    expect(screen.getByText(/Caries oclusal profunda sin compromiso pulpar/i)).toBeInTheDocument()
    expect(screen.getByText(/Restauración con resina compuesta Filtek Z350/i)).toBeInTheDocument()
  })

  it('recepción ve la Historia Clínica pero sin botones para escribir', async () => {
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    await user.click(await screen.findByRole('tab', { name: /Historia Clínica/i }))

    expect(await screen.findByText(/Notas de Evolución Odontológica/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Nueva nota/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Actualizar Antecedentes|Editar antecedentes/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Registrar Pieza/i })).not.toBeInTheDocument()
  })

  it('el odontólogo conserva los botones para escribir la historia', async () => {
    guardarSesion({
      token: 'token-odo',
      usuario: {
        id: 'u-2',
        nombre: 'Dra. Elena Paucar',
        rol: 'ODONTOLOGO',
        sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca',
    })
    _reiniciarCacheSesion()
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    await user.click(await screen.findByRole('tab', { name: /Historia Clínica/i }))

    expect((await screen.findAllByRole('button', { name: /Nueva nota/i })).length).toBeGreaterThan(0)
  })

  it('permite visualizar Odontograma con numeración FDI y hallazgos registrados', async () => {
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    const tabOdontograma = await screen.findByRole('tab', { name: /Odontograma/i })
    await user.click(tabOdontograma)

    expect(await screen.findByText(/Odontograma Clínico Interactivo FDI/i)).toBeInTheDocument()
    expect(screen.getByText(/Adultos \(Permanente\)/i)).toBeInTheDocument()
    expect(screen.getByText(/Pieza 16/i)).toBeInTheDocument()
  })

  it('la pestaña "Estudios y consentimientos" reúne radiografías y consentimientos informados', async () => {
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)

    await user.click(await screen.findByRole('tab', { name: /Estudios y consentimientos/i }))

    // Radiografías
    expect(await screen.findByText(/Estudios Radiológicos & Imagenología Maxilofacial 3D/i)).toBeInTheDocument()
    expect(screen.getByText(/CBCT_Maxilar_Superior.dcm/i)).toBeInTheDocument()
    expect(screen.getByText(/Ver Estudio CBCT 3D/i)).toBeInTheDocument()

    // Consentimientos, en la misma pestaña (sin cambiar de pestaña)
    expect(await screen.findByText(/Consentimientos Informados & Protección de Datos/i)).toBeInTheDocument()
    expect(screen.getByText('Tratamiento de Datos Personales (Ley 29733)')).toBeInTheDocument()
    expect(screen.getByText(/✓ Firmado y Vigente/i)).toBeInTheDocument()
  })

  it('?tab=odontograma abre directamente la pestaña del odontograma', async () => {
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=odontograma`)

    expect(await screen.findByText(/Odontograma Clínico Interactivo FDI/i)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Odontograma/i })).toHaveAttribute('aria-selected', 'true')
  })

  it('?tab=clinica abre la Historia Clínica y un valor desconocido deja el Resumen', async () => {
    const primera = await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=clinica`)
    expect(await screen.findByText(/Notas de Evolución Odontológica/i)).toBeInTheDocument()
    primera.unmount()

    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=inventada`)
    expect(await screen.findByText(/Tratamiento activo: Ortodoncia Correctiva/i)).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Resumen/i })).toHaveAttribute('aria-selected', 'true')
  })

  it('llegando desde una cita (?desde=agenda) ofrece "← Volver a la Agenda"; sin ese parámetro no aparece', async () => {
    const desdeCita = await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=odontograma&desde=agenda`)
    const volver = await screen.findByRole('link', { name: '← Volver a la Agenda' })
    expect(volver).toHaveAttribute('href', '/app/agenda')
    desdeCita.unmount()

    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}`)
    await screen.findByRole('heading', { name: 'Luciana Meza Cárdenas' })
    expect(screen.queryByRole('link', { name: /Volver a la/ })).not.toBeInTheDocument()
  })

  it('"Nueva receta" no se ofrece a recepción (el backend la rechazaría con 403)', async () => {
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=clinica`)
    expect(await screen.findByText(/Notas de Evolución Odontológica/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Nueva receta/ })).not.toBeInTheDocument()
  })

  it('el odontólogo abre "Emitir receta médica" desde la Historia Clínica con los datos del paciente', async () => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 'token-doc',
      usuario: { id: 'u-doc', nombre: 'Dra. Elena Paucar', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
      sedeActivaId: 'sede-chilca',
    })
    servidor.use(http.get(`${API}/especialistas`, () => HttpResponse.json([])))
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=clinica`)

    await userEvent.click(await screen.findByRole('button', { name: /Nueva receta/ }))
    const dialogo = await screen.findByRole('dialog', { name: 'Emitir receta médica' })
    expect(dialogo).toHaveTextContent('Luciana Meza Cárdenas')
    expect(dialogo).toHaveTextContent('DNI 45908123')
    expect(dialogo).toHaveTextContent('Villadent Chilca')
  })

  it('desde la bandeja del día el enlace de regreso apunta a la bandeja', async () => {
    await renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?desde=bandeja`)
    const volver = await screen.findByRole('link', { name: '← Volver a la Bandeja del día' })
    expect(volver).toHaveAttribute('href', '/app/bandeja')
  })
})

describe('Módulo 5 · Avance de sesiones del tratamiento', { timeout: 15_000 }, () => {
  type Plan = Record<string, unknown>
  const plan = (extra: Plan = {}): Plan => ({
    id: 't-1',
    pacienteId: PACIENTE_FICHA.id,
    nombre: 'Ortodoncia Correctiva',
    estado: 'EN_PROCESO',
    aceptadoPorPaciente: true,
    modalidadPago: 'CUOTAS',
    totalSesiones: 3,
    sesionesRealizadas: 1,
    costoTotal: 900,
    descuentoTotal: 0,
    montoPagado: 900,
    saldoPendiente: 0,
    items: [],
    ...extra,
  })

  let planes: Plan[] = []
  let parches: Record<string, unknown>[] = []

  /** Simula al backend: el PATCH actualiza el plan y la lista se vuelve a pedir ya actualizada. */
  function entrarComo(rol: 'ODONTOLOGO' | 'RECEPCION', inicial: Plan[], opciones: { fallaPatch?: boolean } = {}) {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 't',
      usuario: { id: 'u-1', nombre: 'Dra. Elena Paucar', rol, sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
      sedeActivaId: 'sede-chilca',
    })
    planes = inicial
    parches = []
    servidor.use(
      http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json(planes)),
      http.patch(`${API}/tratamientos/:id`, async ({ request, params }) => {
        const cuerpo = (await request.json()) as Record<string, unknown>
        parches.push(cuerpo)
        if (opciones.fallaPatch) return new HttpResponse(JSON.stringify({ message: 'Sin permiso sobre esta sede' }), { status: 400, headers: { 'Content-Type': 'application/json' } })
        planes = planes.map((p) => (p.id === params.id ? { ...p, ...cuerpo } : p))
        return HttpResponse.json(planes.find((p) => p.id === params.id))
      }),
    )
  }

  beforeEach(() => mockApi())
  afterEach(() => limpiarSesion())

  const abrir = () => renderRuta(`/app/pacientes/${PACIENTE_FICHA.id}?tab=tratamientos`)

  it('"+1 Sesión realizada" suma una sesión, mantiene EN_PROCESO y avisa con el avance', async () => {
    entrarComo('ODONTOLOGO', [plan()])
    await abrir()
    expect(await screen.findByRole('heading', { name: 'Ortodoncia Correctiva' })).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: '+1 Sesión realizada' }))

    await waitFor(() => expect(parches).toEqual([{ sesionesRealizadas: 2, estado: 'EN_PROCESO' }]))
    expect(await screen.findByText('Sesión registrada: Ortodoncia Correctiva (2/3)')).toBeInTheDocument()
    // Con los datos refrescados sigue ofreciendo la siguiente sesión.
    expect(await screen.findByRole('button', { name: '+1 Sesión realizada' })).toBeInTheDocument()
  })

  it('la última sesión completa el tratamiento y retira el botón', async () => {
    entrarComo('ODONTOLOGO', [plan({ sesionesRealizadas: 2 })])
    await abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+1 Sesión realizada' }))

    await waitFor(() => expect(parches).toEqual([{ sesionesRealizadas: 3, estado: 'COMPLETADO' }]))
    expect(await screen.findByText('Sesión registrada: Ortodoncia Correctiva (3/3)')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: '+1 Sesión realizada' })).not.toBeInTheDocument())
    expect(screen.queryByRole('button', { name: 'Finalizar Tratamiento' })).not.toBeInTheDocument()
  })

  it('con todas las sesiones hechas pero el plan sin cerrar ofrece "Finalizar Tratamiento"', async () => {
    entrarComo('ODONTOLOGO', [plan({ sesionesRealizadas: 3 })])
    await abrir()
    expect(await screen.findByRole('button', { name: 'Finalizar Tratamiento' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+1 Sesión realizada' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Finalizar Tratamiento' }))
    await waitFor(() => expect(parches).toEqual([{ estado: 'COMPLETADO' }]))
    expect(await screen.findByText('Tratamiento finalizado: Ortodoncia Correctiva')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Finalizar Tratamiento' })).not.toBeInTheDocument())
  })

  it('no ofrece acciones en planes planificados, completados o cancelados', async () => {
    entrarComo('ODONTOLOGO', [
      plan({ id: 't-a', nombre: 'Plan planificado', estado: 'PLANIFICADO', sesionesRealizadas: 0 }),
      plan({ id: 't-b', nombre: 'Plan completado', estado: 'COMPLETADO', sesionesRealizadas: 3 }),
      plan({ id: 't-c', nombre: 'Plan cancelado', estado: 'CANCELADO', sesionesRealizadas: 3 }),
    ])
    await abrir()
    await screen.findByRole('heading', { name: 'Plan planificado' })
    expect(screen.queryByRole('button', { name: '+1 Sesión realizada' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finalizar Tratamiento' })).not.toBeInTheDocument()
  })

  it('recepción no ve las acciones clínicas del tratamiento', async () => {
    entrarComo('RECEPCION', [plan(), plan({ id: 't-2', nombre: 'Otro plan', sesionesRealizadas: 3 })])
    await abrir()
    await screen.findByRole('heading', { name: 'Otro plan' })
    expect(screen.queryByRole('button', { name: '+1 Sesión realizada' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finalizar Tratamiento' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cerrar plan' })).not.toBeInTheDocument()
  })

  it('muestra el rótulo "Pagado · sesiones pendientes" cuando saldo es 0 pero faltan sesiones', async () => {
    entrarComo('ODONTOLOGO', [plan({ saldoPendiente: 0, sesionesRealizadas: 1, totalSesiones: 4 })])
    await abrir()
    expect(await screen.findByText('Pagado · sesiones pendientes')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '+1 Sesión realizada' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cerrar plan' })).toBeInTheDocument()
  })

  it('botón "Cerrar plan" permite cerrar como TERMINADO y oculta "+1 Sesión realizada"', async () => {
    entrarComo('ODONTOLOGO', [plan({ estado: 'EN_PROCESO', sesionesRealizadas: 1, totalSesiones: 4 })])
    await abrir()
    expect(await screen.findByRole('button', { name: '+1 Sesión realizada' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cerrar plan' }))

    // Modal con las dos opciones
    expect(await screen.findByText('Cerrar plan de tratamiento')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Terminado/ })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Interrumpido por el paciente/ })).toBeInTheDocument()

    // Confirmar como Terminado
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Cierre' }))
    await waitFor(() => expect(parches).toEqual([{ estado: 'TERMINADO' }]))
    expect(await screen.findByText(/Plan cerrado: Ortodoncia Correctiva \(Terminado\)/)).toBeInTheDocument()

    // En estado final, ya no ofrece "+1 Sesión realizada" ni "Cerrar plan"
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '+1 Sesión realizada' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cerrar plan' })).not.toBeInTheDocument()
    })
  })

  it('botón "Cerrar plan" como INTERRUMPIDO exige motivo y lo envía al backend', async () => {
    entrarComo('ODONTOLOGO', [plan({ estado: 'EN_PROCESO', sesionesRealizadas: 1, totalSesiones: 4 })])
    await abrir()
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar plan' }))

    // Elegir Interrumpido por el paciente
    await userEvent.click(screen.getByRole('radio', { name: /Interrumpido por el paciente/ }))

    // Intentar confirmar sin motivo muestra error de validación
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Cierre' }))
    expect(await screen.findByText('Debe ingresar un motivo para interrumpir el tratamiento')).toBeInTheDocument()
    expect(parches).toHaveLength(0)

    // Ingresar motivo y confirmar
    await userEvent.type(screen.getByPlaceholderText(/Ej. Paciente se muda de ciudad/), 'Paciente viaja fuera del país')
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar Cierre' }))

    await waitFor(() =>
      expect(parches).toEqual([{ estado: 'INTERRUMPIDO', motivoCierre: 'Paciente viaja fuera del país' }]),
    )
    expect(await screen.findByText(/Plan cerrado: Ortodoncia Correctiva \(Interrumpido\)/)).toBeInTheDocument()

    // En estado final, oculta "+1 Sesión realizada"
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '+1 Sesión realizada' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Cerrar plan' })).not.toBeInTheDocument()
    })
  })

  it('si el backend rechaza el cambio muestra el motivo y no avisa de éxito', async () => {
    // Nombre propio: los avisos de sonner viven en un estado global y los de otras pruebas pueden reaparecer.
    entrarComo('ODONTOLOGO', [plan({ nombre: 'Plan con fallo' })], { fallaPatch: true })
    await abrir()
    await userEvent.click(await screen.findByRole('button', { name: '+1 Sesión realizada' }))
    expect(await screen.findByText('No se pudo actualizar el tratamiento')).toBeInTheDocument()
    expect(screen.getByText('Sin permiso sobre esta sede')).toBeInTheDocument()
    expect(screen.queryByText(/Sesión registrada: Plan con fallo/)).not.toBeInTheDocument()
  })

  it('muestra mensaje de Sin permiso cuando el backend devuelve 403', async () => {
    servidor.use(
      http.get(API + '/pacientes/:id', () => {
        return HttpResponse.json(
          { error: 'Acceso denegado: el paciente no está asignado a este odontólogo.' },
          { status: 403 }
        )
      })
    )
    entrarComo('ODONTOLOGO', [])
    await abrir()
    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
    expect(
      screen.getByText(/Acceso denegado: el paciente no está asignado a este odontólogo/)
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Volver al Directorio' })).toHaveAttribute(
      'href',
      '/app/pacientes'
    )
  })
})
