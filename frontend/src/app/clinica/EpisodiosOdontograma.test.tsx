import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { API, HttpResponse, servidor } from '@/test/msw'
import type { EpisodioOdontograma } from './api-episodios-odontograma'
import { EpisodiosOdontograma } from './EpisodiosOdontograma'
import { HERRAMIENTAS_EN_PALETA, HERRAMIENTAS_ODONTOGRAMA, buscarHerramienta, colorDeEstado, esDePiezaCompleta, esHallazgoRojo } from './herramientasOdontograma'

const PACIENTE = 'p0000000-0000-4000-8000-00000000000a'

const CERRADO: EpisodioOdontograma = {
  id: 'e-1',
  pacienteId: PACIENTE,
  numero: 1,
  motivo: 'Dolor de muela',
  estado: 'CERRADO',
  inicialEn: '2026-06-10T15:00:00Z',
  inicial: [
    { piezaFdi: 16, estado: 'caries', caras: ['O'] },
    { piezaFdi: 26, estado: 'corona_mal_estado' },
  ],
  finalEn: '2026-07-20T15:00:00Z',
  finalPiezas: [
    { piezaFdi: 16, estado: 'restauracion', caras: ['O'] },
    { piezaFdi: 26, estado: 'corona' },
  ],
  notaFinal: 'Sin molestias',
}

function montar() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <EpisodiosOdontograma pacienteId={PACIENTE} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  guardarSesion({
    token: 't',
    usuario: { id: 'u-1', nombre: 'Dra. Ríos', rol: 'ODONTOLOGO', sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
  })
})
afterEach(() => limpiarSesion())

describe('Odontograma inicial y final por episodios', () => {
  it('registra la llegada con su motivo para guardar el odontograma inicial', async () => {
    let cuerpo: unknown
    servidor.use(
      http.get(`${API}/odontograma/pacientes/:id/episodios`, () => HttpResponse.json([])),
      http.post(`${API}/odontograma/pacientes/:id/episodios`, async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json({ ...CERRADO, id: 'e-2', numero: 1, estado: 'ABIERTO', finalEn: undefined, finalPiezas: undefined }, { status: 201 })
      }),
    )
    const user = userEvent.setup()
    montar()

    await user.type(await screen.findByLabelText('Motivo de esta llegada'), 'Dolor de muela')
    await user.click(screen.getByRole('button', { name: 'Registrar llegada (odontograma inicial)' }))

    await waitFor(() => expect(cuerpo).toEqual({ motivo: 'Dolor de muela' }))
  })

  it('con un episodio abierto ofrece el alta y la manda con la evolución', async () => {
    let cuerpo: unknown
    servidor.use(
      http.get(`${API}/odontograma/pacientes/:id/episodios`, () =>
        HttpResponse.json([{ ...CERRADO, estado: 'ABIERTO', finalEn: undefined, finalPiezas: undefined, notaFinal: undefined }]),
      ),
      http.post(`${API}/odontograma/pacientes/:id/episodios/:ep/cerrar`, async ({ request }) => {
        cuerpo = await request.json()
        return HttpResponse.json(CERRADO)
      }),
    )
    const user = userEvent.setup()
    montar()

    expect(await screen.findByText(/Episodio 1 en curso/)).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Evolución al finalizar/), 'Sin dolor')
    await user.click(screen.getByRole('button', { name: /Dar de alta/ }))

    await waitFor(() => expect(cuerpo).toEqual({ nota: 'Sin dolor' }))
  })

  it('compara el inicial con el final y lista solo las piezas que cambiaron', async () => {
    servidor.use(http.get(`${API}/odontograma/pacientes/:id/episodios`, () => HttpResponse.json([CERRADO])))
    const user = userEvent.setup()
    montar()

    await user.click(await screen.findByRole('button', { name: /Comparar fechas/ }))
    const dialogo = within(await screen.findByRole('dialog'))

    expect(dialogo.getByText(/Antes · /)).toBeInTheDocument()
    expect(dialogo.getByText(/Después · /)).toBeInTheDocument()
    const filas = dialogo.getAllByRole('row').slice(1)
    expect(filas).toHaveLength(2)
    expect(within(filas[0]).getByText('Caries dental')).toBeInTheDocument()
    expect(within(filas[0]).getByText('Restauración de resina')).toBeInTheDocument()
    expect(within(filas[1]).getByText('Corona definitiva en mal estado')).toBeInTheDocument()
    expect(within(filas[1]).getByText('Corona definitiva (sin tipo)')).toBeInTheDocument()
  })
})

describe('Símbolos del odontograma según la norma: rojo en mal estado, azul en buen estado', () => {
  it('coronas, endodoncias y restauraciones tienen su versión en mal estado en rojo y en buen estado en azul', () => {
    for (const base of ['corona', 'endodoncia', 'restauracion']) {
      const bueno = buscarHerramienta(base)
      const malo = buscarHerramienta(`${base}_mal_estado`)
      expect(bueno?.grupo).toBe('realizado')
      expect(malo?.grupo).toBe('por_hacer')
      expect(colorDeEstado(base).fill).not.toBe(colorDeEstado(`${base}_mal_estado`).fill)
    }
  })

  it('cada herramienta tiene un id único y las de diente completo no se marcan por cara', () => {
    const ids = HERRAMIENTAS_ODONTOGRAMA.map((h) => h.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(esDePiezaCompleta('corona_mal_estado')).toBe(true)
    expect(esDePiezaCompleta('caries')).toBe(false)
    expect(esDePiezaCompleta('restauracion_mal_estado')).toBe(false)
  })
})

describe('Norma Técnica del Odontograma: solo rojo y azul, con las siglas oficiales', () => {
  const hallazgos = HERRAMIENTAS_ODONTOGRAMA.filter((h) => h.id !== 'sano')

  it('todos los hallazgos son rojos o azules (regla 7)', () => {
    const colores = new Set(hallazgos.map((h) => h.colorHex))
    expect(colores.size).toBe(2)
  })

  it('son rojos la caries, la fractura, el remanente radicular, lo temporal y lo que está en mal estado', () => {
    const rojos = ['caries', 'fractura', 'remanente_radicular', 'restauracion_temporal', 'corona_temporal', 'restauracion_mal_estado', 'corona_mal_estado', 'endodoncia_mal_estado', 'implante_mal_estado', 'protesis_mal_estado', 'aparato_mal_estado']
    for (const id of rojos) expect(esHallazgoRojo(buscarHerramienta(id)!), id).toBe(true)
  })

  it('son azules los tratamientos en buen estado, el diente ausente y todas las anomalías', () => {
    const azules = HERRAMIENTAS_ODONTOGRAMA.filter((h) => h.grupo === 'realizado' || h.grupo === 'anomalia' || h.id === 'ausente')
    const rojosPermitidos = new Set(['mancha_blanca', 'lesion_periapical']) // extras que no son de la norma
    for (const h of azules.filter((x) => !rojosPermitidos.has(x.id))) expect(esHallazgoRojo(h), h.id).toBe(false)
  })

  it('cada hallazgo de la norma lleva su sigla oficial', () => {
    const siglas: Record<string, string> = {
      endodoncia: 'TC', pulpectomia: 'PC', pulpotomia: 'PP', corona_cc: 'CC', corona_cf: 'CF', corona_cmc: 'CMC', corona_cv: 'CV', corona_cj: 'CJ',
      corona_parcial: '3/4', restauracion: 'R', restauracion_am: 'AM', restauracion_iv: 'IV', restauracion_im: 'IM', restauracion_ie: 'IE',
      implante: 'IMP', desgaste: 'DES', discromico: 'DIS', ectopico: 'E', impactacion: 'I', semi_impactacion: 'SI', macrodoncia: 'MAC',
      microdoncia: 'MIC', movilidad_1: 'M1', movilidad_2: 'M2', movilidad_3: 'M3', remanente_radicular: 'RR', supernumerario: 'S',
    }
    for (const [id, sigla] of Object.entries(siglas)) expect(buscarHerramienta(id)?.codigoCorto, id).toBe(sigla)
  })

  it('los registros antiguos sin tipo o sin grado se siguen leyendo pero ya no se ofrecen para marcar', () => {
    for (const id of ['corona', 'movilidad']) {
      expect(buscarHerramienta(id), id).toBeDefined()
      expect(HERRAMIENTAS_EN_PALETA.some((h) => h.id === id), id).toBe(false)
    }
  })
})
