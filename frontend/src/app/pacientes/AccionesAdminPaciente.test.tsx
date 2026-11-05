import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import * as descargas from '@/shared/descargas'
import { API, HttpResponse, errorBackend, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { PacienteDTO } from './api-pacientes'

vi.mock('@/shared/descargas', async (original) => ({
  ...(await original<typeof import('@/shared/descargas')>()),
  descargarArchivo: vi.fn(),
}))

const PACIENTE: PacienteDTO = {
  id: 'p0000000-0000-4000-8000-000000000009',
  dni: '45908123',
  nombres: 'Luciana',
  apellidos: 'Meza Cárdenas',
  nombreCompleto: 'Luciana Meza Cárdenas',
  sedeId: 'sede-chilca',
}

let eliminados: string[] = []
let archivados: string[] = []
let user: ReturnType<typeof userEvent.setup>

function api(opciones: { falla?: 'eliminar' | 'exportar' } = {}) {
  eliminados = []
  archivados = []
  servidor.use(
    http.get(`${API}/pacientes/:id/exportar`, () => {
      if (opciones.falla === 'exportar') return errorBackend(500, 'No se pudo armar el expediente')
      return HttpResponse.json({ datosPersonales: PACIENTE, avisoLegal: 'Ley 29733', citas: [], pagos: [] })
    }),
    http.get(`${API}/pacientes/:id`, () => HttpResponse.json(PACIENTE)),
    http.get(`${API}/pacientes`, () => HttpResponse.json([PACIENTE])),
    http.delete(`${API}/pacientes/:id`, ({ request, params }) => {
      if (opciones.falla === 'eliminar') {
        return errorBackend(409, 'No se puede eliminar permanentemente el paciente porque cuenta con registros protegidos')
      }
      const q = new URL(request.url).searchParams
      eliminados.push(`${params.id}:${q.get('confirmacion')}:${q.get('motivo')}`)
      return HttpResponse.json({ status: 200, mensaje: 'Eliminado' })
    }),
    http.post(`${API}/pacientes/:id/archivar`, async ({ request, params }) => {
      const cuerpo = (await request.json()) as { motivo?: string }
      archivados.push(`${params.id}:${cuerpo.motivo}`)
      return HttpResponse.json({ mensaje: 'Paciente archivado con éxito conservando su historia clínica intacta' })
    }),
    http.get(`${API}/evoluciones/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/odontograma/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/radiografias/paciente/:id`, () => HttpResponse.json([])),
    http.get(`${API}/consentimientos/pacientes/:id`, () => HttpResponse.json([])),
    http.get(`${API}/tratamientos/paciente/:id`, () => HttpResponse.json([])),
  )
}

function iniciarSesion(rol: 'ADMIN_GENERAL' | 'RECEPCION') {
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Usuario', rol, sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
}

async function abrirFicha() {
  await renderRuta(`/app/pacientes/${PACIENTE.id}`)
  await screen.findByRole('heading', { name: 'Luciana Meza Cárdenas' })
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  vi.mocked(descargas.descargarArchivo).mockClear()
  user = userEvent.setup({ delay: null })
})
afterEach(() => limpiarSesion())

describe('Ficha del paciente · acciones del administrador', { timeout: 20_000 }, () => {
  it('recepción no ve exportar ni eliminar', async () => {
    iniciarSesion('RECEPCION')
    api()
    await abrirFicha()
    expect(screen.queryByRole('button', { name: /Exportar expediente/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Eliminar paciente/ })).not.toBeInTheDocument()
  })

  it('exportar descarga el expediente completo como JSON', async () => {
    iniciarSesion('ADMIN_GENERAL')
    api()
    await abrirFicha()
    await user.click(screen.getByRole('button', { name: /Exportar expediente/ }))
    await waitFor(() => expect(descargas.descargarArchivo).toHaveBeenCalledTimes(1), { timeout: 5000 })
    const [archivo, nombre] = vi.mocked(descargas.descargarArchivo).mock.calls[0]
    expect(nombre).toBe('expediente-luciana-meza-cardenas.json')
    expect(JSON.parse(await (archivo as Blob).text())).toMatchObject({ avisoLegal: 'Ley 29733', datosPersonales: { dni: '45908123' } })
  })

  it('si la exportación falla muestra el motivo y no descarga nada', async () => {
    iniciarSesion('ADMIN_GENERAL')
    api({ falla: 'exportar' })
    await abrirFicha()
    await user.click(screen.getByRole('button', { name: /Exportar expediente/ }))
    expect(await screen.findByText('No se pudo exportar el expediente')).toBeInTheDocument()
    expect(screen.getByText('No se pudo armar el expediente')).toBeInTheDocument()
    expect(descargas.descargarArchivo).not.toHaveBeenCalled()
  })

  it('eliminar exige elegir un motivo y lo envía al backend junto con el detalle; luego vuelve al directorio', async () => {
    iniciarSesion('ADMIN_GENERAL')
    api()
    await abrirFicha()
    await user.click(screen.getByRole('button', { name: /Eliminar paciente/ }))
    const d = within(await screen.findByRole('dialog', { name: '¿Eliminar a Luciana Meza Cárdenas?' }))
    expect(d.getByText(/solo se archiva/)).toBeInTheDocument()
    expect(d.queryByLabelText(/escribe ELIMINAR/)).not.toBeInTheDocument()
    // Las cuatro opciones rápidas.
    const opciones = within(d.getByLabelText('Motivo de eliminación')).getAllByRole('option').map((o) => o.textContent)
    expect(opciones).toEqual([
      'Elige un motivo…',
      'Ficha duplicada / creada por error',
      'Ficha de prueba o capacitación',
      'Paciente solicitó anulación de registro',
      'Otro motivo (especificar)',
    ])

    // Sin motivo no se llama al backend.
    await user.click(d.getByRole('button', { name: 'Eliminar definitivamente' }))
    expect(d.getByText('Elige el motivo.')).toBeInTheDocument()
    expect(eliminados).toEqual([])

    await user.selectOptions(d.getByLabelText('Motivo de eliminación'), 'Ficha duplicada / creada por error')
    await user.type(d.getByLabelText('Detalle adicional (opcional)'), 'Existe otra ficha con el mismo DNI')
    await user.click(d.getByRole('button', { name: 'Eliminar definitivamente' }))
    await waitFor(() =>
      expect(eliminados).toEqual([`${PACIENTE.id}:true:Ficha duplicada / creada por error: Existe otra ficha con el mismo DNI`]),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: 'Luciana Meza Cárdenas', level: 1 })).not.toBeInTheDocument()
  })

  it('"Otro motivo" obliga a escribir el detalle y se envía solo ese texto', async () => {
    iniciarSesion('ADMIN_GENERAL')
    api()
    await abrirFicha()
    await user.click(screen.getByRole('button', { name: /Eliminar paciente/ }))
    const d = within(await screen.findByRole('dialog'))
    await user.selectOptions(d.getByLabelText('Motivo de eliminación'), 'Otro motivo (especificar)')
    await user.click(d.getByRole('button', { name: 'Eliminar definitivamente' }))
    expect(d.getByText('Describe el motivo (al menos 5 caracteres).')).toBeInTheDocument()
    expect(eliminados).toEqual([])

    await user.type(d.getByLabelText('Detalle del motivo'), 'Cargada con el DNI de otra persona')
    await user.click(d.getByRole('button', { name: 'Eliminar definitivamente' }))
    await waitFor(() => expect(eliminados).toEqual([`${PACIENTE.id}:true:Cargada con el DNI de otra persona`]))
  })

  it('si el paciente ya tiene historia o pagos (409), explica y ofrece "Archivar ficha" con el mismo motivo', async () => {
    iniciarSesion('ADMIN_GENERAL')
    api({ falla: 'eliminar' })
    await abrirFicha()
    await user.click(screen.getByRole('button', { name: /Eliminar paciente/ }))
    const d = within(await screen.findByRole('dialog'))
    await user.selectOptions(d.getByLabelText('Motivo de eliminación'), 'Ficha de prueba o capacitación')
    await user.click(d.getByRole('button', { name: 'Eliminar definitivamente' }))

    expect(await d.findByText('Esta ficha ya tiene historia y no se puede eliminar')).toBeInTheDocument()
    expect(d.getByText(/registros protegidos/)).toBeInTheDocument()
    expect(d.queryByRole('button', { name: 'Eliminar definitivamente' })).not.toBeInTheDocument()
    expect(archivados).toEqual([])

    await user.click(d.getByRole('button', { name: 'Archivar ficha' }))
    await waitFor(() => expect(archivados).toEqual([`${PACIENTE.id}:Ficha de prueba o capacitación`]))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('cancelar no elimina nada', async () => {
    iniciarSesion('ADMIN_GENERAL')
    api()
    await abrirFicha()
    await user.click(screen.getByRole('button', { name: /Eliminar paciente/ }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(eliminados).toEqual([])
    expect(archivados).toEqual([])
  })
})
