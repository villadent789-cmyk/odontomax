import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { API, HttpResponse, SEDES, errorBackend, http, manejadores, servidor } from '@/test/msw'
import {
  ID_PACIENTE,
  ID_PAUCAR,
  PACIENTES,
  ESPECIALISTAS_SEDE,
  agenda,
  cita,
  disponibilidad,
  respuestaCita,
  respuestaPaciente,
} from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

const MANANA = '2026-10-09'

function sesion() {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin Mendoza', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

const CITA = cita({ fecha: '2026-10-08', hora: '11:00:00' })

function api({ sedes = SEDES, citas = [CITA] } = {}) {
  servidor.use(
    manejadores.sedes(sedes),
    agenda.especialistas(),
    agenda.horarios(),
    agenda.listaEspera([]),
    agenda.reservasWeb([]),
    agenda.citas(citas),
    agenda.pacientes(),
    agenda.paciente(),
    agenda.disponibilidad(({ url, id }) =>
      HttpResponse.json(
        id === ID_PAUCAR
          ? disponibilidad({ fecha: url.searchParams.get('fecha')! })
          : disponibilidad({ atiendeEseDia: false, slots: [], mensaje: 'El especialista no tiene turnos programados para el día seleccionado.' }),
      ),
    ),
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

async function abrirNueva() {
  await renderRuta('/app/agenda')
  await screen.findByRole('table')
  await userEvent.click(screen.getByRole('button', { name: 'Nueva cita' }))
  return screen.findByRole('dialog', { name: 'Nueva cita' })
}

async function elegirPaciente(d: HTMLElement) {
  await userEvent.type(within(d).getByLabelText('Paciente'), 'Beat')
  await userEvent.click(await within(d).findByRole('button', { name: /Beatriz Tovar Lazo/ }))
}

async function cambiarFecha(d: HTMLElement, etiqueta: string, fecha: string) {
  const campo = within(d).getByLabelText(etiqueta)
  await userEvent.clear(campo)
  await userEvent.type(campo, fecha)
}

describe('B5 · Crear cita', { timeout: 20_000 }, () => {
  it('valida los obligatorios del DTO (paciente, especialista, horario y motivo)', async () => {
    api()
    const d = await abrirNueva()
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))
    expect(await within(d).findByText('Busca y selecciona un paciente, o regístralo.')).toBeInTheDocument()
    expect(within(d).getByText('Elige el especialista.')).toBeInTheDocument()
    expect(within(d).getByText('Elige el horario de la cita.')).toBeInTheDocument()
    expect(within(d).getByText('Indica el motivo o procedimiento.')).toBeInTheDocument()
  })

  it('crea la cita con POST /citas y el sillón fijo del doctor', async () => {
    api()
    let cuerpo: unknown
    servidor.use(
      agenda.crearCita(({ cuerpo: c }) => {
        cuerpo = c
        return respuestaCita(cita({ id: 'c0000000-0000-4000-8000-0000000000f1', fecha: MANANA, hora: '09:00:00' }), 201)
      }),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    expect(within(d).getByText('Beatriz Tovar Lazo')).toBeInTheDocument()
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    await cambiarFecha(d, 'Fecha', MANANA)

    const huecos = await within(d).findByRole('group', { name: 'Horario de la cita' })
    // 08:30 está ocupado según la disponibilidad.
    expect(within(huecos).getAllByRole('radio').map((r) => (r as HTMLInputElement).value)).toEqual(['08:00', '09:00', '09:30'])
    // 60 min: se necesitan dos franjas libres seguidas → solo 09:00.
    await userEvent.selectOptions(within(d).getByLabelText('Duración'), '60')
    const huecos60 = await within(d).findByRole('group', { name: 'Horario de la cita' })
    expect(within(huecos60).getAllByRole('radio').map((r) => (r as HTMLInputElement).value)).toEqual(['09:00'])
    await userEvent.click(within(huecos60).getByLabelText('09:00'))

    expect(within(d).getByText('Sillón 1 · Dra. Elena Paucar')).toBeInTheDocument()
    expect(within(d).queryByLabelText('Sillón')).not.toBeInTheDocument()
    // Procedimiento del catálogo de servicios activos (GET /servicios).
    await userEvent.selectOptions(within(d).getByLabelText('Procedimiento'), 'Evaluación & Diagnóstico')
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() =>
      expect(cuerpo).toEqual({
        pacienteId: ID_PACIENTE,
        pacienteNombre: 'Beatriz Tovar Lazo',
        pacienteTelefono: '964123456',
        doctorId: ID_PAUCAR,
        doctorNombre: 'Dra. Elena Paucar',
        sedeId: 'sede-chilca',
        fecha: MANANA,
        hora: '09:00',
        duracionMinutos: 60,
        sillonNumero: 1,
        motivoConsulta: 'Evaluación & Diagnóstico',
      }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Nueva cita' })).not.toBeInTheDocument())
  })

  it('muestra el sillón fijo del especialista sin un selector editable', async () => {
    api()
    const d = await abrirNueva()

    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)

    expect(await within(d).findByText('Sillón 1 · Dra. Elena Paucar')).toBeInTheDocument()
    expect(within(d).queryByLabelText('Sillón')).not.toBeInTheDocument()
  })

  it('bloquea guardar y avisa si el especialista no tiene sillón fijo', async () => {
    api()
    servidor.use(
      agenda.especialistas([
        { ...ESPECIALISTAS_SEDE[0], sillonesPorSede: undefined },
        ESPECIALISTAS_SEDE[1],
      ]),
    )
    const d = await abrirNueva()

    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)

    expect(await within(d).findByText('Asígnalo en Administración para poder agendar.')).toBeInTheDocument()
    expect(within(d).getByRole('button', { name: 'Agendar cita' })).toBeDisabled()
  })

  it('muestra el 409 del backend (colisión) y pide elegir otro hueco', async () => {
    api()
    servidor.use(agenda.crearCita(() => errorBackend(409, 'El doctor ya tiene una cita agendada en esa fecha y hora.')))
    const d = await abrirNueva()
    await elegirPaciente(d)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    await cambiarFecha(d, 'Fecha', MANANA)
    await userEvent.click(await within(d).findByLabelText('09:30'))
    await userEvent.selectOptions(within(d).getByLabelText('Procedimiento'), 'Otro motivo (escribir)')
    await userEvent.type(within(d).getByLabelText('Motivo o procedimiento'), 'Control')
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))
    expect(await within(d).findByText('El doctor ya tiene una cita agendada en esa fecha y hora.')).toBeInTheDocument()
    expect(await within(d).findByRole('radio', { name: '09:30' })).not.toBeChecked()
  })

  it('especialista que no atiende ese día: muestra el mensaje de la disponibilidad', async () => {
    api()
    const d = await abrirNueva()
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), '7b1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f')
    expect(await within(d).findByText('El especialista no tiene turnos programados para el día seleccionado.')).toBeInTheDocument()
  })

  it('al elegir especialista muestra sus días y horarios de atención en la sede', async () => {
    api()
    const d = await abrirNueva()
    expect(within(d).queryByTestId('atencion-especialista')).not.toBeInTheDocument()
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    expect(await within(d).findByText('Atiende en Sede Chilca: Lunes a Sábado de 08:00 a 13:00')).toBeInTheDocument()
    expect(within(d).getByText('Horario de la cita')).toBeInTheDocument()
    expect(within(d).queryByText(/Hueco libre/i)).not.toBeInTheDocument()
  })

  it('si la sede no figura en el horario del especialista lo dice', async () => {
    api()
    servidor.use(agenda.horarios([{ id: 'h1', diaSemana: 1, horaInicio: '09:00:00', horaFin: '13:00:00', sedeId: 'sede-centro' }]))
    const d = await abrirNueva()
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    expect(await within(d).findByText('El especialista no tiene horario de atención en esta sede.')).toBeInTheDocument()
  })

  it('un día sin turnos del especialista avisa cuál es el día y cuáles son los días de atención', async () => {
    api()
    servidor.use(agenda.horarios([1, 3].map((dia) => ({ id: `h${dia}`, diaSemana: dia, horaInicio: '09:00:00', horaFin: '13:00:00', sedeId: 'sede-chilca' }))))
    const d = await abrirNueva()
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), '7b1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f')
    // 2026-10-09 es viernes: el especialista solo atiende lunes y miércoles.
    await cambiarFecha(d, 'Fecha', MANANA)
    expect(
      await within(d).findByText('El especialista no atiende los viernes en esta sede. Días de atención: Lunes y Miércoles.'),
    ).toBeInTheDocument()
  })

  it('con un tratamiento en proceso muestra la insignia con la sesión pendiente y propone el motivo', async () => {
    api()
    servidor.use(
      http.get(`${API}/tratamientos/paciente/:id`, () =>
        HttpResponse.json([
          { id: 't-orto', pacienteId: ID_PACIENTE, nombre: 'Ortodoncia Correctiva', estado: 'EN_PROCESO', totalSesiones: 5, sesionesRealizadas: 2, saldoPendiente: 900 },
          { id: 't-viejo', pacienteId: ID_PACIENTE, nombre: 'Limpieza', estado: 'COMPLETADO', totalSesiones: 1, sesionesRealizadas: 1 },
        ]),
      ),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    expect(await within(d).findByText(/Ortodoncia Correctiva \(Sesión 3\/5\).*falta S\/\s*900/)).toBeInTheDocument()
    expect(within(d).getByRole('checkbox', { name: /Esta cita es la sesión pendiente/ })).toBeChecked()
  })

  async function agendarConPlan(desmarcar: boolean) {
    api()
    let cuerpo: Record<string, unknown> | undefined
    servidor.use(
      http.get(`${API}/tratamientos/paciente/:id`, () =>
        HttpResponse.json([{ id: 't-orto', pacienteId: ID_PACIENTE, nombre: 'Ortodoncia Correctiva', estado: 'EN_PROCESO', totalSesiones: 5, sesionesRealizadas: 2 }]),
      ),
      agenda.crearCita(({ cuerpo: c }) => {
        cuerpo = c as Record<string, unknown>
        return respuestaCita(cita({ id: 'c0000000-0000-4000-8000-0000000000f2', fecha: MANANA, hora: '09:00:00' }), 201)
      }),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    const casilla = await within(d).findByRole('checkbox', { name: /Esta cita es la sesión pendiente/ })
    expect(casilla).toBeChecked()
    if (desmarcar) await userEvent.click(casilla)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    await cambiarFecha(d, 'Fecha', MANANA)
    await userEvent.click(within(await within(d).findByRole('group', { name: 'Horario de la cita' })).getByLabelText('09:00'))
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))
    await waitFor(() => expect(cuerpo).toBeDefined())
    return cuerpo!
  }

  it('con un tratamiento en proceso la cita se envía enlazada a ese plan (tratamientoId)', async () => {
    const cuerpo = await agendarConPlan(false)
    expect(cuerpo).toMatchObject({ pacienteId: ID_PACIENTE, tratamientoId: 't-orto', motivoConsulta: 'Ortodoncia Correctiva · sesión 3/5' })
  })

  it('si se desmarca la casilla la cita no se enlaza al plan', async () => {
    const cuerpo = await agendarConPlan(true)
    expect(cuerpo).not.toHaveProperty('tratamientoId')
  })

  it('sin tratamiento en proceso no hay insignia ni se toca el motivo', async () => {
    api()
    servidor.use(
      http.get(`${API}/tratamientos/paciente/:id`, () =>
        HttpResponse.json([{ id: 't1', pacienteId: ID_PACIENTE, nombre: 'Limpieza', estado: 'COMPLETADO', totalSesiones: 1, sesionesRealizadas: 1 }]),
      ),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    await waitFor(() => expect(within(d).getByText('Beatriz Tovar Lazo')).toBeInTheDocument())
    expect(within(d).queryByText(/Tratamiento activo/)).not.toBeInTheDocument()
    expect(within(d).getByLabelText('Procedimiento')).toHaveValue('')
  })

  it('al activar el presupuesto crea el plan antes de la cita y la enlaza', async () => {
    api()
    const orden: string[] = []
    let cuerpoPlan: unknown
    let cuerpoCita: Record<string, unknown> | undefined
    servidor.use(
      http.post(`${API}/tratamientos`, async ({ request }) => {
        orden.push('plan')
        cuerpoPlan = await request.json()
        return HttpResponse.json({ id: 't-nuevo', pacienteId: ID_PACIENTE, estado: 'EN_PROCESO' }, { status: 201 })
      }),
      agenda.crearCita(({ cuerpo }) => {
        orden.push('cita')
        cuerpoCita = cuerpo as Record<string, unknown>
        return respuestaCita(cita({ id: 'c-presupuesto', fecha: MANANA, hora: '09:00:00' }), 201)
      }),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    await cambiarFecha(d, 'Fecha', MANANA)
    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:00'))
    await userEvent.selectOptions(within(d).getByLabelText('Procedimiento'), 'Evaluación & Diagnóstico')
    await userEvent.click(within(d).getByRole('checkbox', { name: 'Es un tratamiento con presupuesto' }))
    await userEvent.clear(within(d).getByLabelText('Costo total (S/)'))
    await userEvent.type(within(d).getByLabelText('Costo total (S/)'), '1500')
    await userEvent.clear(within(d).getByLabelText('Sesiones estimadas'))
    await userEvent.type(within(d).getByLabelText('Sesiones estimadas'), '5')
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => expect(orden).toEqual(['plan', 'cita']))
    expect(cuerpoPlan).toMatchObject({
      pacienteId: ID_PACIENTE,
      sedeId: 'sede-chilca',
      especialistaId: ID_PAUCAR,
      nombre: 'Evaluación & Diagnóstico',
      modalidadPago: 'CUOTAS',
      costoTotal: 1500,
      totalSesiones: 5,
    })
    expect(cuerpoCita).toMatchObject({ tratamientoId: 't-nuevo', sillonNumero: 1 })
  })

  it('sin activar presupuesto agenda la cita sin crear un plan', async () => {
    api()
    let planesCreados = 0
    let cuerpoCita: Record<string, unknown> | undefined
    servidor.use(
      http.post(`${API}/tratamientos`, () => {
        planesCreados++
        return HttpResponse.json({ id: 'inesperado' }, { status: 201 })
      }),
      agenda.crearCita(({ cuerpo }) => {
        cuerpoCita = cuerpo as Record<string, unknown>
        return respuestaCita(cita({ id: 'c-normal', fecha: MANANA, hora: '09:00:00' }), 201)
      }),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    await cambiarFecha(d, 'Fecha', MANANA)
    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:00'))
    await userEvent.selectOptions(within(d).getByLabelText('Procedimiento'), 'Evaluación & Diagnóstico')
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => expect(cuerpoCita).toBeDefined())
    expect(planesCreados).toBe(0)
    expect(cuerpoCita).not.toHaveProperty('tratamientoId')
  })

  it('reintentar la cita después de un error reutiliza el plan ya creado', async () => {
    api()
    let planesCreados = 0
    const cuerposCita: Record<string, unknown>[] = []
    servidor.use(
      http.post(`${API}/tratamientos`, async ({ request }) => {
        planesCreados++
        await request.json()
        return HttpResponse.json({ id: 't-unico', pacienteId: ID_PACIENTE, estado: 'EN_PROCESO' }, { status: 201 })
      }),
      agenda.crearCita(({ cuerpo }) => {
        cuerposCita.push(cuerpo as Record<string, unknown>)
        return cuerposCita.length === 1
          ? errorBackend(409, 'El sillón ya está ocupado.')
          : respuestaCita(cita({ id: 'c-reintento', fecha: MANANA, hora: '09:30:00' }), 201)
      }),
    )
    const d = await abrirNueva()
    await elegirPaciente(d)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), ID_PAUCAR)
    await cambiarFecha(d, 'Fecha', MANANA)
    await userEvent.selectOptions(within(d).getByLabelText('Procedimiento'), 'Evaluación & Diagnóstico')
    await userEvent.click(within(d).getByRole('checkbox', { name: 'Es un tratamiento con presupuesto' }))
    await userEvent.type(within(d).getByLabelText('Costo total (S/)'), '1200')
    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:00'))
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))
    expect(await within(d).findByText(/Al reintentar se usará el mismo plan/)).toBeInTheDocument()

    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:30'))
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => expect(cuerposCita).toHaveLength(2))
    expect(planesCreados).toBe(1)
    expect(cuerposCita[0]).toMatchObject({ tratamientoId: 't-unico' })
    expect(cuerposCita[1]).toMatchObject({ tratamientoId: 't-unico' })
  })

  it('el procedimiento se elige del catálogo de servicios activos, agrupado por especialidad', async () => {
    api()
    const d = await abrirNueva()
    const selector = await within(d).findByLabelText('Procedimiento')
    await waitFor(() => expect(within(selector).getByRole('option', { name: 'Limpieza & Profilaxis profunda' })).toBeInTheDocument())
    expect(within(selector).getByRole('group', { name: 'Odontología general' })).toBeInTheDocument()
    // Un servicio desactivado no se ofrece.
    expect(within(selector).queryByRole('option', { name: 'Servicio retirado' })).not.toBeInTheDocument()
    expect(within(selector).getByRole('option', { name: 'Otro motivo (escribir)' })).toBeInTheDocument()
    expect(within(d).queryByLabelText('Motivo o procedimiento')).not.toBeInTheDocument()
  })

  it('"Otro motivo" abre el campo de texto y se puede volver al catálogo', async () => {
    api()
    const d = await abrirNueva()
    await waitFor(() => expect(within(d).getByRole('option', { name: 'Limpieza & Profilaxis profunda' })).toBeInTheDocument())
    await userEvent.selectOptions(within(d).getByLabelText('Procedimiento'), 'Otro motivo (escribir)')
    await userEvent.type(within(d).getByLabelText('Motivo o procedimiento'), 'Dolor de muela')
    expect(within(d).getByLabelText('Motivo o procedimiento')).toHaveValue('Dolor de muela')

    await userEvent.click(within(d).getByRole('button', { name: 'Elegir del catálogo de procedimientos' }))
    expect(within(d).getByLabelText('Procedimiento')).toHaveValue('')
    expect(within(d).queryByLabelText('Motivo o procedimiento')).not.toBeInTheDocument()
  })

  it('si el catálogo no responde se puede escribir el motivo igual', async () => {
    api()
    servidor.use(http.get(`${API}/servicios`, () => new HttpResponse(null, { status: 500 })))
    const d = await abrirNueva()
    expect(await within(d).findByLabelText('Motivo o procedimiento')).toBeInTheDocument()
  })

  it('no permite agendar si la sede activa no está entre las sedes activas (B-023)', async () => {
    api({ sedes: SEDES.filter((s) => s.id !== 'sede-chilca') })
    const d = await abrirNueva()
    expect(await within(d).findByText('La sede activa no está disponible')).toBeInTheDocument()
    expect(within(d).getByRole('button', { name: 'Agendar cita' })).toBeDisabled()
  })

  it('registro rápido de paciente con POST /pacientes (PacienteRequestDTO) y lo selecciona', async () => {
    api()
    let cuerpo: unknown
    servidor.use(
      agenda.crearPaciente(({ cuerpo: c }) => {
        cuerpo = c
        return respuestaPaciente({ ...PACIENTES[0], id: 'a1b2c3d4-0000-4000-8000-000000000002', nombreCompleto: 'Rosa Mendoza Bravo', dni: '70192844' })
      }),
    )
    const d = await abrirNueva()
    await userEvent.click(within(d).getByRole('button', { name: 'Registrar paciente nuevo' }))
    await userEvent.type(within(d).getByLabelText('DNI'), '7019')
    await userEvent.type(within(d).getByLabelText('Celular (opcional)'), '812345678')
    await userEvent.click(within(d).getByRole('button', { name: 'Registrar y seleccionar' }))
    expect(await within(d).findByText('El DNI debe tener exactamente 8 dígitos numéricos.')).toBeInTheDocument()
    expect(within(d).getByText('El celular debe tener 9 dígitos y empezar con 9.')).toBeInTheDocument()
    expect(within(d).getByText('Los nombres son obligatorios.')).toBeInTheDocument()

    await userEvent.type(within(d).getByLabelText('DNI'), '2844')
    await userEvent.clear(within(d).getByLabelText('Celular (opcional)'))
    await userEvent.type(within(d).getByLabelText('Celular (opcional)'), '987654321')
    await userEvent.type(within(d).getByLabelText('Nombres'), 'Rosa')
    await userEvent.type(within(d).getByLabelText('Apellidos'), 'Mendoza Bravo')
    await userEvent.click(within(d).getByRole('button', { name: 'Registrar y seleccionar' }))
    await waitFor(() =>
      expect(cuerpo).toEqual({ dni: '70192844', nombres: 'Rosa', apellidos: 'Mendoza Bravo', telefono: '987654321', sedeId: 'sede-chilca' }),
    )
    expect(await within(d).findByText('Rosa Mendoza Bravo')).toBeInTheDocument()
    expect(within(d).getByRole('button', { name: 'Cambiar paciente' })).toBeInTheDocument()
  })
})

describe('B5 · Reprogramar cita', { timeout: 20_000 }, () => {
  async function abrirReprogramar() {
    await renderRuta('/app/agenda')
    await userEvent.click(await screen.findByRole('button', { name: /Ver cita: .*Beatriz Tovar Lazo/ }))
    const panel = await screen.findByRole('dialog', { name: 'Beatriz Tovar Lazo' })
    await userEvent.click(within(panel).getByRole('button', { name: 'Reprogramar' }))
    return { panel, d: await screen.findByRole('dialog', { name: 'Reprogramar cita' }) }
  }

  it('envía PATCH /citas/{id}/reprogramar { nuevaFecha, nuevoHorario, motivo } y actualiza el detalle', async () => {
    api()
    let cuerpo: unknown
    let id: string | undefined
    servidor.use(
      agenda.reprogramar(({ cuerpo: c, id: i }) => {
        cuerpo = c
        id = i
        return respuestaCita({ ...CITA, fecha: MANANA, hora: '09:30:00', motivoReprogramacion: 'Pidió la tarde libre' })
      }),
    )
    const { panel, d } = await abrirReprogramar()
    await cambiarFecha(d, 'Nueva fecha', MANANA)
    await userEvent.click(await within(d).findByLabelText('09:30'))
    await userEvent.type(within(d).getByLabelText('Motivo de la reprogramación'), 'Pide')
    await userEvent.click(within(d).getByRole('button', { name: 'Reprogramar' }))
    expect(await within(d).findByText('El motivo debe tener al menos 5 caracteres.')).toBeInTheDocument()
    expect(cuerpo).toBeUndefined()

    await userEvent.clear(within(d).getByLabelText('Motivo de la reprogramación'))
    await userEvent.type(within(d).getByLabelText('Motivo de la reprogramación'), 'Pidió la tarde libre')
    await userEvent.click(within(d).getByRole('button', { name: 'Reprogramar' }))
    await waitFor(() => expect(cuerpo).toEqual({ nuevaFecha: MANANA, nuevoHorario: '09:30', motivo: 'Pidió la tarde libre' }))
    expect(id).toBe(CITA.id)
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Reprogramar cita' })).not.toBeInTheDocument())
    expect(await within(panel).findByText(/Viernes 09\/10\/2026 · 09:30/)).toBeInTheDocument()
  })

  it('muestra el error del backend (400 si la cita ya fue atendida, 409 si choca)', async () => {
    api()
    servidor.use(agenda.reprogramar(() => errorBackend(409, 'El sillón #1 ya está ocupado en ese horario.')))
    const { d } = await abrirReprogramar()
    await cambiarFecha(d, 'Nueva fecha', MANANA)
    await userEvent.click(await within(d).findByLabelText('09:00'))
    await userEvent.type(within(d).getByLabelText('Motivo de la reprogramación'), 'Cambio de turno')
    await userEvent.click(within(d).getByRole('button', { name: 'Reprogramar' }))
    expect(await within(d).findByText('El sillón #1 ya está ocupado en ese horario.')).toBeInTheDocument()
  })
})
