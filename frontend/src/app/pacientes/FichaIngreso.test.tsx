import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { FichaIngreso } from './api-ficha-ingreso'

const ID = 'p0000000-0000-4000-8000-00000000000a'

function api(paciente: Record<string, unknown> = {}) {
  servidor.use(
    http.get(`${API}/pacientes/:id`, () =>
      HttpResponse.json({ id: ID, dni: '40111222', nombres: 'Rosa', apellidos: 'Quispe Vera', nombreCompleto: 'Rosa Quispe Vera', edad: 34, sedeId: 'sede-chilca', saldoPendiente: 0, tieneDeuda: false, ...paciente }),
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

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin Mendoza', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Historia clínica general (ficha de ingreso) y foto del paciente', { timeout: 20_000 }, () => {
  it('muestra la ficha ordenada por secciones y guarda lo que se completó, con parentesco y teléfono de emergencia', async () => {
    api()
    let guardada: FichaIngreso | undefined
    servidor.use(
      http.get(`${API}/pacientes/:id/ficha-ingreso`, () => HttpResponse.json({})),
      http.put(`${API}/pacientes/:id/ficha-ingreso`, async ({ request }) => {
        guardada = (await request.json()) as FichaIngreso
        return HttpResponse.json({ ...guardada, actualizadoEn: '2026-10-30T10:00:00Z', actualizadoPorNombre: 'Katherin Mendoza' })
      }),
    )
    const user = userEvent.setup()
    await renderRuta(`/app/pacientes/${ID}?tab=ficha`)

    const formulario = within(await screen.findByRole('form', { name: 'Historia clínica general' }))
    for (const titulo of ['Datos personales', 'En caso de emergencia', 'Apoderado (si es menor de edad)', 'Historial de enfermedades', 'Examen clínico y estomatológico']) {
      expect(formulario.getByText(titulo)).toBeInTheDocument()
    }
    // Es una persona adulta: la sección de pediatría no estorba.
    expect(formulario.queryByText('Pediatría')).not.toBeInTheDocument()

    await user.type(formulario.getByLabelText('Nombre del contacto'), 'Marta Vera')
    await user.type(formulario.getByLabelText('Teléfono del contacto'), '964123456')
    await user.selectOptions(formulario.getByLabelText('Parentesco'), 'Madre')
    await user.click(formulario.getByLabelText('Bruxismo'))
    await user.click(formulario.getByRole('button', { name: 'Guardar historia clínica general' }))

    await waitFor(() =>
      expect(guardada).toMatchObject({ emergenciaNombre: 'Marta Vera', emergenciaTelefono: '964123456', emergenciaParentesco: 'Madre', bruxismo: true }),
    )
    expect(await screen.findByText(/Última actualización/)).toHaveTextContent('Katherin Mendoza')
  })

  it('para un niño aparece la sección de pediatría', async () => {
    api({ edad: 6 })
    servidor.use(http.get(`${API}/pacientes/:id/ficha-ingreso`, () => HttpResponse.json({})))
    await renderRuta(`/app/pacientes/${ID}?tab=ficha`)

    const formulario = within(await screen.findByRole('form', { name: 'Historia clínica general' }))
    expect(formulario.getByText('Pediatría')).toBeInTheDocument()
  })

  it('sin foto muestra las iniciales y con foto la pide al servidor', async () => {
    api({ fotoActualizadaEn: '2026-10-29T12:00:00Z' })
    let pidioFoto = false
    servidor.use(
      http.get(`${API}/pacientes/:id/ficha-ingreso`, () => HttpResponse.json({})),
      http.get(`${API}/pacientes/:id/foto`, () => {
        pidioFoto = true
        return new HttpResponse(new Uint8Array([0xff, 0xd8, 0xff, 0xe0]), { headers: { 'Content-Type': 'image/jpeg' } })
      }),
    )
    URL.createObjectURL = () => 'blob:foto-de-prueba'
    await renderRuta(`/app/pacientes/${ID}`)

    expect(await screen.findByRole('img', { name: 'Foto de Rosa Quispe Vera' })).toHaveAttribute('src', 'blob:foto-de-prueba')
    expect(pidioFoto).toBe(true)
  })
})
