import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import { http } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import { useCambiarEstado } from './agenda/api-agenda'
import { useAnularPagoMutation, useRegistrarPagoMutation } from './caja/api-caja'
import { useCrearPacienteMutation, useEliminarPaciente, type PacienteDTO } from './pacientes/api-pacientes'

/** Tras cada acción las listas en pantalla se actualizan solas, sin recargar la página con F5. */

const ANA: PacienteDTO = { id: 'p-ana', nombreCompleto: 'Ana Quispe', dni: '11111111', sedeId: 'sede-chilca' }
const NUEVO: PacienteDTO = { id: 'p-nuevo', nombreCompleto: 'Carlos Huamán', dni: '22222222', sedeId: 'sede-chilca' }

let cliente: QueryClient
const envoltorio = ({ children }: { children: ReactNode }) => <QueryClientProvider client={cliente}>{children}</QueryClientProvider>

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Andrea', rol: 'ADMIN_GENERAL', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
  cliente = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } })
})
afterEach(() => limpiarSesion())

/** Claves que la acción mandó refrescar. */
function claveInvalidadas() {
  const espia = vi.spyOn(cliente, 'invalidateQueries')
  return () => espia.mock.calls.map(([filtro]) => JSON.stringify(filtro?.queryKey))
}

describe('Actualización sin recargar', () => {
  it('un paciente nuevo aparece al instante en la lista sin filtro, no en una búsqueda que no lo incluye', async () => {
    servidor.use(http.post(`${API}/pacientes`, () => HttpResponse.json(NUEVO)))
    cliente.setQueryData(['pacientes', 'lista', {}], [ANA])
    cliente.setQueryData(['pacientes', 'lista', { busqueda: 'ana' }], [ANA])
    cliente.setQueryData(['pacientes', 'lista', { sedeId: 'sede-centro' }], [])

    const { result } = renderHook(() => useCrearPacienteMutation(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ dni: '22222222', nombres: 'Carlos', apellidos: 'Huamán', sedeId: 'sede-chilca' } as never))

    expect(cliente.getQueryData<PacienteDTO[]>(['pacientes', 'lista', {}])?.map((p) => p.id)).toEqual(['p-nuevo', 'p-ana'])
    expect(cliente.getQueryData<PacienteDTO[]>(['pacientes', 'lista', { busqueda: 'ana' }])?.map((p) => p.id)).toEqual(['p-ana'])
    expect(cliente.getQueryData<PacienteDTO[]>(['pacientes', 'lista', { sedeId: 'sede-centro' }])).toEqual([])
  })

  it('un paciente eliminado sale al instante de las listas', async () => {
    servidor.use(http.delete(`${API}/pacientes/:id`, () => HttpResponse.json({ mensaje: 'ok' })))
    cliente.setQueryData(['pacientes', 'lista', {}], [ANA, NUEVO])

    const { result } = renderHook(() => useEliminarPaciente(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ id: 'p-ana', motivo: 'Ficha duplicada / creada por error' }))

    expect(cliente.getQueryData<PacienteDTO[]>(['pacientes', 'lista', {}])?.map((p) => p.id)).toEqual(['p-nuevo'])
  })

  it('cobrar refresca caja, pagos, saldo del paciente, tratamientos y reportes del mes', async () => {
    servidor.use(http.post(`${API}/pagos`, () => HttpResponse.json({ id: 'pg-1', pacienteId: 'p-ana', monto: 50 })))
    const invalidadas = claveInvalidadas()

    const { result } = renderHook(() => useRegistrarPagoMutation(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ pacienteId: 'p-ana', sedeId: 'sede-chilca', monto: 50, metodoPago: 'EFECTIVO' } as never))

    expect(invalidadas()).toEqual(expect.arrayContaining(['["caja"]', '["pagos"]', '["pacientes"]', '["tratamientos"]', '["reportes"]']))
  })

  it('anular un comprobante refresca lo mismo que cobrar (también la ficha del paciente)', async () => {
    servidor.use(http.post(`${API}/pagos/:id/anular`, () => HttpResponse.json({ id: 'pg-1', pacienteId: 'p-ana', anulado: true })))
    const invalidadas = claveInvalidadas()

    const { result } = renderHook(() => useAnularPagoMutation(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ id: 'pg-1', motivo: 'Cobro duplicado' }))

    expect(invalidadas()).toEqual(expect.arrayContaining(['["caja"]', '["pagos"]', '["pacientes"]', '["tratamientos"]', '["reportes"]']))
  })

  it('atender una cita refresca la agenda y el directorio de pacientes (última atención)', async () => {
    servidor.use(http.patch(`${API}/citas/:id/estado`, () => HttpResponse.json({ id: 'c-1', estado: 'ATENDIDA' })))
    const invalidadas = claveInvalidadas()

    const { result } = renderHook(() => useCambiarEstado(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ id: 'c-1', estado: 'ATENDIDA' }))

    expect(invalidadas()).toEqual(expect.arrayContaining(['["citas"]', '["pacientes"]']))
    // Sin enlace a un plan no hace falta refrescar los tratamientos.
    expect(invalidadas()).not.toContain('["tratamientos"]')
  })

  it('atender una cita que es sesión de un plan refresca también los tratamientos', async () => {
    servidor.use(http.patch(`${API}/citas/:id/estado`, () => HttpResponse.json({ id: 'c-1', estado: 'ATENDIDA', tratamientoId: 't-orto' })))
    const invalidadas = claveInvalidadas()

    const { result } = renderHook(() => useCambiarEstado(), { wrapper: envoltorio })
    await act(() => result.current.mutateAsync({ id: 'c-1', estado: 'ATENDIDA' }))

    expect(invalidadas()).toContain('["tratamientos"]')
  })
})
