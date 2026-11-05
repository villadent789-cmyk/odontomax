import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion } from '@/shared/auth'
import { API, HttpResponse, servidor } from '@/test/msw'
import { EsperaDeHuecos } from './EsperaDeHuecos'
import { TarjetaCita } from './TarjetaCita'

const ESPERA = {
  id: 'e-1',
  sedeId: 'sede-chilca',
  pacienteId: 'p-1',
  pacienteNombre: 'Rosa Mendoza',
  pacienteTelefono: '987654321',
  motivo: 'Limpieza dental',
  estado: 'ACTIVA',
}

const HUECO = {
  id: 'HUECO-c1-e-1',
  tipo: 'HUECO_DISPONIBLE',
  pacienteNombre: 'Rosa Mendoza',
  motivoConsulta: 'Hueco mañana a las 10:00 · espera: Limpieza dental',
  doctorNombre: 'Doctor A',
  enlaceWhatsapp: 'https://wa.me/51987654321?text=hola',
}

function montar(huecos: unknown[] = []) {
  const cliente = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={cliente}>
      <EsperaDeHuecos sedeId="sede-chilca" huecos={huecos as never} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
  limpiarSesion()
  guardarSesion({
    token: 't',
    usuario: { nombre: 'Recepción', rol: 'RECEPCION', sedes: [{ id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
    negocioActivo: 'VILLADENT',
  })
})

describe('Lista de espera de huecos', () => {
  it('muestra a quien espera y el hueco que se liberó con el botón de WhatsApp', async () => {
    servidor.use(http.get(`${API}/citas/espera-hueco`, () => HttpResponse.json([ESPERA])))
    montar([HUECO])

    expect(await screen.findByText('Limpieza dental')).toBeInTheDocument()
    const oferta = screen.getByRole('link', { name: /Ofrecer por WhatsApp/ })
    expect(oferta.getAttribute('href')).toBe('https://wa.me/51987654321?text=hola')
    expect(screen.getByText(/Hueco mañana a las 10:00/)).toBeInTheDocument()
  })

  it('marca a un paciente como ofrecido', async () => {
    const user = userEvent.setup({ delay: null })
    let cuerpo: Record<string, unknown> | null = null
    servidor.use(
      http.get(`${API}/citas/espera-hueco`, () => HttpResponse.json([ESPERA])),
      http.patch(`${API}/citas/espera-hueco/e-1/estado`, async ({ request }) => {
        cuerpo = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ ...ESPERA, estado: 'OFRECIDA' })
      }),
    )
    montar()

    await user.click(await screen.findByRole('button', { name: 'Marcar ofrecido' }))

    await waitFor(() => expect(cuerpo).toEqual({ estado: 'OFRECIDA' }))
  })

  it('sin nadie esperando lo dice, y el formulario pide elegir al paciente', async () => {
    const user = userEvent.setup({ delay: null })
    servidor.use(http.get(`${API}/citas/espera-hueco`, () => HttpResponse.json([])), http.get(`${API}/especialistas`, () => HttpResponse.json([])))
    montar()

    expect(await screen.findByText('Nadie espera un hueco por ahora.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Agregar paciente' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Agregar' }))

    expect(await screen.findByText('Elige al paciente.')).toBeInTheDocument()
  })
})

describe('Inasistencias en la agenda', () => {
  it('la tarjeta de la cita avisa cuando el paciente tiene 2 o más inasistencias', () => {
    render(<TarjetaCita cita={{ id: 'c-1', pacienteNombre: 'Carlos Soto', estado: 'PROGRAMADA', hora: '10:00', pacienteInasistencias: 3 }} />)
    expect(screen.getByText('3 inasistencias')).toBeInTheDocument()
  })

  it('con una sola inasistencia no avisa', () => {
    render(<TarjetaCita cita={{ id: 'c-1', pacienteNombre: 'Carlos Soto', estado: 'PROGRAMADA', hora: '10:00', pacienteInasistencias: 1 }} />)
    expect(screen.queryByText(/inasistencias/)).not.toBeInTheDocument()
  })
})
