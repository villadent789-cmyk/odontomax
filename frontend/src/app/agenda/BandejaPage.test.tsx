import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion, type Rol } from '@/shared/auth'
import { http } from 'msw'
import { API, HttpResponse, errorBackend, manejadores, servidor } from '@/test/msw'
import { agenda, cita, respuestaCita } from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

const HOY = '2026-10-08'
const MANANA = '2026-10-09'

function sesion(rol: Rol = 'RECEPCION') {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin Mendoza', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

const PROGRAMADA = cita({ id: 'c0000000-0000-4000-8000-0000000000a1', pacienteNombre: 'David Cerrón', hora: '09:30:00' })
const CONFIRMADA = cita({ id: 'c0000000-0000-4000-8000-0000000000a2', pacienteNombre: 'Tania Meza', estado: 'CONFIRMADA', hora: '11:00:00' })
const ATENDIDA = cita({ id: 'c0000000-0000-4000-8000-0000000000a3', pacienteNombre: 'Nelly Vílchez', estado: 'ATENDIDA', hora: '08:00:00' })
const MANANA_PROG = cita({ id: 'c0000000-0000-4000-8000-0000000000b1', pacienteNombre: 'Raúl Córdova', fecha: MANANA })
const WEB = cita({
  id: 'c0000000-0000-4000-8000-0000000000c1',
  pacienteNombre: 'Karina Soto',
  estado: 'PENDIENTE_WEB',
  codigoTicket: 'OM-7K9M2P4X',
  doctorId: undefined,
  doctorNombre: undefined,
  sillonNumero: undefined,
  fecha: MANANA,
})
const EN_SALA = cita({ id: 'c0000000-0000-4000-8000-0000000000d1', pacienteNombre: 'Carlos Huamán', estado: 'EN_SALA' })

let sedesPedidas: (string | null)[] = []

function api({ hoy = [PROGRAMADA, CONFIRMADA, ATENDIDA], manana = [MANANA_PROG], web = [WEB], espera = [EN_SALA], tareas = {} } = {}) {
  sedesPedidas = []
  servidor.use(
    agenda.citas(({ url }) => {
      sedesPedidas.push(url.searchParams.get('sedeId'))
      const fecha = url.searchParams.get('fecha')
      return HttpResponse.json(fecha === HOY ? hoy : fecha === MANANA ? manana : [])
    }),
    agenda.reservasWeb(web),
    agenda.listaEspera(espera),
    agenda.bandejaTareas(tareas),
    // Por defecto la caja está abierta (sin banner); las pruebas del módulo 6 lo sustituyen.
    http.get(`${API}/caja/activa`, () => HttpResponse.json({ id: 'c-1', sedeId: 'sede-chilca', estado: 'ABIERTA' })),
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-08T15:00:00Z'))
  sessionStorage.clear()
  _reiniciarCacheSesion()
  sesion()
})
afterEach(() => {
  vi.useRealTimers()
  limpiarSesion()
})

function fila(nombre: string) {
  return screen.getByRole('heading', { level: 3, name: nombre }).closest('li') as HTMLElement
}

describe('B2 · Bandeja del día', { timeout: 15_000 }, () => {
  it('muestra esqueleto y luego las citas reales de la sede activa con sus indicadores', async () => {
    api()
    await renderRuta('/app/bandeja')
    expect(screen.getByRole('heading', { level: 2, name: 'Operación del día' })).toBeInTheDocument()
    expect(screen.getByText('Cargando las citas de hoy…')).toBeInTheDocument()

    expect(await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })).toBeInTheDocument()
    expect(screen.getByText(/Jueves 08\/10\/2026/)).toBeInTheDocument()
    // Indicadores calculados solo con las citas recibidas.
    const citasHoy = screen.getByRole('region', { name: 'Total de citas hoy' })
    expect(within(citasHoy).getByText('3')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Confirmadas' })).getByText('1')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'En sala de espera' })).getByText('1')).toBeInTheDocument()
    // Secciones: mañana por confirmar, reservas web y sala de espera.
    expect(screen.getByRole('heading', { level: 3, name: 'Raúl Córdova' })).toBeInTheDocument()
    expect(screen.getByText('OM-7K9M2P4X')).toBeInTheDocument()
    expect(within(screen.getByRole('complementary', { name: 'Sala de espera' })).getByText('Carlos Huamán')).toBeInTheDocument()
    expect(within(fila('Tania Meza')).getByText('Confirmada')).toHaveAttribute('data-estado', 'CONFIRMADA')
    expect(sedesPedidas.every((s) => s === 'sede-chilca')).toBe(true)
    // Nada inventado: ni adelantos ni cobros.
    expect(screen.queryByText(/Adelanto/i)).not.toBeInTheDocument()
  })

  it('muestra cumpleaños de mañana sin WhatsApp ni incluirlos en las tareas de hoy', async () => {
    api()
    servidor.use(
      agenda.bandejaTareas({
        tareas: [{
          id: 'CUMPLE-MANANA-paciente-1',
          tipo: 'RECORDATORIO_CUMPLEANOS',
          prioridad: 'NORMAL',
          pacienteId: 'paciente-1',
          pacienteNombre: 'Elena Ramos',
          pacienteDni: '12345678',
          pacienteTelefono: '987654321',
          avisoInasistencias: true,
          enlaceWhatsapp: 'https://wa.me/51987654321',
        }],
      }),
    )
    await renderRuta('/app/bandeja')

    expect(await screen.findByRole('heading', { level: 3, name: 'Cumplen años mañana' })).toBeInTheDocument()
    expect(screen.getByText('Elena Ramos')).toBeInTheDocument()
    expect(screen.getByText('DNI 12345678')).toBeInTheDocument()
    expect(screen.getByText('Teléfono: 987654321')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Enviar saludo WhatsApp/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Promo Activa')).not.toBeInTheDocument()
    expect(screen.queryByText(/Aviso Clínico Crítico/)).not.toBeInTheDocument()
    expect(screen.getAllByText('Elena Ramos')).toHaveLength(1)
  })

  it('no muestra la tarjeta de cumpleaños de mañana cuando no hay recordatorios', async () => {
    api()
    await renderRuta('/app/bandeja')

    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    expect(screen.queryByRole('heading', { level: 3, name: 'Cumplen años mañana' })).not.toBeInTheDocument()
  })

  it('muestra la tarea "Pasar al sillón" y la dispara con POST /citas/{id}/iniciar-atencion', async () => {
    api()
    let id: string | undefined
    servidor.use(
      agenda.bandejaTareas({
        totalPendientes: 1,
        tareas: [{
          id: `SILLON-${EN_SALA.id}`,
          tipo: 'PASAR_AL_SILLON',
          prioridad: 'ALTA',
          citaId: EN_SALA.id,
          pacienteNombre: 'Carlos Huamán',
          llegadaEn: '2026-10-08T14:40:00Z',
        }],
      }),
      agenda.iniciarAtencion(({ id: i }) => {
        id = i
        return respuestaCita({ ...EN_SALA, inicioAtencionEn: '2026-10-08T15:00:00Z' })
      }),
    )
    await renderRuta('/app/bandeja')

    const titulo = await screen.findByRole('heading', { level: 2, name: 'Pasar al sillón' })
    const seccion = within(titulo.closest('section') as HTMLElement)
    expect(seccion.getByText('Carlos Huamán')).toBeInTheDocument()
    expect(seccion.getByText('Espera en sala desde las 09:40')).toBeInTheDocument()

    await userEvent.click(seccion.getByRole('button', { name: 'Pasar al sillón' }))
    await waitFor(() => expect(id).toBe(EN_SALA.id))
  })

  it('no muestra la sección "Pasar al sillón" cuando nadie espera sin iniciar', async () => {
    api()
    await renderRuta('/app/bandeja')

    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    expect(screen.queryByRole('heading', { level: 2, name: 'Pasar al sillón' })).not.toBeInTheDocument()
  })

  it('muestra la espera de quien está en sala y la duración real de quien ya fue atendido', async () => {
    const esperando = cita({
      id: 'c0000000-0000-4000-8000-0000000000e1',
      pacienteNombre: 'Julia Paredes',
      estado: 'EN_SALA',
      llegadaEn: '2026-10-08T14:48:00Z',
    })
    const terminada = cita({
      id: 'c0000000-0000-4000-8000-0000000000e2',
      pacienteNombre: 'Martín Rojas',
      estado: 'ATENDIDA',
      hora: '08:00:00',
      duracionMinutos: 30,
      llegadaEn: '2026-10-08T13:05:00Z',
      inicioAtencionEn: '2026-10-08T13:10:00Z',
      finAtencionEn: '2026-10-08T13:48:00Z',
    })
    api({ hoy: [esperando, terminada], espera: [esperando] })
    await renderRuta('/app/bandeja')

    expect(await screen.findAllByText('Espera 12 min')).not.toHaveLength(0)
    expect(within(fila('Martín Rojas')).getByText('Atendida en 38 min · prevista 30')).toBeInTheDocument()
  })

  it('solo ofrece las transiciones válidas según el estado', async () => {
    api()
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })

    const programada = within(fila('David Cerrón'))
    expect(programada.getByRole('button', { name: 'Confirmar' })).toBeInTheDocument()
    expect(programada.getByRole('button', { name: 'Marcar llegada' })).toBeInTheDocument()
    expect(programada.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument()
    expect(programada.queryByRole('button', { name: 'Marcar atendida' })).not.toBeInTheDocument()

    const confirmada = within(fila('Tania Meza'))
    expect(confirmada.queryByRole('button', { name: 'Confirmar' })).not.toBeInTheDocument()
    expect(confirmada.getByRole('button', { name: 'No asistió' })).toBeInTheDocument()

    // ATENDIDA: sin acciones.
    expect(within(fila('Nelly Vílchez')).queryByRole('group')).not.toBeInTheDocument()

    // Mañana: solo confirmar (no "llegó" ni "no asistió").
    const manana = within(fila('Raúl Córdova'))
    expect(manana.getByRole('button', { name: 'Confirmar' })).toBeInTheDocument()
    expect(manana.queryByRole('button', { name: 'Marcar llegada' })).not.toBeInTheDocument()

    // Reserva web: aceptar o confirmar.
    expect(within(fila('Karina Soto')).getByRole('button', { name: 'Aceptar reserva' })).toBeInTheDocument()
  })

  it('cambia el estado con PATCH /citas/{id}/estado { estado } y recarga', async () => {
    api()
    let cuerpo: unknown
    let id: string | undefined
    servidor.use(
      agenda.cambiarEstado(({ cuerpo: c, id: i }) => {
        cuerpo = c
        id = i
        return respuestaCita({ ...PROGRAMADA, estado: 'EN_SALA' })
      }),
    )
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    await userEvent.click(within(fila('David Cerrón')).getByRole('button', { name: 'Marcar llegada' }))
    await waitFor(() => expect(cuerpo).toEqual({ estado: 'EN_SALA' }))
    expect(id).toBe(PROGRAMADA.id)
  })

  it('muestra el mensaje del backend si el cambio de estado falla', async () => {
    api()
    servidor.use(agenda.cambiarEstado(() => errorBackend(400, 'Cita no encontrada con ID: x')))
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    await userEvent.click(within(fila('David Cerrón')).getByRole('button', { name: 'Confirmar' }))
    expect(await screen.findByText('Cita no encontrada con ID: x')).toBeInTheDocument()
  })

  it('cancelar pide un motivo de 5+ caracteres y envía PATCH /citas/{id}/cancelar { motivo }', async () => {
    api()
    let cuerpo: unknown
    servidor.use(
      agenda.cancelar(({ cuerpo: c }) => {
        cuerpo = c
        return respuestaCita({ ...PROGRAMADA, estado: 'CANCELADA' })
      }),
    )
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    await userEvent.click(within(fila('David Cerrón')).getByRole('button', { name: 'Cancelar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Cancelar cita' })
    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'Viaje')
    await userEvent.clear(within(dialogo).getByLabelText('Motivo de la cancelación'))
    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'No')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar cita' }))
    expect(await within(dialogo).findByText(/mínimo 5 caracteres/)).toBeInTheDocument()
    expect(cuerpo).toBeUndefined()

    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), ' puede venir')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar cita' }))
    await waitFor(() => expect(cuerpo).toEqual({ motivo: 'No puede venir' }))
    // Tras cancelar queda abierta la pantalla de cupo liberado hasta cerrarla.
    expect(await screen.findByRole('dialog', { name: /Horario Liberado/ })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('"Ver detalle" abre el panel B4 de la cita', async () => {
    api()
    servidor.use(manejadores.sedes(), agenda.paciente())
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    await userEvent.click(screen.getByRole('button', { name: 'Ver detalle de la cita de David Cerrón' }))
    const panel = await screen.findByRole('dialog', { name: 'David Cerrón' })
    expect(await within(panel).findByText('42891023')).toBeInTheDocument()
  })

  it('estados vacíos de cada sección', async () => {
    api({ hoy: [], manana: [], web: [], espera: [] })
    await renderRuta('/app/bandeja')
    expect(await screen.findByText('No hay citas para hoy')).toBeInTheDocument()
    expect(screen.getByText('Nada por confirmar')).toBeInTheDocument()
    expect(screen.getByText('No hay reservas web pendientes')).toBeInTheDocument()
    expect(screen.getByText('Sala vacía')).toBeInTheDocument()
  })

  it('error al cargar: mensaje y reintentar', async () => {
    api()
    let fallar = true
    servidor.use(
      agenda.citas(({ url }) =>
        fallar ? errorBackend(500, 'Error interno') : HttpResponse.json(url.searchParams.get('fecha') === HOY ? [PROGRAMADA] : []),
      ),
    )
    await renderRuta('/app/bandeja')
    const region = screen.getByRole('region', { name: 'Citas de hoy' })
    expect(await within(region).findByRole('alert')).toHaveTextContent('Error interno')
    fallar = false
    await userEvent.click(within(region).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })).toBeInTheDocument()
  })

  it('ODONTOLOGO no tiene bandeja (Sin permiso)', async () => {
    sesion('ODONTOLOGO')
    await renderRuta('/app/bandeja')
    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
  })
})

describe('Módulo 6 · Banner preventivo de caja cerrada', { timeout: 15_000 }, () => {
  const TEXTO_PRINCIPAL = 'La caja de esta sede no está abierta hoy.'
  const TEXTO_DETALLE = 'Para poder registrar cobros o anticipos de los pacientes, abra el turno de caja.'

  const cajaActiva = (respuesta: () => Response) => http.get(`${API}/caja/activa`, respuesta)

  it('sin turno abierto avisa arriba de la bandeja y enlaza a la caja', async () => {
    api()
    servidor.use(cajaActiva(() => new HttpResponse(null, { status: 204 })))
    await renderRuta('/app/bandeja')

    expect(await screen.findByText(TEXTO_PRINCIPAL)).toBeInTheDocument()
    expect(screen.getByText(TEXTO_DETALLE)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ir a Caja' })).toHaveAttribute('href', '/app/caja')
    // El resto de la bandeja sigue funcionando.
    expect(await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })).toBeInTheDocument()
  })

  it('un turno cerrado (no ABIERTA) también cuenta como caja no abierta', async () => {
    api()
    servidor.use(cajaActiva(() => HttpResponse.json({ id: 'c-1', sedeId: 'sede-chilca', estado: 'CERRADA' })))
    await renderRuta('/app/bandeja')
    expect(await screen.findByText(TEXTO_PRINCIPAL)).toBeInTheDocument()
  })

  it('con el turno abierto no muestra el aviso', async () => {
    api()
    servidor.use(cajaActiva(() => HttpResponse.json({ id: 'c-1', sedeId: 'sede-chilca', estado: 'ABIERTA' })))
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    await waitFor(() => expect(screen.queryByText(TEXTO_PRINCIPAL)).not.toBeInTheDocument())
  })

  it('si la consulta de caja falla no muestra un aviso falso', async () => {
    api()
    servidor.use(cajaActiva(() => errorBackend(500, 'Fallo temporal del servidor')))
    await renderRuta('/app/bandeja')
    await screen.findByRole('heading', { level: 3, name: 'David Cerrón' })
    expect(screen.queryByText(TEXTO_PRINCIPAL)).not.toBeInTheDocument()
  })

  it('consulta la caja de la sede activa', async () => {
    api()
    const sedes: (string | null)[] = []
    servidor.use(
      http.get(`${API}/caja/activa`, ({ request }) => {
        sedes.push(new URL(request.url).searchParams.get('sedeId'))
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await renderRuta('/app/bandeja')
    await screen.findByText(TEXTO_PRINCIPAL)
    expect(sedes.length).toBeGreaterThan(0)
    expect(sedes.every((s) => s === 'sede-chilca')).toBe(true)
  })

  it('al abrir el WhatsApp de un recordatorio se registra el mensaje como enviado', async () => {
    const cuerpos: { medio?: string; resultado?: string }[] = []
    api({
      tareas: {
        tareas: [{ id: 'REC24H-1', tipo: 'RECORDATORIO_24H', citaId: MANANA_PROG.id, pacienteNombre: 'Raúl Córdova', horaCita: '10:00:00', enlaceWhatsapp: '#wa', estadoConfirmacion: 'SIN_CONTACTAR' }],
      },
    })
    servidor.use(agenda.registrarContacto(({ cuerpo }) => { cuerpos.push(cuerpo); return HttpResponse.json({}) }))
    await renderRuta('/app/bandeja')
    const enlace = await screen.findByRole('link', { name: 'Enviar WhatsApp' })
    enlace.addEventListener('click', (e) => e.preventDefault())
    await userEvent.click(enlace)
    await waitFor(() => expect(cuerpos).toEqual([expect.objectContaining({ medio: 'WHATSAPP', resultado: 'MENSAJE_ENVIADO' })]))
  })

  it('«Pidió reprogramar» y «Canceló» registran el resultado y abren de una vez el diálogo para hacerlo', async () => {
    const cuerpos: { resultado?: string }[] = []
    api({
      tareas: {
        tareas: [{ id: 'REC24H-1', tipo: 'RECORDATORIO_24H', citaId: MANANA_PROG.id, pacienteNombre: 'Raúl Córdova', horaCita: '10:00:00', enlaceWhatsapp: '#wa', estadoConfirmacion: 'SIN_CONTACTAR' }],
      },
    })
    servidor.use(agenda.registrarContacto(({ cuerpo }) => { cuerpos.push(cuerpo); return HttpResponse.json({}) }))
    const user = userEvent.setup()
    await renderRuta('/app/bandeja')

    await user.click(await screen.findByRole('button', { name: 'Pidió reprogramar' }))
    expect(await screen.findByRole('dialog', { name: 'Reprogramar cita' })).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Reprogramar cita' })).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: 'Canceló' }))
    expect(await screen.findByRole('dialog', { name: /Cancelar cita/ })).toBeInTheDocument()
    expect(cuerpos.map((c) => c.resultado)).toEqual(['PIDIO_REPROGRAMAR', 'CANCELO'])
  })
})
