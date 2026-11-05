import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cambiarSedeActiva, guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

const PACIENTE = { id: 'p0000000-0000-4000-8000-00000000000a', dni: '41234567', nombres: 'María', apellidos: 'Quispe Huamán', nombreCompleto: 'María Quispe Huamán', edad: 34, sedeId: 'sede-chilca', alergias: ['Penicilina'], saldoPendiente: 0 }

function api(opciones: { fichaLlena?: boolean; hallazgos?: boolean } = {}) {
  servidor.use(
    http.get(`${API}/pacientes`, () => HttpResponse.json([PACIENTE])),
    http.get(`${API}/pacientes/:id/ficha-ingreso`, () => HttpResponse.json(opciones.fichaLlena ? { actualizadoEn: '2026-10-30T10:00:00Z' } : {})),
    http.get(`${API}/odontograma/pacientes/:id`, () =>
      HttpResponse.json(opciones.hallazgos ? [{ id: 'r1', pacienteId: PACIENTE.id, piezaFdi: 16, estado: 'caries', caras: ['O'], activo: true, registradoEn: '2026-10-30T10:00:00Z' }] : []),
    ),
    http.get(`${API}/servicios`, () => HttpResponse.json([])),
    http.get(`${API}/especialistas`, () => HttpResponse.json([])),
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json([])),
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Dra. Ríos', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Nuevo tratamiento (camino corto)', { timeout: 20_000 }, () => {
  it('con el paciente elegido avisa qué falta (ficha y odontograma) y ofrece plan o presupuesto', async () => {
    api()
    const user = userEvent.setup()
    await renderRuta('/app/nuevo-tratamiento')

    await user.type(await screen.findByRole('searchbox'), 'María')
    await user.click(await screen.findByText(/María Quispe Huamán/))

    expect(await screen.findByRole('heading', { name: 'María Quispe Huamán' })).toBeInTheDocument()
    expect(screen.getByText(/Alergias: Penicilina/)).toBeInTheDocument()
    const pasos = within(screen.getByRole('list', { name: 'Antes de empezar' }))
    expect(await pasos.findByText(/Falta llenar la ficha de ingreso/)).toBeInTheDocument()
    expect(pasos.getByText(/Todavía no hay hallazgos/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear plan de tratamiento' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hacer un presupuesto' })).toBeInTheDocument()
  })

  it('cuando ya tiene ficha y odontograma lo marca como listo y lista los hallazgos', async () => {
    api({ fichaLlena: true, hallazgos: true })
    const user = userEvent.setup()
    await renderRuta('/app/nuevo-tratamiento')

    await user.type(await screen.findByRole('searchbox'), 'María')
    await user.click(await screen.findByText(/María Quispe Huamán/))

    expect(await screen.findByText(/La ficha de ingreso ya se llenó/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(/1 hallazgo\(s\) marcados/)).toBeInTheDocument())
    expect(screen.getByText('Lo que hay en el odontograma')).toBeInTheDocument()
  })

  it('sin paciente elegido ofrece los pacientes de hoy, los presupuestos pendientes y los planes en curso', async () => {
    api()
    servidor.use(
      http.get(`${API}/citas`, () =>
        HttpResponse.json([
          { id: 'c1', pacienteId: PACIENTE.id, pacienteNombre: 'María Quispe Huamán', hora: '09:30:00', estado: 'CONFIRMADA', motivoConsulta: 'Dolor de muela', doctorNombre: 'Dra. Ríos' },
          { id: 'c2', pacienteId: 'p-2', pacienteNombre: 'Cancelada Pérez', hora: '10:00:00', estado: 'CANCELADA' },
        ]),
      ),
      http.get(`${API}/pacientes/:id`, () => HttpResponse.json(PACIENTE)),
      http.get(`${API}/presupuestos/pendientes`, () =>
        HttpResponse.json([{ id: 'pr1', pacienteId: 'p-3', pacienteNombre: 'Luis Meza', total: 510, lineas: 2, creadoEn: '2026-10-28T10:00:00Z' }]),
      ),
      http.get(`${API}/tratamientos/sede/:sedeId`, () =>
        HttpResponse.json([
          { id: 't1', pacienteId: 'p-4', pacienteNombre: 'Rosa Vega', nombre: 'Endodoncia', estado: 'EN_PROCESO', sesionesRealizadas: 1, totalSesiones: 3, saldoPendiente: 200 },
          { id: 't2', pacienteId: 'p-5', pacienteNombre: 'Terminado Sosa', nombre: 'Limpieza', estado: 'FINALIZADO' },
        ]),
      ),
    )
    const user = userEvent.setup()
    await renderRuta('/app/nuevo-tratamiento')

    expect(await screen.findByRole('heading', { name: 'Pacientes de hoy' })).toBeInTheDocument()
    expect(await screen.findByText('Luis Meza')).toBeInTheDocument()
    expect(screen.getByText('Convertir en plan')).toBeInTheDocument()
    expect(await screen.findByText('Rosa Vega')).toBeInTheDocument()
    expect(screen.getByText(/Sesión 1 de 3/)).toBeInTheDocument()
    expect(screen.queryByText('Cancelada Pérez')).not.toBeInTheDocument()
    expect(screen.queryByText('Terminado Sosa')).not.toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: /María Quispe Huamán/ }))
    expect(await screen.findByRole('heading', { name: 'María Quispe Huamán' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear plan de tratamiento' })).toBeInTheDocument()
  })

  it('el administrador general con «Todas» ve las citas, presupuestos y planes de todas las sedes, con su sede', async () => {
    api()
    guardarSesion({
      token: 't',
      usuario: {
        id: 'u-9',
        nombre: 'Dueño',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' },
          { id: 'sede-huancayo', nombre: 'Villadent Huancayo Centro', marca: 'VILLADENT' },
        ],
      },
      sedeActivaId: 'sede-chilca',
    })
    cambiarSedeActiva(null) // «Todas»
    servidor.use(
      http.get(`${API}/citas`, () => HttpResponse.json([{ id: 'c1', pacienteId: 'p-1', pacienteNombre: 'Ana de Hoy', hora: '09:00:00', estado: 'CONFIRMADA', sedeId: 'sede-chilca' }])),
      http.get(`${API}/presupuestos/pendientes`, ({ request }) => {
        const sede = new URL(request.url).searchParams.get('sedeId')
        return HttpResponse.json(sede === 'sede-huancayo' ? [{ id: 'pr1', pacienteId: 'p-3', pacienteNombre: 'Luis de Huancayo', total: 100, lineas: 1, creadoEn: '2026-10-28T10:00:00Z' }] : [])
      }),
      http.get(`${API}/tratamientos/sede/:sedeId`, ({ params }) =>
        HttpResponse.json(params.sedeId === 'sede-chilca' ? [{ id: 't1', pacienteId: 'p-4', pacienteNombre: 'Rosa de Chilca', nombre: 'Endodoncia', estado: 'EN_PROCESO' }] : []),
      ),
    )
    await renderRuta('/app/nuevo-tratamiento')

    expect(await screen.findByText('Ana de Hoy')).toBeInTheDocument()
    expect(await screen.findByText('Luis de Huancayo')).toBeInTheDocument()
    expect(await screen.findByText('Rosa de Chilca')).toBeInTheDocument()
    expect(screen.getAllByText('Villadent Huancayo Centro').length).toBeGreaterThan(0)
    expect(screen.queryByText(/Elige una sede arriba/)).not.toBeInTheDocument()
  })

  it('ofrece a los pacientes con hallazgos por tratar y sin plan, y al tocarlos abre su tratamiento', async () => {
    api()
    servidor.use(
      http.get(`${API}/pacientes/:id`, () => HttpResponse.json(PACIENTE)),
      http.get(`${API}/tratamientos/pacientes-sin-plan`, () =>
        HttpResponse.json([{ pacienteId: PACIENTE.id, pacienteNombre: 'María Quispe Huamán', hallazgos: 3, tipos: ['caries', 'fractura'] }]),
      ),
    )
    const user = userEvent.setup()
    await renderRuta('/app/nuevo-tratamiento')

    expect(await screen.findByRole('heading', { name: 'Con hallazgos y sin plan' })).toBeInTheDocument()
    expect(await screen.findByText('3 hallazgos')).toBeInTheDocument()
    expect(screen.getByText(/Caries dental, Fractura/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /María Quispe Huamán/ }))
    expect(await screen.findByRole('button', { name: 'Crear plan de tratamiento' })).toBeInTheDocument()
  })

  it('recepción no ve el módulo en el menú', async () => {
    guardarSesion({ token: 't', usuario: { id: 'u-2', nombre: 'Katherin', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] }, sedeActivaId: 'sede-chilca' })
    _reiniciarCacheSesion()
    await renderRuta('/app')
    await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(screen.queryByRole('link', { name: 'Nuevo tratamiento' })).not.toBeInTheDocument()
  })
})
