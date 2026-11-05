import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { ID_PACIENTE, ID_PAUCAR, PACIENTES, agenda, cita, disponibilidad, respuestaCita } from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

const PACIENTE = PACIENTES[0]
const MANANA = '2026-10-09'

let cuerpo: Record<string, unknown> | null = null
let consultasCitas = 0

function api() {
  cuerpo = null
  consultasCitas = 0
  servidor.use(
    http.get(`${API}/pacientes/:id`, () => HttpResponse.json(PACIENTE)),
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/pagos/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/evoluciones/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/odontograma/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/radiografias/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/pacientes/:id`, () => HttpResponse.json([])),
    http.post(`${API}/historia-clinica/pacientes/:id/apertura`, () => HttpResponse.json({})),
    // Próxima cita del Resumen: se cuenta cuántas veces se consulta para comprobar que se refresca.
    http.get(`${API}/citas`, () => {
      consultasCitas += 1
      return HttpResponse.json(
        cuerpo ? [cita({ id: 'c-nueva', pacienteId: ID_PACIENTE, fecha: MANANA, hora: '09:00:00', motivoConsulta: 'Control de ortodoncia', estado: 'PROGRAMADA' })] : [],
      )
    }),
    agenda.especialistas(),
    agenda.horarios(),
    agenda.disponibilidad(({ url }) => HttpResponse.json(disponibilidad({ fecha: url.searchParams.get('fecha')! }))),
    agenda.crearCita(({ cuerpo: c }) => {
      cuerpo = c as Record<string, unknown>
      return respuestaCita(cita({ id: 'c-nueva', fecha: MANANA, hora: '09:00:00' }), 201)
    }),
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-08T15:00:00Z'))
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => {
  vi.useRealTimers()
  limpiarSesion()
})

describe('Ficha · nueva cita sin salir a la agenda', { timeout: 30_000 }, () => {
  it('abre el diálogo en la misma ficha con el paciente ya elegido, agenda y refresca la próxima cita', async () => {
    const user = userEvent.setup({ delay: null })
    api()
    const { router } = await renderRuta(`/app/pacientes/${ID_PACIENTE}`)
    await screen.findByRole('heading', { name: 'Beatriz Tovar Lazo', level: 1 })
    const rutaFicha = router.state.location.pathname

    await user.click(screen.getByRole('button', { name: '+ Nueva Cita' }))
    const d = within(await screen.findByRole('dialog', { name: 'Nueva cita' }))
    expect(router.state.location.pathname).toBe(rutaFicha)
    expect(d.getByText('Beatriz Tovar Lazo')).toBeInTheDocument()
    expect(d.queryByLabelText('Paciente')).not.toBeInTheDocument()

    await user.selectOptions(d.getByLabelText('Especialista'), ID_PAUCAR)
    const fecha = d.getByLabelText('Fecha')
    await user.clear(fecha)
    await user.type(fecha, MANANA)
    await user.click(within(await d.findByRole('group', { name: 'Horario de la cita' })).getByLabelText('09:00'))
    await user.selectOptions(d.getByLabelText('Procedimiento'), 'Otro motivo (escribir)')
    await user.type(d.getByLabelText('Motivo o procedimiento'), 'Control de ortodoncia')
    const antes = consultasCitas
    await user.click(d.getByRole('button', { name: 'Agendar cita' }))

    await waitFor(() => expect(cuerpo).not.toBeNull())
    expect(cuerpo).toMatchObject({
      pacienteId: ID_PACIENTE,
      pacienteNombre: 'Beatriz Tovar Lazo',
      pacienteTelefono: PACIENTE.telefono,
      doctorId: ID_PAUCAR,
      fecha: MANANA,
      hora: '09:00',
    })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('Cita agendada')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(rutaFicha)
    await waitFor(() => expect(consultasCitas).toBeGreaterThan(antes))
    expect(await screen.findByText('Control de ortodoncia')).toBeInTheDocument()
  })

  it('cancelar cierra el diálogo y deja al usuario en la ficha', async () => {
    const user = userEvent.setup({ delay: null })
    api()
    const { router } = await renderRuta(`/app/pacientes/${ID_PACIENTE}`)
    await screen.findByRole('heading', { name: 'Beatriz Tovar Lazo', level: 1 })
    await user.click(screen.getByRole('button', { name: '+ Nueva Cita' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe(`/app/pacientes/${ID_PACIENTE}`)
    expect(cuerpo).toBeNull()
  })
})
