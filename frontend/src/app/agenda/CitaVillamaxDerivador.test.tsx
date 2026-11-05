import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { API, HttpResponse, SEDES, SEDE_VILLAMAX, http, manejadores, servidor } from '@/test/msw'
import {
  ID_PACIENTE,
  agenda,
  cita,
  disponibilidad,
  respuestaCita,
} from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

const MANANA = '2026-10-09'

const ESP_VILLAMAX = {
  id: 'doc-villamax-1',
  nombre: 'Dr. Radiólogo Villamax',
  especialidad: 'Radiología Bucal y Maxilofacial',
  sedes: ['sede-villamax-chilca'],
  activo: true,
  sillonesPorSede: { 'sede-villamax-chilca': 1 },
}

const DERIVADORES = [
  {
    id: 'derivador-1',
    nombre: 'Dr. Carlos Mendoza Ortodoncia',
    cop: '33445',
    clinicaOrigen: 'Clínica Dental Sur',
    porcentajeOTarifaFija: 10,
    activo: true,
  },
  {
    id: 'derivador-2',
    nombre: 'Dra. Ana Lopez Endodoncia',
    cop: '55667',
    clinicaOrigen: 'OdontoSalud',
    porcentajeOTarifaFija: 12,
    activo: true,
  },
]

function sesionVillamax(rol: 'ADMIN_GENERAL' | 'RECEPCION' | 'ODONTOLOGO' = 'RECEPCION') {
  guardarSesion({
    token: 't',
    usuario: {
      id: 'u-villamax',
      nombre: 'Usuario Villamax',
      rol,
      sedes: [{ id: 'sede-villamax-chilca', nombre: 'Sede Villamax Chilca', marca: 'VILLAMAX' }],
    },
    sedeActivaId: 'sede-villamax-chilca',
    negocioActivo: 'VILLAMAX',
  })
}

function api() {
  servidor.use(
    manejadores.sedes([...SEDES, SEDE_VILLAMAX]),
    agenda.especialistas([ESP_VILLAMAX]),
    agenda.horarios([
      { diaSemana: 5, horaInicio: '08:00:00', horaFin: '18:00:00', sedeId: 'sede-villamax-chilca' },
    ]),
    agenda.listaEspera([]),
    agenda.reservasWeb([]),
    agenda.citas([]),
    agenda.pacientes(),
    agenda.paciente(),
    agenda.disponibilidad(() =>
      HttpResponse.json(disponibilidad({ fecha: MANANA })),
    ),
    http.get(`${API}/radiologia/derivadores`, () => {
      return HttpResponse.json(DERIVADORES)
    }),
    http.post(`${API}/tratamientos`, () => {
      return HttpResponse.json({ id: 't-1', pacienteId: ID_PACIENTE, estado: 'EN_PROCESO' }, { status: 201 })
    }),
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-08T15:00:00Z'))
  sessionStorage.clear()
  _reiniciarCacheSesion()
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

describe('Paso 5 · Doctor derivador obligatorio y gestión en Villamax', { timeout: 20_000 }, () => {
  it('en Villamax muestra selector obligatorio de doctor derivador con opciones activas y paciente directo', async () => {
    sesionVillamax()
    api()
    const d = await abrirNueva()
    await elegirPaciente(d)

    const selectDerivador = within(d).getByLabelText(/Doctor que deriva \*/i)
    expect(selectDerivador).toBeInTheDocument()

    // Opciones presentes: placeholder, paciente directo, y derivadores cargados
    expect(within(selectDerivador).getByRole('option', { name: /Paciente directo, sin derivación/i })).toBeInTheDocument()
    await waitFor(() => {
      expect(within(selectDerivador).getByRole('option', { name: /Dr\. Carlos Mendoza Ortodoncia/i })).toBeInTheDocument()
    })
  })

  it('valida que en Villamax el doctor derivador o paciente directo sea obligatorio al agendar', async () => {
    sesionVillamax()
    api()
    const d = await abrirNueva()
    await elegirPaciente(d)

    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), 'doc-villamax-1')
    const campoFecha = within(d).getByLabelText('Fecha')
    await userEvent.clear(campoFecha)
    await userEvent.type(campoFecha, MANANA)
    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:00'))

    await userEvent.selectOptions(within(d).getByLabelText(/Estudio/i), 'Radiografía Panorámica')

    // Intento de agendar sin seleccionar derivador ni paciente directo
    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    // Muestra mensaje de error obligatorio
    expect(await within(d).findByText(/Selecciona el doctor que deriva o 'Paciente directo, sin derivación'/i)).toBeInTheDocument()
  })

  it('al seleccionar Paciente directo envía pacienteDirecto: true y doctorDerivadorId: undefined', async () => {
    sesionVillamax()
    api()
    let cuerpoCita = null as Record<string, unknown> | null
    servidor.use(
      agenda.crearCita(({ cuerpo }) => {
        cuerpoCita = cuerpo as Record<string, unknown>
        return respuestaCita(cita({ id: 'c-1', fecha: MANANA, hora: '09:00:00' }), 201)
      }),
    )

    const d = await abrirNueva()
    await elegirPaciente(d)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), 'doc-villamax-1')
    const campoFecha = within(d).getByLabelText('Fecha')
    await userEvent.clear(campoFecha)
    await userEvent.type(campoFecha, MANANA)
    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:00'))

    await userEvent.selectOptions(within(d).getByLabelText(/Estudio/i), 'Radiografía Panorámica')
    await userEvent.selectOptions(within(d).getByLabelText(/Doctor que deriva \*/i), 'PACIENTE_DIRECTO')

    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => {
      expect(cuerpoCita).not.toBeNull()
    })
    expect(cuerpoCita?.pacienteDirecto).toBe(true)
    expect(cuerpoCita?.doctorDerivadorId).toBeUndefined()
  })

  it('al seleccionar doctor derivador envía su id y pacienteDirecto: false', async () => {
    sesionVillamax()
    api()
    let cuerpoCita = null as Record<string, unknown> | null
    servidor.use(
      agenda.crearCita(({ cuerpo }) => {
        cuerpoCita = cuerpo as Record<string, unknown>
        return respuestaCita(cita({ id: 'c-2', fecha: MANANA, hora: '09:00:00' }), 201)
      }),
    )

    const d = await abrirNueva()
    await elegirPaciente(d)
    await userEvent.selectOptions(within(d).getByLabelText('Especialista'), 'doc-villamax-1')
    const campoFecha = within(d).getByLabelText('Fecha')
    await userEvent.clear(campoFecha)
    await userEvent.type(campoFecha, MANANA)
    await userEvent.click(await within(await within(d).findByRole('group', { name: 'Horario de la cita' })).findByLabelText('09:00'))

    await userEvent.selectOptions(within(d).getByLabelText(/Estudio/i), 'Radiografía Panorámica')

    await waitFor(() => {
      expect(within(within(d).getByLabelText(/Doctor que deriva \*/i)).getByRole('option', { name: /Dr\. Carlos Mendoza Ortodoncia/i })).toBeInTheDocument()
    })
    await userEvent.selectOptions(within(d).getByLabelText(/Doctor que deriva \*/i), 'derivador-1')

    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => {
      expect(cuerpoCita).not.toBeNull()
    })
    expect(cuerpoCita?.pacienteDirecto).toBe(false)
    expect(cuerpoCita?.doctorDerivadorId).toBe('derivador-1')
  })

  it('+ Doctor derivador abre modal de alta rápida', async () => {
    sesionVillamax()
    api()
    const d = await abrirNueva()

    const btnAltaRapida = within(d).getByRole('button', { name: /\+ Doctor derivador/i })
    expect(btnAltaRapida).toBeInTheDocument()

    await userEvent.click(btnAltaRapida)

    expect(await screen.findByRole('dialog', { name: /Registrar Doctor Derivador/i })).toBeInTheDocument()
  })

  it('en RadiologiaPage solo ADMIN_GENERAL ve el botón + Registrar comisión', async () => {
    // 1. Con ODONTOLOGO no se muestra (solo ADMIN_GENERAL)
    sesionVillamax('ODONTOLOGO')
    servidor.use(
      http.get(`${API}/radiologia/comisiones`, () => HttpResponse.json([])),
      http.get(`${API}/radiologia/liquidaciones`, () => HttpResponse.json([])),
      http.get(`${API}/radiologia/derivadores`, () => HttpResponse.json([])),
    )
    const { unmount } = await renderRuta('/app/radiologia')
    await screen.findByRole('heading', { name: /Villamax/i })

    expect(screen.queryByRole('button', { name: /\+ Registrar comisión/i })).not.toBeInTheDocument()
    unmount()

    // 2. Con ADMIN_GENERAL sí se muestra
    _reiniciarCacheSesion()
    sesionVillamax('ADMIN_GENERAL')
    await renderRuta('/app/radiologia')
    await screen.findByRole('heading', { name: /Villamax/i })

    expect(await screen.findByRole('button', { name: /\+ Registrar comisión/i })).toBeInTheDocument()
  })
})
