import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRootRoute, createRouter } from '@tanstack/react-router'
import { act, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import type { ComisionRadiologicaDTO } from '../radiologia/types'
import { VillamaxTab } from './VillamaxTab'
import type { ReporteFinancieroDTO } from './types'

const API = 'http://localhost:3000/api/v1'
const SEDE = 'sede-villamax-chilca'

function comision(extra: Partial<ComisionRadiologicaDTO>): ComisionRadiologicaDTO {
  return {
    id: crypto.randomUUID(),
    doctorDerivadorId: 'd-1',
    doctorDerivadorNombre: 'Dr. Palomino',
    pacienteNombre: 'Luciana Meza',
    tipoEstudio: 'Radiografía panorámica',
    precioCobrado: 60,
    montoComision: 6,
    estado: 'PENDIENTE',
    fechaEmision: '2026-10-10T15:00:00Z',
    sedeId: SEDE,
    creadoEn: '2026-10-10T15:00:00Z',
    ...extra,
  }
}

const FINANCIERO = {
  totalIngresos: 410,
  totalGastos: 12,
  balanceNeto: 398,
  ingresosPorTratamiento: [
    { procedimientoNombre: 'Radiografía panorámica', totalIngresos: 120, cantidadRealizados: 2 },
    { procedimientoNombre: 'Tomografía 3D Cone Beam', totalIngresos: 290, cantidadRealizados: 1 },
  ],
} as ReporteFinancieroDTO

async function montar(financiero: ReporteFinancieroDTO | undefined = FINANCIERO) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  // El componente usa un Link: necesita un router, aunque la ruta sea la raíz.
  const router = createRouter({
    routeTree: createRootRoute({
      component: () => (
        <QueryClientProvider client={queryClient}>
          <VillamaxTab financiero={financiero} desde="2026-10-01" hasta="2026-10-31" />
        </QueryClientProvider>
      ),
    }),
    history: createMemoryHistory({ initialEntries: ['/'] }),
  })
  render(<RouterProvider router={router} />)
  await act(() => router.load())
}

describe('Resumen de Villamax para los dueños', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
    guardarSesion({
      token: 't',
      usuario: {
        nombre: 'Andrea',
        rol: 'ADMIN_GENERAL',
        sedes: [
          { id: 'sede-chilca-1', nombre: 'Villadent Chilca', marca: 'VILLADENT' },
          { id: SEDE, nombre: 'Villamax Chilca', marca: 'VILLAMAX' },
        ],
      },
      sedeActivaId: SEDE,
      negocioActivo: 'VILLAMAX',
    })
    servidor.use(http.get(`${API}/caja/activa`, () => new HttpResponse(null, { status: 204 })))
  })
  afterEach(() => limpiarSesion())

  it('suma los estudios cobrados y muestra los ingresos y el balance del reporte financiero', async () => {
    servidor.use(http.get(`${API}/radiologia/comisiones`, () => HttpResponse.json([])))
    await montar()

    expect(await screen.findByText('Estudios realizados')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('S/ 410.00')).toBeInTheDocument()
    expect(screen.getByText(/Egresos S\/ 12\.00 · balance S\/ 398\.00/)).toBeInTheDocument()
    const fila = within(screen.getByText('Tomografía 3D Cone Beam').closest('tr') as HTMLElement)
    expect(fila.getByText('S/ 290.00')).toBeInTheDocument()
  })

  it('agrupa por doctor las derivaciones del periodo y deja fuera las de otras fechas', async () => {
    servidor.use(
      http.get(`${API}/radiologia/comisiones`, () =>
        HttpResponse.json([
          comision({ montoComision: 6, estado: 'PENDIENTE' }),
          comision({ montoComision: 25, estado: 'LIQUIDADO' }),
          comision({ doctorDerivadorId: 'd-2', doctorDerivadorNombre: 'Dra. Huamán', montoComision: 10 }),
          comision({ fechaEmision: '2026-09-02T15:00:00Z', montoComision: 99 }),
        ]),
      ),
    )
    await montar()

    const palomino = within((await screen.findByText('Dr. Palomino')).closest('tr') as HTMLElement)
    expect(palomino.getByText('2')).toBeInTheDocument()
    expect(palomino.getAllByText('S/ 31.00').length).toBeGreaterThan(0)
    expect(palomino.getByText('S/ 6.00')).toBeInTheDocument()
    expect(palomino.getByText('S/ 25.00')).toBeInTheDocument()
    const huaman = within(screen.getByText('Dra. Huamán').closest('tr') as HTMLElement)
    expect(huaman.getByText('1')).toBeInTheDocument()
    // La comisión de septiembre no entra en el periodo, pero su deuda sí cuenta en "por pagar".
    expect(screen.getByText('S/ 41.00')).toBeInTheDocument()
    expect(screen.getByText('S/ 115.00')).toBeInTheDocument()
  })

  it('el administrador de sede ve estudios e ingresos pero no las comisiones ni los doctores que derivan', async () => {
    let pidioComisiones = false
    servidor.use(
      http.get(`${API}/radiologia/comisiones`, () => {
        pidioComisiones = true
        return HttpResponse.json([])
      }),
    )
    guardarSesion({
      token: 't',
      usuario: {
        nombre: 'Luis',
        rol: 'ADMIN_SEDE',
        sedes: [{ id: SEDE, nombre: 'Villamax Chilca', marca: 'VILLAMAX' }],
      },
      sedeActivaId: SEDE,
      negocioActivo: 'VILLAMAX',
    })
    await montar()

    expect(await screen.findByText('Estudios realizados')).toBeInTheDocument()
    expect(screen.queryByText('Comisiones del periodo')).not.toBeInTheDocument()
    expect(screen.queryByText('Por pagar a doctores')).not.toBeInTheDocument()
    expect(screen.queryByText('Doctores que derivan')).not.toBeInTheDocument()
    expect(pidioComisiones).toBe(false)
  })

  it('sin derivaciones ni estudios muestra los mensajes vacíos', async () => {
    servidor.use(http.get(`${API}/radiologia/comisiones`, () => HttpResponse.json([])))
    await montar({ ...FINANCIERO, ingresosPorTratamiento: [] })

    expect(await screen.findByText('Sin estudios cobrados en este periodo.')).toBeInTheDocument()
    expect(screen.getByText('Ningún doctor derivó pacientes en este periodo.')).toBeInTheDocument()
  })
})
