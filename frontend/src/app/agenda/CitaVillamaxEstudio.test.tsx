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

function sesionVillamax() {
  guardarSesion({
    token: 't',
    usuario: {
      id: 'u-villamax',
      nombre: 'Recepción Villamax',
      rol: 'RECEPCION',
      sedes: [{ id: 'sede-villamax-chilca', nombre: 'Sede Villamax Chilca', marca: 'VILLAMAX' }],
    },
    sedeActivaId: 'sede-villamax-chilca',
    negocioActivo: 'VILLAMAX',
  })
}

function sesionVilladent() {
  guardarSesion({
    token: 't',
    usuario: {
      id: 'u-1',
      nombre: 'Katherin Mendoza',
      rol: 'RECEPCION',
      sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }],
    },
    sedeActivaId: 'sede-chilca',
    negocioActivo: 'VILLADENT',
  })
}

function api(especialistas = [ESP_VILLAMAX]) {
  servidor.use(
    manejadores.sedes([...SEDES, SEDE_VILLAMAX]),
    agenda.especialistas(especialistas),
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

describe('Paso 4 · Flujo de estudio en Villamax y Tarifario en Villadent', { timeout: 20_000 }, () => {
  it('en Villamax al elegir estudio muestra el precio fijo del catálogo y oculta campos de presupuesto y sesiones', async () => {
    sesionVillamax()
    api()
    const d = await abrirNueva()
    await elegirPaciente(d)

    // En Villamax se muestra el selector de estudios
    const selectEstudio = within(d).getByLabelText(/Estudio/i)
    await userEvent.selectOptions(selectEstudio, 'Radiografía Panorámica')

    // Muestra el precio del catálogo (S/ 60.00 en ESTUDIOS_VILLAMAX)
    expect(await within(d).findByText('Precio del estudio:')).toBeInTheDocument()
    expect(within(d).getAllByText('S/ 60.00')).toHaveLength(2) // Precio estudio y Total a cobrar inicial

    // Oculta los campos odontológicos de presupuesto y sesiones
    expect(within(d).queryByRole('checkbox', { name: /Es un tratamiento con presupuesto/i })).not.toBeInTheDocument()
    expect(within(d).queryByLabelText(/Costo total/i)).not.toBeInTheDocument()
    expect(within(d).queryByLabelText(/Sesiones estimadas/i)).not.toBeInTheDocument()

    // Muestra el campo opcional de Descuento (S/) y Total a cobrar en vivo inicial
    expect(within(d).getByLabelText(/Descuento/i)).toBeInTheDocument()
    expect(within(d).getByText('Total a cobrar:')).toBeInTheDocument()
  })

  it('en Villamax calcula el total en vivo con descuento opcional y valida descuento inválido', async () => {
    sesionVillamax()
    api()
    const d = await abrirNueva()
    await elegirPaciente(d)

    const selectEstudio = within(d).getByLabelText(/Estudio/i)
    await userEvent.selectOptions(selectEstudio, 'Radiografía Panorámica')
    await within(d).findByText('Precio del estudio:')

    const campoDesc = within(d).getByLabelText(/Descuento/i)
    await userEvent.type(campoDesc, '10')

    // Total a cobrar = 60 - 10 = S/ 50.00
    expect(within(d).getByText('S/ 50.00')).toBeInTheDocument()

    // Si el descuento excede el precio (ej. 70 > 60), muestra mensaje de validación
    await userEvent.clear(campoDesc)
    await userEvent.type(campoDesc, '70')
    expect(within(d).getByText('El descuento debe ser entre 0 y el precio del estudio.')).toBeInTheDocument()
  })

  it('en Villamax crea automáticamente un plan de una sesión con el precio y descuento, y enlaza la cita', async () => {
    sesionVillamax()
    api()
    let cuerpoPlan = null as Record<string, unknown> | null
    let cuerpoCita = null as Record<string, unknown> | null
    servidor.use(
      http.post(`${API}/tratamientos`, async ({ request }) => {
        cuerpoPlan = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 't-villamax-auto', pacienteId: ID_PACIENTE, estado: 'EN_PROCESO' }, { status: 201 })
      }),
      agenda.crearCita(({ cuerpo }) => {
        cuerpoCita = cuerpo as Record<string, unknown>
        return respuestaCita(cita({ id: 'c-villamax-1', fecha: MANANA, hora: '09:00:00' }), 201)
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
    const campoDesc = within(d).getByLabelText(/Descuento/i)
    await userEvent.type(campoDesc, '15')

    // En Villamax se selecciona paciente directo
    await userEvent.selectOptions(within(d).getByLabelText(/Doctor que deriva/i), 'PACIENTE_DIRECTO')

    await userEvent.click(within(d).getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => {
      expect(cuerpoPlan).not.toBeNull()
      expect(cuerpoCita).not.toBeNull()
    })

    // Plan automático de una sesión: costoTotal = 60, descuentoTotal = 15, totalSesiones = 1
    expect(cuerpoPlan?.nombre).toBe('Radiografía Panorámica')
    expect(cuerpoPlan?.costoTotal).toBe(60)
    expect(cuerpoPlan?.descuentoTotal).toBe(15)
    expect(cuerpoPlan?.totalSesiones).toBe(1)

    // Cita enlazada al plan creado automáticamente
    expect(cuerpoCita?.tratamientoId).toBe('t-villamax-auto')
  })

  it('en Villadent autocompleta el costo total con el precio del catálogo y permite aplicar descuento opcional', async () => {
    sesionVilladent()
    api()
    const d = await abrirNueva()
    await elegirPaciente(d)

    // Al elegir procedimiento del tarifario: Evaluación & Diagnóstico (precio: 30)
    await userEvent.selectOptions(within(d).getByLabelText(/Procedimiento/i), 'Evaluación & Diagnóstico')
    await userEvent.click(within(d).getByRole('checkbox', { name: /Es un tratamiento con presupuesto/i }))

    // Autocompleta el costo con el precio del catálogo (30)
    const campoCosto = within(d).getByLabelText(/Costo total/i)
    expect(campoCosto).toHaveValue(30)

    // Permite aplicar descuento opcional
    const campoDesc = within(d).getByLabelText(/Descuento/i)
    await userEvent.type(campoDesc, '5')

    // Muestra total a cobrar = 30 - 5 = S/ 25.00
    expect(within(d).getByText('S/ 25.00')).toBeInTheDocument()

    // Mantiene campo de sesiones estimadas
    expect(within(d).getByLabelText(/Sesiones estimadas/i)).toBeInTheDocument()
  })
})
