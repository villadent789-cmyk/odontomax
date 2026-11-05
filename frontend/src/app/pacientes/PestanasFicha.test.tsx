import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

const ID = 'p0000000-0000-4000-8000-00000000000a'

function api() {
  servidor.use(
    http.get(`${API}/pacientes/:id`, () =>
      HttpResponse.json({ id: ID, dni: '40111222', nombres: 'Rosa', apellidos: 'Quispe Vera', nombreCompleto: 'Rosa Quispe Vera', sedeId: 'sede-chilca', saldoPendiente: 0, tieneDeuda: false }),
    ),
    http.get(`${API}/citas`, () => HttpResponse.json([])),
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/evoluciones/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/radiografias/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/odontograma/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/plantillas`, () => HttpResponse.json([])),
    http.get(`${API}/pagos/paciente/:id`, () => HttpResponse.json([])),
    http.post(`${API}/historia-clinica/pacientes/:id/apertura`, () => HttpResponse.json({})),
  )
}

async function abrir(tab?: string) {
  await renderRuta(`/app/pacientes/${ID}${tab ? `?tab=${tab}` : ''}`)
  await screen.findByRole('heading', { name: 'Rosa Quispe Vera' })
}

const NOMBRES = ['Resumen', 'Ficha de ingreso', 'Historia Clínica', 'Odontograma', 'Tratamientos y pagos', 'Estudios y consentimientos']

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin Mendoza', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
  api()
})
afterEach(() => limpiarSesion())

describe('Ficha · seis pestañas', { timeout: 20_000 }, () => {
  it('tiene exactamente seis pestañas, sin "Citas" ni pestañas sueltas de pagos, radiografías o consentimientos', async () => {
    await abrir()
    const pestanas = within(screen.getByRole('tablist')).getAllByRole('tab')
    expect(pestanas.map((t) => t.textContent)).toEqual(NOMBRES)
    expect(screen.queryByRole('tab', { name: 'Citas' })).not.toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: /Pagos y Saldo/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('tab', { name: /Radiografías & 3D/ })).not.toBeInTheDocument()
  })

  it('"Tratamientos y pagos" muestra juntos los planes y el historial de cobros', async () => {
    await abrir()
    await userEvent.click(screen.getByRole('tab', { name: 'Tratamientos y pagos' }))
    expect(await screen.findByRole('heading', { name: /Planes de Tratamiento & Presupuestos/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Historial de Pagos & Comprobantes/ })).toBeInTheDocument()
    expect(screen.queryByText(/Estudios Radiológicos/)).not.toBeInTheDocument()
  })

  it('"Estudios y consentimientos" muestra juntas las radiografías y los consentimientos', async () => {
    await abrir()
    await userEvent.click(screen.getByRole('tab', { name: 'Estudios y consentimientos' }))
    expect(await screen.findByText(/Estudios Radiológicos & Imagenología Maxilofacial 3D/)).toBeInTheDocument()
    expect(screen.getByText(/Consentimientos Informados & Protección de Datos/)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Planes de Tratamiento/ })).not.toBeInTheDocument()
  })

  it('cambiar de pestaña muestra solo el contenido de la pestaña elegida', async () => {
    await abrir()
    await userEvent.click(screen.getByRole('tab', { name: 'Historia Clínica' }))
    expect(await screen.findByText(/Notas de Evolución Odontológica/)).toBeInTheDocument()
    expect(screen.queryByText(/Historial de Pagos & Comprobantes/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Resumen' }))
    expect(await screen.findByRole('region', { name: 'Próxima atención programada' })).toBeInTheDocument()
  })

  it.each([
    ['radiografias', 'Estudios y consentimientos'],
    ['consentimientos', 'Estudios y consentimientos'],
    ['finanzas', 'Tratamientos y pagos'],
    ['pagos', 'Tratamientos y pagos'],
    ['tratamientos', 'Tratamientos y pagos'],
    ['clinica', 'Historia Clínica'],
    ['citas', 'Resumen'],
    ['inventada', 'Resumen'],
  ])('los enlaces antiguos siguen funcionando: ?tab=%s abre "%s"', async (tab, esperada) => {
    await abrir(tab)
    expect(screen.getByRole('tab', { name: esperada })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getAllByRole('tab').filter((t) => t.getAttribute('aria-selected') === 'true')).toHaveLength(1)
  })
})
