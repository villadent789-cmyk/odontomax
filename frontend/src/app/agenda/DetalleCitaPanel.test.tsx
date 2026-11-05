import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion, type Rol } from '@/shared/auth'
import { HttpResponse, delay, manejadores, servidor } from '@/test/msw'
import { ID_PACIENTE, ID_PAUCAR, PACIENTES, agenda, cita, disponibilidad, respuestaCita } from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

function sesion(rol: Rol = 'RECEPCION') {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin Mendoza', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

const CITA = cita({
  hora: '09:00:00',
  duracionMinutos: 60,
  codigoTicket: 'OM-7K9M2P4X',
  notas: '[REPROGRAMACIÓN 2026-10-07] De 2026-10-07 10:00 a 2026-10-08 09:00. Motivo: Pidió otro día',
  motivoReprogramacion: 'Pidió otro día',
})

function api(c = CITA) {
  servidor.use(
    manejadores.sedes(),
    agenda.especialistas(),
    agenda.horarios(),
    agenda.listaEspera([]),
    agenda.reservasWeb([]),
    agenda.citas([c]),
    agenda.disponibilidad(({ id }) => HttpResponse.json(disponibilidad({ especialistaId: id, slots: id === ID_PAUCAR ? undefined : [] }))),
    agenda.paciente(PACIENTES[0]),
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

async function abrir(nombre = 'Beatriz Tovar Lazo') {
  await renderRuta('/app/agenda')
  const tarjeta = await screen.findByRole('button', { name: new RegExp(`Ver cita: .*${nombre}`) })
  await userEvent.click(tarjeta)
  return { tarjeta, panel: await screen.findByRole('dialog', { name: nombre }) }
}

describe('B4 · Detalle de cita', { timeout: 15_000 }, () => {
  it('muestra cita, paciente (GET /pacientes/{id}) y avisos reales', async () => {
    api()
    const { panel } = await abrir()
    const p = within(panel)
    expect(p.getByText('Programada')).toHaveAttribute('data-estado', 'PROGRAMADA')
    expect(await p.findByText('42891023')).toBeInTheDocument()
    expect(p.getByText('Alergias registradas: Penicilina')).toBeInTheDocument()
    expect(p.getByText('Saldo pendiente: S/ 120.00')).toBeInTheDocument()
    expect(p.getByText('Sede Chilca · Sillón 1')).toBeInTheDocument()
    expect(p.getByText('Jueves 08/10/2026 · 09:00 a 10:00 (60 min)')).toBeInTheDocument()
    expect(p.getByText('Dra. Elena Paucar')).toBeInTheDocument()
    expect(p.getByText('OM-7K9M2P4X')).toBeInTheDocument()
    expect(p.getByText('Pidió otro día')).toBeInTheDocument()
    expect(p.getByText(/\[REPROGRAMACIÓN 2026-10-07\]/)).toBeInTheDocument()
    expect(p.getByRole('link', { name: /Llamar al paciente/ })).toHaveAttribute('href', 'tel:+51964123456')
  })

  it('cambia el estado desde el panel y lo refleja con la respuesta del backend', async () => {
    api()
    let cuerpo: unknown
    servidor.use(
      agenda.cambiarEstado(async ({ cuerpo: c }) => {
        cuerpo = c
        await delay(20)
        return respuestaCita({ ...CITA, estado: 'CONFIRMADA' })
      }),
    )
    const { panel } = await abrir()
    await userEvent.click(within(panel).getByRole('button', { name: 'Confirmar' }))
    await waitFor(() => expect(cuerpo).toEqual({ estado: 'CONFIRMADA' }))
    expect(await within(panel).findByText('Confirmada')).toHaveAttribute('data-estado', 'CONFIRMADA')
    expect(within(panel).queryByRole('button', { name: 'Confirmar' })).not.toBeInTheDocument()
  })

  it('cancela desde el panel con motivo y cierra con Escape devolviendo el foco a la tarjeta', async () => {
    api()
    let cuerpo: unknown
    servidor.use(
      agenda.cancelar(({ cuerpo: c }) => {
        cuerpo = c
        return respuestaCita({ ...CITA, estado: 'CANCELADA', motivoCancelacion: 'Paciente viajó' })
      }),
    )
    const { panel, tarjeta } = await abrir()
    await userEvent.click(within(panel).getByRole('button', { name: 'Cancelar' }))
    const dialogo = await screen.findByRole('dialog', { name: 'Cancelar cita' })
    await userEvent.type(within(dialogo).getByLabelText('Motivo de la cancelación'), 'Paciente viajó')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar cita' }))
    await waitFor(() => expect(cuerpo).toEqual({ motivo: 'Paciente viajó' }))
    expect(await within(panel).findByText('Cancelada')).toBeInTheDocument()
    expect(within(panel).getByText('Paciente viajó')).toBeInTheDocument()
    // Cancelada: sin acciones.
    expect(within(panel).queryByRole('group', { name: /Acciones/ })).not.toBeInTheDocument()

    // Primer Escape cierra la pantalla de cupo liberado; el segundo, el panel.
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Horario Liberado/ })).not.toBeInTheDocument())
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(tarjeta).toHaveFocus()
  })

  it('si el paciente no se puede cargar, el detalle de la cita se muestra igual', async () => {
    api()
    // 403 del paciente (p. ej. de otra sede): no redirige, solo avisa.
    servidor.use(agenda.pacienteError(403, 'Acceso denegado a la sede'))
    const { panel } = await abrir()
    expect(await within(panel).findByText('No se pudieron cargar los datos del paciente.')).toBeInTheDocument()
    expect(within(panel).getByText('Dra. Elena Paucar')).toBeInTheDocument()
  })

  it('ODONTOLOGO no puede saltarse pasos: en sala no hay botón directo "Marcar atendida"', async () => {
    sesion('ODONTOLOGO')
    api(cita({ estado: 'EN_SALA', doctorId: ID_PAUCAR }))
    servidor.use(agenda.especialistas([{ id: ID_PAUCAR, nombre: 'Dra. Elena Paucar', usuarioId: 'u-1', activo: true, sedes: ['sede-chilca'] }]))
    const { panel } = await abrir()
    expect(within(panel).queryByRole('button', { name: 'Marcar atendida' })).not.toBeInTheDocument()
  })

  it('con la cita en atención ofrece "Atender Paciente / Ver Ficha" hacia el odontograma del paciente', async () => {
    api(cita({ estado: 'EN_ATENCION' }))
    const { panel } = await abrir()
    const enlace = within(panel).getByRole('link', { name: 'Atender Paciente / Ver Ficha' })
    expect(enlace).toHaveAttribute('href', `/app/pacientes/${ID_PACIENTE}?tab=odontograma&desde=agenda`)
  })

  it('fuera de "en atención" no ofrece el acceso directo de atender paciente', async () => {
    api(cita({ estado: 'PROGRAMADA' }))
    const { panel } = await abrir()
    expect(within(panel).queryByRole('link', { name: 'Atender Paciente / Ver Ficha' })).not.toBeInTheDocument()
  })
})
