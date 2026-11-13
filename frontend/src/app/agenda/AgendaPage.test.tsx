import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion, type Rol } from '@/shared/auth'
import { HttpResponse, errorBackend, manejadores, servidor } from '@/test/msw'
import { ID_PAUCAR, ID_ROJAS, agenda, cita, disponibilidad } from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

const HOY = '2026-10-08'

function sesion(rol: Rol = 'RECEPCION', id = 'u-1') {
  guardarSesion({
    token: 't',
    usuario: { id, nombre: 'Katherin Mendoza', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

const C_PAUCAR = cita({ pacienteNombre: 'Beatriz Tovar Lazo', hora: '09:00:00', duracionMinutos: 60 })
const C_ROJAS = cita({
  id: 'c0000000-0000-4000-8000-0000000000e1',
  pacienteNombre: 'Carlos Huamán',
  doctorId: ID_ROJAS,
  doctorNombre: 'Dr. Luis Rojas',
  hora: '10:00:00',
  estado: 'CONFIRMADA',
  sillonNumero: 2,
})

let consultas: URLSearchParams[] = []
let dispPedidas: string[] = []

function api(citas = [C_PAUCAR, C_ROJAS]) {
  consultas = []
  dispPedidas = []
  servidor.use(
    manejadores.sedes(),
    agenda.especialistas(),
    agenda.horarios(),
    agenda.listaEspera([]),
    agenda.reservasWeb([]),
    agenda.citas(({ url }) => {
      consultas.push(url.searchParams)
      return HttpResponse.json(citas)
    }),
    agenda.disponibilidad(({ url, id }) => {
      dispPedidas.push(`${id}|${url.searchParams.get('fecha')}|${url.searchParams.get('sedeId')}`)
      return HttpResponse.json(
        id === ID_PAUCAR
          ? disponibilidad({ fecha: url.searchParams.get('fecha')! })
          : disponibilidad({ especialistaId: ID_ROJAS, atiendeEseDia: false, slots: [] }),
      )
    }),
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

function tabla() {
  return screen.getByRole('table')
}

describe('B3 · Agenda diaria y semanal', { timeout: 15_000 }, () => {
  it('vista diaria por especialista con citas, franjas libres/ocupadas y fuera de horario', async () => {
    api()
    await renderRuta('/app/agenda')
    expect(screen.getByRole('heading', { level: 1, name: /Jueves, 8 de octubre de 2026/ })).toBeInTheDocument()
    expect(screen.queryByText('Cargando la agenda…') ?? screen.getByRole('table')).toBeInTheDocument()

    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Columnas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Por sillón/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Por especialista/ })).not.toBeInTheDocument()
    // La antigua "Mesa de Operaciones" se movió al Inicio: la agenda solo tiene vista diaria y semanal.
    expect(screen.queryByRole('button', { name: /Mesa de Operaciones/ })).not.toBeInTheDocument()
    const t = within(tabla())
    expect(t.getByRole('columnheader', { name: /Sillón 1 · Dra\. Elena Paucar/ })).toHaveTextContent('08:00–13:00')
    expect(t.getByRole('columnheader', { name: /Sillón 2 · Dr\. Luis Rojas/ })).toBeInTheDocument()
    expect(t.getByText('Beatriz Tovar Lazo')).toBeInTheDocument()
    expect(t.getByText('Continúa: Beatriz Tovar Lazo')).toBeInTheDocument()
    expect(t.getByText('Carlos Huamán')).toBeInTheDocument()
    await waitFor(() => expect(t.getAllByText('Libre').length).toBeGreaterThan(0))
    expect(t.getAllByText('Ocupado').length).toBeGreaterThan(0)
    expect(t.getAllByText('Fuera de horario').length).toBeGreaterThan(0)
    // Hora actual (10:00 en Lima).
    expect(t.getByText('Ahora 10:00')).toBeInTheDocument()

    expect(consultas[0].get('fecha')).toBe(HOY)
    expect(consultas[0].get('sedeId')).toBe('sede-chilca')
    expect(dispPedidas).toContain(`${ID_PAUCAR}|${HOY}|sede-chilca`)
    // Resumen con conteos de las citas recibidas.
    expect(within(screen.getByRole('region', { name: 'Resumen de citas' })).getByText('Confirmadas').previousSibling).toHaveTextContent('1')
  })

  it('navega al día siguiente y a la vista semanal (desde/hasta de lunes a domingo)', async () => {
    api()
    await renderRuta('/app/agenda')
    await screen.findByRole('table')
    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }))
    expect(screen.getByRole('heading', { level: 1, name: /Viernes, 9 de octubre/ })).toBeInTheDocument()
    await waitFor(() => expect(consultas.some((q) => q.get('fecha') === '2026-10-09')).toBe(true))

    await userEvent.click(screen.getByRole('button', { name: 'Semana' }))
    expect(screen.getByRole('heading', { level: 1, name: 'Semana del 05/10/2026 al 11/10/2026' })).toBeInTheDocument()
    await waitFor(() =>
      expect(consultas.some((q) => q.get('desde') === '2026-10-05' && q.get('hasta') === '2026-10-11')).toBe(true),
    )
    const t = await screen.findByRole('table')
    expect(within(t).getByRole('button', { name: /Ver el día Jue 08\/10\/2026 \(2 citas\)/ })).toBeInTheDocument()
    await userEvent.click(within(t).getByRole('button', { name: /Ver el día Lun 05\/10\/2026/ }))
    expect(screen.getByRole('heading', { level: 1, name: /Lunes, 5 de octubre/ })).toBeInTheDocument()
  })

  it('filtra por especialista y mantiene el encabezado Sillón N · Nombre', async () => {
    api()
    await renderRuta('/app/agenda')
    await screen.findByRole('table')
    expect(screen.queryByRole('group', { name: 'Columnas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Por sillón/ })).not.toBeInTheDocument()

    await userEvent.click(within(screen.getByRole('group', { name: 'Especialista' })).getByRole('button', { name: 'Dr. Luis Rojas' }))
    expect(within(tabla()).queryByRole('columnheader', { name: /Sillón 1 · Dra\. Elena Paucar/ })).not.toBeInTheDocument()
    expect(within(tabla()).getByRole('columnheader', { name: /Sillón 2 · Dr\. Luis Rojas/ })).toBeInTheDocument()
    expect(within(tabla()).queryByText('Beatriz Tovar Lazo')).not.toBeInTheDocument()

    await userEvent.click(within(screen.getByRole('group', { name: 'Especialista' })).getByRole('button', { name: 'Todos (2)' }))
    expect(within(tabla()).getByRole('columnheader', { name: /Sillón 1 · Dra\. Elena Paucar/ })).toBeInTheDocument()
    expect(within(tabla()).getByRole('columnheader', { name: /Sillón 2 · Dr\. Luis Rojas/ })).toBeInTheDocument()
  })

  it('día sin citas y error con reintentar', async () => {
    api([])
    await renderRuta('/app/agenda')
    expect(await screen.findByText('No hay citas para este día.')).toBeInTheDocument()

    let fallar = true
    servidor.use(agenda.citas(() => (fallar ? errorBackend(400, 'La fecha no es válida.') : HttpResponse.json([C_PAUCAR]))))
    await userEvent.click(screen.getByRole('button', { name: 'Día siguiente' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La fecha no es válida.')
    fallar = false
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(await within(await screen.findByRole('table')).findByText('Beatriz Tovar Lazo')).toBeInTheDocument()
  })

  it('ODONTOLOGO ve solo su agenda (especialista vinculado a su usuario)', async () => {
    sesion('ODONTOLOGO', '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a')
    api()
    await renderRuta('/app/agenda')
    const t = within(await screen.findByRole('table'))
    expect(t.getByRole('columnheader', { name: /Sillón 2 · Dr\. Luis Rojas/ })).toBeInTheDocument()
    expect(t.queryByRole('columnheader', { name: /Sillón 1 · Dra\. Elena Paucar/ })).not.toBeInTheDocument()
    expect(t.queryByText('Beatriz Tovar Lazo')).not.toBeInTheDocument()
    expect(screen.queryByRole('group', { name: 'Especialista' })).not.toBeInTheDocument()
  })

  it('ODONTOLOGO sin especialista vinculado: aviso', async () => {
    sesion('ODONTOLOGO', 'otro-usuario')
    api()
    await renderRuta('/app/agenda')
    expect(await screen.findByText('Tu usuario no está vinculado a un especialista')).toBeInTheDocument()
  })

  it('ODONTOLOGO que suma la función de RECEPCION ve la agenda de todos los doctores', async () => {
    guardarSesion({
      token: 't',
      usuario: {
        id: '9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a',
        nombre: 'Doble Función',
        rol: 'ODONTOLOGO',
        rolesAdicionales: ['RECEPCION'],
        sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }],
      },
      sedeActivaId: 'sede-chilca',
    })
    api()
    await renderRuta('/app/agenda')
    const t = within(await screen.findByRole('table'))
    expect(t.getByRole('columnheader', { name: /Sillón 2 · Dr\. Luis Rojas/ })).toBeInTheDocument()
    expect(t.getByRole('columnheader', { name: /Sillón 1 · Dra\. Elena Paucar/ })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Especialista' })).toBeInTheDocument()
  })

  it('el nombre del paciente en la cita es un enlace a su ficha', async () => {
    sesion('RECEPCION')
    api()
    await renderRuta('/app/agenda')
    const t = within(await screen.findByRole('table'))
    const enlace = await t.findByRole('link', { name: 'Beatriz Tovar Lazo' })
    expect(enlace.getAttribute('href')).toContain(`/app/pacientes/${C_PAUCAR.pacienteId}`)
    expect(enlace.getAttribute('href')).toContain('desde=agenda')
  })
})
