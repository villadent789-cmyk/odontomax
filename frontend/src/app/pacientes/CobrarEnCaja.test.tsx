import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { PacienteDTO } from './api-pacientes'

const PACIENTE: PacienteDTO = {
  id: 'p0000000-0000-4000-8000-000000000077',
  dni: '45908123',
  nombres: 'Carlos',
  apellidos: 'Huamán',
  nombreCompleto: 'Carlos Huamán',
  sedeId: 'sede-chilca',
  tieneDeuda: false,
  saldoPendiente: 0,
}

function api(paciente: PacienteDTO, tratamientos: unknown[]) {
  servidor.use(
    http.get(`${API}/pacientes/:id`, () => HttpResponse.json(paciente)),
    http.get(`${API}/pacientes`, () => HttpResponse.json([paciente])),
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json(tratamientos)),
    http.get(`${API}/pagos/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/evoluciones/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/odontograma/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/radiografias/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/plantillas`, () => HttpResponse.json([])),
    http.post(`${API}/historia-clinica/pacientes/:id/apertura`, () => HttpResponse.json({})),
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Katherin', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

async function abrirFicha() {
  await renderRuta(`/app/pacientes/${PACIENTE.id}`)
  await screen.findByRole('heading', { name: 'Carlos Huamán', level: 1 })
}

describe('Ficha · botón "Cobrar en Caja"', { timeout: 20_000 }, () => {
  it('paciente nuevo sin tratamientos ni deuda: permite cobrar una consulta', async () => {
    api(PACIENTE, [])
    await abrirFicha()
    const boton = await screen.findByRole('button', { name: 'Cobrar en Caja' })
    expect(boton).toBeEnabled()
    await userEvent.click(boton)
    expect(await screen.findByRole('dialog', { name: 'Registrar cobro' })).toBeInTheDocument()
    expect(await screen.findByRole('region', { name: 'Cobrar consulta o procedimiento' })).toBeInTheDocument()
  })

  it('con un plan de tratamiento creado se puede cobrar', async () => {
    api(PACIENTE, [{ id: 't1', pacienteId: PACIENTE.id, nombre: 'Limpieza', estado: 'PLANIFICADO', costoTotal: 80, saldoPendiente: 80 }])
    await abrirFicha()
    expect(await screen.findByText(/Limpieza/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cobrar en Caja' })).toBeEnabled()
    expect(screen.queryByText('El paciente no tiene tratamientos ni saldos pendientes por cobrar')).not.toBeInTheDocument()
  })

  it('sin tratamientos pero con saldo pendiente también se puede cobrar', async () => {
    api({ ...PACIENTE, tieneDeuda: true, saldoPendiente: 120 }, [])
    await abrirFicha()
    // Se espera a que termine la carga de tratamientos antes de comprobar el botón.
    await screen.findByRole('tab', { name: 'Resumen' })
    expect(screen.getByRole('button', { name: 'Cobrar en Caja' })).toBeEnabled()
    expect(screen.queryByText('El paciente no tiene tratamientos ni saldos pendientes por cobrar')).not.toBeInTheDocument()
  })
})
