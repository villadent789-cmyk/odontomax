import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion, limpiarSesion, type Rol } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { cita } from '@/test/msw-agenda'
import { renderRuta } from '@/test/renderRuta'

interface Opciones {
  citas?: unknown[]
  reservas?: unknown[]
  tareas?: { tipo: string }[]
  productos?: unknown[] | 'error'
  financiero?: Record<string, unknown> | 'error'
  pacientes?: Record<string, unknown>
}

const contadores = { tareas: 0, financiero: [] as string[], pacientes: [] as string[] }

function api({ citas = [], reservas = [], tareas = [], productos = [], financiero, pacientes }: Opciones = {}) {
  contadores.tareas = 0
  contadores.financiero = []
  contadores.pacientes = []
  servidor.use(
    http.get(`${API}/citas`, () => HttpResponse.json(citas)),
    http.get(`${API}/caja/activa`, () => new HttpResponse(null, { status: 204 })),
    http.get(`${API}/especialistas`, () => HttpResponse.json([])),
    http.get(`${API}/citas/reservas-web-pendientes`, () => HttpResponse.json(reservas)),
    http.get(`${API}/citas/bandeja-tareas`, () => {
      contadores.tareas += 1
      return HttpResponse.json({ tareas })
    }),
    http.get(`${API}/inventario/productos`, () => (productos === 'error' ? errorBackend(500, 'Fallo en inventario') : HttpResponse.json(productos))),
    http.get(`${API}/reportes/financiero`, ({ request }) => {
      const q = new URL(request.url).searchParams
      contadores.financiero.push(`${q.get('desde')}|${q.get('hasta')}|${q.get('sedeId')}`)
      return financiero === 'error'
        ? errorBackend(500, 'Fallo en reportes')
        : HttpResponse.json({ totalIngresos: 0, totalGastos: 0, balanceNeto: 0, cuentasPorCobrar: 0, tratamientosConSaldoPendiente: 0, ...financiero })
    }),
    http.get(`${API}/reportes/pacientes`, ({ request }) => {
      const q = new URL(request.url).searchParams
      contadores.pacientes.push(`${q.get('desde')}|${q.get('hasta')}|${q.get('sedeId')}`)
      return HttpResponse.json({ totalPacientes: 0, pacientesNuevos: 0, ...pacientes })
    }),
  )
}

function sesion(rol: Rol = 'ADMIN_GENERAL') {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Andrea Quispe', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

const panel = (nombre: string) => within(screen.getByRole('article', { name: nombre }))

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

describe('Inicio · Agenda de hoy', { timeout: 20_000 }, () => {
  it('lista las citas del día por hora, sin canceladas ni expiradas, con enlace a la ficha de cada paciente', async () => {
    sesion('ODONTOLOGO')
    api({
      citas: [
        cita({ id: 'c2', pacienteId: 'p-2', pacienteNombre: 'Tania Meza', hora: '11:00:00', estado: 'CONFIRMADA', doctorNombre: 'Dr. Mario Rivas', motivoConsulta: 'Control' }),
        cita({ id: 'c1', pacienteId: 'p-1', pacienteNombre: 'David Cerrón', hora: '09:30:00', estado: 'EN_SALA', doctorNombre: 'Dra. Elena Paucar', motivoConsulta: 'Carillas' }),
        cita({ id: 'c3', pacienteNombre: 'Paciente cancelado', hora: '08:00:00', estado: 'CANCELADA' }),
        cita({ id: 'c4', pacienteNombre: 'Reserva expirada', hora: '08:30:00', estado: 'EXPIRADA' }),
      ],
    })
    await renderRuta('/app')
    const agenda = panel('Agenda de hoy')
    const filas = await agenda.findAllByRole('listitem')
    expect(filas).toHaveLength(2)
    expect(filas[0]).toHaveTextContent('09:30')
    expect(filas[0]).toHaveTextContent('David Cerrón')
    expect(filas[0]).toHaveTextContent('Dra. Elena Paucar · Carillas')
    expect(filas[0]).toHaveTextContent('En sala')
    expect(filas[1]).toHaveTextContent('11:00')
    expect(within(filas[1]).getByText('Confirmada')).toBeInTheDocument()
    expect(agenda.getByRole('link', { name: 'David Cerrón' })).toHaveAttribute('href', '/app/pacientes/p-1')
    expect(agenda.queryByText('Paciente cancelado')).not.toBeInTheDocument()
    expect(agenda.queryByText('Reserva expirada')).not.toBeInTheDocument()
    expect(agenda.getByRole('link', { name: 'Abrir la agenda' })).toHaveAttribute('href', '/app/agenda')
  })

  it('muestra como máximo 8 filas e indica cuántas más hay', async () => {
    sesion('ODONTOLOGO')
    api({ citas: Array.from({ length: 11 }, (_, i) => cita({ id: `c${i}`, pacienteId: `p-${i}`, pacienteNombre: `Paciente ${String(i).padStart(2, '0')}`, hora: `${String(8 + i).padStart(2, '0')}:00:00` })) })
    await renderRuta('/app')
    const agenda = panel('Agenda de hoy')
    expect(await agenda.findAllByRole('listitem')).toHaveLength(8)
    expect(agenda.getByText('y 3 cita(s) más hoy.')).toBeInTheDocument()
  })

  it('un día sin citas lo dice', async () => {
    sesion('ODONTOLOGO')
    api()
    await renderRuta('/app')
    expect(await panel('Agenda de hoy').findByText('No hay citas programadas para hoy en esta sede.')).toBeInTheDocument()
  })

  it('el odontólogo, que no tiene bandeja, va a la agenda', async () => {
    sesion('ODONTOLOGO')
    api()
    await renderRuta('/app')
    expect(await panel('Agenda de hoy').findByRole('link', { name: 'Abrir la agenda' })).toHaveAttribute('href', '/app/agenda')
  })
})

describe('Inicio · Atención requerida', { timeout: 20_000 }, () => {
  it('reúne adelantos por validar, reservas web, recordatorios y stock bajo, cada uno con su enlace', async () => {
    api({
      reservas: [{ id: 'r1' }, { id: 'r2' }],
      tareas: [{ tipo: 'ADELANTO_POR_EXPIRAR' }, { tipo: 'RECORDATORIO_24H' }, { tipo: 'RECORDATORIO_24H' }, { tipo: 'RECORDATORIO_24H' }],
      productos: [
        { id: 'a', nombre: 'Guantes', alertaStockBajo: true },
        { id: 'b', nombre: 'Resina', alertaStockBajo: true },
      ],
    })
    await renderRuta('/app')
    const alertas = panel('Atención requerida')
    expect(await alertas.findByText('1 adelanto(s) Yape por validar')).toBeInTheDocument()
    expect(alertas.getByText('2 reserva(s) web por aceptar')).toBeInTheDocument()
    expect(alertas.getByText('3 recordatorio(s) de mañana por enviar')).toBeInTheDocument()
    expect(alertas.getByRole('button', { name: 'Validar' })).toBeInTheDocument()
    expect(alertas.getByRole('button', { name: 'Revisar' })).toBeInTheDocument()
    expect(alertas.getByRole('button', { name: 'Enviar' })).toBeInTheDocument()
    expect(alertas.getByRole('link', { name: 'Ver inventario' })).toHaveAttribute('href', '/app/inventario')
    expect(alertas.queryByText(/Todo en orden/)).not.toBeInTheDocument()
  })

  it('solo cuenta los productos que el backend marca con stock bajo', async () => {
    api({ productos: [{ id: 'a', alertaStockBajo: true }, { id: 'b', alertaStockBajo: false }, { id: 'c', alertaStockBajo: false }] })
    await renderRuta('/app')
    expect(await panel('Atención requerida').findByText('1 producto(s) con stock bajo')).toBeInTheDocument()
  })

  it('sin pendientes dice "Todo en orden"', async () => {
    api()
    await renderRuta('/app')
    expect(await panel('Atención requerida').findByText('Todo en orden: no hay alertas pendientes.')).toBeInTheDocument()
  })

  it('el odontólogo no consulta la bandeja de tareas (le daría 403 y lo sacaría de la pantalla)', async () => {
    sesion('ODONTOLOGO')
    api({ reservas: [{ id: 'r1' }], tareas: [{ tipo: 'ADELANTO_POR_EXPIRAR' }] })
    await renderRuta('/app')
    const alertas = panel('Atención requerida')
    expect(await alertas.findByText('1 reserva(s) web por aceptar')).toBeInTheDocument()
    expect(alertas.getByRole('link', { name: 'Revisar' })).toHaveAttribute('href', '/app/agenda')
    expect(alertas.queryByText(/adelanto/)).not.toBeInTheDocument()
    expect(contadores.tareas).toBe(0)
  })

  it('si falla una consulta conserva las alertas que sí llegaron y avisa de las que no pudo revisar', async () => {
    api({ reservas: [{ id: 'r1' }], productos: 'error' })
    await renderRuta('/app')
    const alertas = panel('Atención requerida')
    expect(await alertas.findByText('1 reserva(s) web por aceptar')).toBeInTheDocument()
    expect(await alertas.findByRole('alert')).toHaveTextContent('No se pudieron revisar algunas alertas')
    expect(alertas.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument()
  })

  it('con una consulta caída y nada más que mostrar NO afirma que todo está en orden', async () => {
    api({ productos: 'error' })
    await renderRuta('/app')
    expect(await panel('Atención requerida').findByRole('alert')).toHaveTextContent('No se pudieron revisar algunas alertas')
    expect(screen.queryByText(/Todo en orden/)).not.toBeInTheDocument()
  })
})

describe('Inicio · Mes en curso', { timeout: 20_000 }, () => {
  it('muestra ingresos, gastos, balance, por cobrar y pacientes nuevos del 1 del mes a hoy', async () => {
    api({
      financiero: { totalIngresos: 12500.5, totalGastos: 3200, balanceNeto: 9300.5, cuentasPorCobrar: 4100, tratamientosConSaldoPendiente: 3 },
      pacientes: { pacientesNuevos: 17 },
    })
    await renderRuta('/app')
    const mes = panel('Mes en curso')
    expect(await mes.findByText('Del 01/10/2026 al 08/10/2026 · Sede Chilca')).toBeInTheDocument()
    const valor = (etiqueta: string) => mes.getByText(etiqueta).nextElementSibling?.textContent
    expect(valor('Ingresos')).toBe('S/ 12,500.50')
    expect(valor('Gastos')).toBe('S/ 3,200.00')
    expect(valor('Balance neto')).toBe('S/ 9,300.50')
    expect(valor('Por cobrar')).toBe('S/ 4,100.00')
    expect(mes.getByText('3 tratamiento(s) con saldo')).toBeInTheDocument()
    expect(valor('Pacientes nuevos')).toBe('17')
    expect(mes.getByRole('link', { name: 'Ver estadísticas completas' })).toHaveAttribute('href', '/app/estadisticas')
    expect(contadores.financiero).toEqual(['2026-10-01|2026-10-08|sede-chilca'])
    expect(contadores.pacientes).toEqual(['2026-10-01|2026-10-08|sede-chilca'])
  })

  it('un balance negativo se marca en rojo', async () => {
    api({ financiero: { balanceNeto: -250 } })
    await renderRuta('/app')
    const balance = (await panel('Mes en curso').findByText('Balance neto')).nextElementSibling
    expect(balance).toHaveClass('text-error')
    expect(balance).toHaveTextContent('-S/ 250.00')
  })

  it('recepción no ve el panel ni consulta los reportes (le darían 403)', async () => {
    sesion('RECEPCION')
    api()
    await renderRuta('/app')
    await screen.findByRole('heading', { name: 'Operación del día' })
    expect(screen.queryByRole('article', { name: 'Mes en curso' })).not.toBeInTheDocument()
    expect(contadores.financiero).toEqual([])
    expect(contadores.pacientes).toEqual([])
  })

  it('si falla el reporte lo dice y permite reintentar, sin tumbar el resto del inicio', async () => {
    api({ financiero: 'error' })
    await renderRuta('/app')
    const mes = panel('Mes en curso')
    expect(await mes.findByRole('alert')).toHaveTextContent('Fallo en reportes')
    expect(await screen.findByRole('heading', { name: 'Operación del día' })).toBeInTheDocument()
    await userEvent.click(mes.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(contadores.financiero.length).toBeGreaterThan(1))
  })
})
