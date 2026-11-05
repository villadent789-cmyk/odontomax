import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { limpiarSesion } from '@/shared/auth'
import { HttpResponse, http, servidor } from '@/test/msw'
import { RendimientoTab } from './RendimientoTab'
import type { MiAvanceDTO, RendimientoDoctoresDTO } from './types'
import { TarjetaMiAvance } from '../inicio/TarjetaMiAvance'

const API = 'http://localhost:3000/api/v1'
const CHILCA = 'sede-chilca-1'
const CENTRO = 'sede-eltambo'

const SEDES = [
  { id: CHILCA, nombre: 'Villadent Chilca' },
  { id: CENTRO, nombre: 'Villadent Centro' },
]

function respuestaDueno(): RendimientoDoctoresDTO {
  return {
    mes: '2026-10',
    sedeId: null,
    sedes: SEDES,
    totalCobrado: 23000,
    doctores: [
      {
        doctorId: 'doc-a',
        nombre: 'Doctor A',
        cobrado: 20000,
        cobradoPorSede: { [CHILCA]: 12000, [CENTRO]: 8000 },
        metaMensual: 16000,
        porcentajeAvance: 125,
        comisionExcedentePorcentaje: 10,
        comisionEstimada: 400,
        citasAtendidas: 84,
        citasCanceladas: 3,
        citasNoAsistio: 2,
        duracionRealPromedioMin: 38,
        duracionPrevistaPromedioMin: 30,
        porcentajeCitasQuePasaronDelTiempoPrevisto: 31,
        esperaPromedioMin: 9,
      },
      {
        doctorId: 'doc-b',
        nombre: 'Doctor B',
        cobrado: 3000,
        cobradoPorSede: { [CHILCA]: 3000, [CENTRO]: 0 },
        citasAtendidas: 10,
        citasCanceladas: 0,
        citasNoAsistio: 1,
      },
    ],
  }
}

function montar(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('Paso 6 · Rendimiento y meta de doctores', () => {
  beforeEach(() => {
    limpiarSesion()
    _reiniciarCacheSesion()
  })
  afterEach(() => limpiarSesion())

  it('muestra por doctor el cobrado frente a su meta, la comisión estimada y los tiempos', async () => {
    servidor.use(http.get(`${API}/reportes/rendimiento-doctores`, () => HttpResponse.json(respuestaDueno())))
    montar(<RendimientoTab />)

    const tarjeta = within(await screen.findByLabelText('Rendimiento de Doctor A'))
    expect(tarjeta.getByText('S/ 20,000.00')).toBeInTheDocument()
    expect(tarjeta.getByText(/de S\/ 16,000.00/)).toBeInTheDocument()
    expect(tarjeta.getByRole('progressbar', { name: 'Avance de la meta de Doctor A' })).toHaveAttribute('aria-valuenow', '100')
    expect(tarjeta.getByText(/125 % de la meta · meta lograda/)).toBeInTheDocument()
    expect(tarjeta.getByText(/Comisión estimada S\/ 400.00 \(10 % del excedente\)/)).toBeInTheDocument()
    expect(tarjeta.getByText('38 min (prevista 30 min)')).toBeInTheDocument()
    expect(tarjeta.getByText('9 min')).toBeInTheDocument()
    expect(tarjeta.getByText('31 % de las citas')).toBeInTheDocument()
    expect(screen.getByTestId('total-cobrado')).toHaveTextContent('S/ 23,000.00')

    const sinMeta = within(screen.getByLabelText('Rendimiento de Doctor B'))
    expect(sinMeta.getByText('Sin meta definida')).toBeInTheDocument()
    expect(sinMeta.queryByRole('progressbar')).not.toBeInTheDocument()
    expect(sinMeta.queryByText(/Comisión estimada/)).not.toBeInTheDocument()
  })

  it('la meta se mide sin laboratorio: muestra el cobrado de mano de obra y cuánto se descontó', async () => {
    const r = respuestaDueno()
    r.doctores[0] = {
      ...r.doctores[0],
      cobrado: 20000,
      laboratorioDescontado: 5000,
      cobradoParaMeta: 15000,
      porcentajeAvance: 93.8,
      comisionEstimada: 0,
    }
    servidor.use(http.get(`${API}/reportes/rendimiento-doctores`, () => HttpResponse.json(r)))
    montar(<RendimientoTab />)

    const tarjeta = within(await screen.findByLabelText('Rendimiento de Doctor A'))
    expect(tarjeta.getByText('S/ 15,000.00')).toBeInTheDocument()
    expect(tarjeta.getByText(/de S\/ 16,000\.00/)).toBeInTheDocument()
    expect(tarjeta.getByText(/Sin laboratorio: se descontaron S\/ 5,000\.00/)).toBeInTheDocument()
    expect(tarjeta.getByText(/93[.,]8 % de la meta/)).toBeInTheDocument()
  })

  it('no muestra la comisión cuando el servidor no la envía (administrador de sede)', async () => {
    const r = respuestaDueno()
    r.sedes = [SEDES[0]]
    r.doctores = r.doctores.map((d) => ({ ...d, comisionEstimada: null, comisionExcedentePorcentaje: null }))
    servidor.use(http.get(`${API}/reportes/rendimiento-doctores`, () => HttpResponse.json(r)))
    montar(<RendimientoTab />)

    await screen.findByLabelText('Rendimiento de Doctor A')
    expect(screen.queryByText(/Comisión estimada/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Comparar' })).not.toBeInTheDocument()
  })

  it('la vista Comparar pone las sedes en columnas con el total al final', async () => {
    servidor.use(http.get(`${API}/reportes/rendimiento-doctores`, () => HttpResponse.json(respuestaDueno())))
    montar(<RendimientoTab />)
    await screen.findByLabelText('Rendimiento de Doctor A')

    await userEvent.click(screen.getByRole('button', { name: 'Comparar' }))

    const tabla = within(await screen.findByRole('table'))
    expect(tabla.getByRole('columnheader', { name: 'Villadent Chilca' })).toBeInTheDocument()
    expect(tabla.getByRole('columnheader', { name: 'Villadent Centro' })).toBeInTheDocument()
    const filaA = within(tabla.getByText('Doctor A').closest('tr') as HTMLElement)
    expect(filaA.getByText('S/ 12,000.00')).toBeInTheDocument()
    expect(filaA.getByText('S/ 8,000.00')).toBeInTheDocument()
    const total = within(tabla.getByText('Total', { selector: 'td' }).closest('tr') as HTMLElement)
    expect(total.getByText('S/ 15,000.00')).toBeInTheDocument()
    expect(total.getByText('S/ 8,000.00')).toBeInTheDocument()
    expect(total.getByText('S/ 23,000.00')).toBeInTheDocument()
  })

  it('al elegir una sede o cambiar de mes consulta con esos filtros', async () => {
    const consultas: string[] = []
    servidor.use(
      http.get(`${API}/reportes/rendimiento-doctores`, ({ request }) => {
        consultas.push(new URL(request.url).search)
        return HttpResponse.json(respuestaDueno())
      }),
    )
    montar(<RendimientoTab />)
    await screen.findByLabelText('Rendimiento de Doctor A')

    await userEvent.click(screen.getByRole('button', { name: 'Villadent Chilca' }))
    await waitFor(() => expect(consultas.some((q) => q.includes(`sedeId=${CHILCA}`))).toBe(true))

    const mes = screen.getByLabelText('Mes')
    fireEvent.change(mes, { target: { value: '2026-09' } })
    await waitFor(() => expect(consultas.some((q) => q.includes('mes=2026-09'))).toBe(true))
  })

  it('el doctor ve su avance del mes sin comisión', async () => {
    const avance: MiAvanceDTO = {
      mes: '2026-10',
      doctorId: 'doc-a',
      nombre: 'Doctor A',
      cobrado: 14000,
      metaMensual: 20000,
      porcentajeAvance: 70,
      citasAtendidas: 40,
      duracionRealPromedioMin: 35,
      duracionPrevistaPromedioMin: 30,
      esperaPromedioMin: 8,
    }
    let consulta = ''
    servidor.use(
      http.get(`${API}/rendimiento/mi-avance`, ({ request }) => {
        consulta = new URL(request.url).search
        return HttpResponse.json(avance)
      }),
    )
    montar(<TarjetaMiAvance hoy="2026-10-22" />)

    expect(await screen.findByText(/Llevas/)).toHaveTextContent('Llevas S/ 14,000.00 de S/ 20,000.00')
    expect(screen.getByRole('progressbar', { name: 'Avance de tu meta mensual' })).toHaveAttribute('aria-valuenow', '70')
    expect(screen.getByText('70 % de tu meta')).toBeInTheDocument()
    expect(screen.getByText('35 min (prevista 30 min)')).toBeInTheDocument()
    expect(screen.queryByText(/omisi/i)).not.toBeInTheDocument()
    expect(consulta).toBe('?mes=2026-10')
  })
})
