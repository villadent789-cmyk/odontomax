import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { cambiarSedeActiva, guardarSesion, limpiarSesion, type Rol, type Sesion } from '@/shared/auth'
import { servidor } from '@/test/msw'

const SEDES = [
  { id: 'sede-chilca-1', nombre: 'Villadent Chilca', marca: 'VILLADENT' as const },
  { id: 'sede-huancayo', nombre: 'Villadent Huancayo Centro', marca: 'VILLADENT' as const },
  { id: 'sede-radiologia', nombre: 'Villamax Chilca', marca: 'VILLAMAX' as const },
]

function sesionDe(rol: Rol, sedeActivaId: string | null = null): Sesion {
  return {
    token: 'token-test',
    usuario: {
      nombre: 'Carlos Morales',
      rol,
      sedes: SEDES,
    },
    sedeActivaId,
  }
}

async function renderEn(ruta: string) {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
  return router
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
  _reiniciarCacheSesion()
})
afterEach(() => limpiarSesion())

describe('InicioPage · Vista consolidada Todas las sedes', () => {
  it('muestra el pulso operativo consolidado y todos los paneles cuando sedeActivaId es null (Todas)', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', 'sede-chilca-1'))
    cambiarSedeActiva(null)
    await renderEn('/app')

    // Saludo y subtitulo consolidado
    expect(await screen.findByRole('heading', { name: /Carlos/i })).toBeInTheDocument()
    expect(screen.getByText(/Resumen operativo consolidado de/i)).toBeInTheDocument()
    expect(screen.getByText('todas las sedes')).toBeInTheDocument()

    // Las 3 tarjetas de pulso del día están visibles sin importar que sedeId sea null
    expect(screen.getByRole('region', { name: 'Pulso del día' })).toBeInTheDocument()
    expect(screen.getByText('Citas de hoy (todas las sedes)')).toBeInTheDocument()
    expect(screen.getByText('Caja y turnos')).toBeInTheDocument()
    expect(screen.getByText('Equipo clínico de la red')).toBeInTheDocument()

    // Los paneles de operación de hoy están visibles
    expect(screen.getByRole('region', { name: 'Operación del día' })).toBeInTheDocument()
    expect(screen.getByText('Atención requerida')).toBeInTheDocument()

    // Panel del mes en curso consolidado
    expect(screen.getByRole('article', { name: 'Mes en curso' })).toBeInTheDocument()
    expect(screen.getByText(/Red Odontológica \(todas las sedes\)/i)).toBeInTheDocument()
  })

  it('muestra la sede específica cuando hay una sede activa seleccionada', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', 'sede-chilca-1'))
    await renderEn('/app')

    expect(await screen.findByRole('heading', { name: /Carlos/i })).toBeInTheDocument()
    expect(screen.getByText(/Así va el día en/i)).toBeInTheDocument()
    expect(screen.getAllByText('Villadent Chilca').length).toBeGreaterThan(0)
    expect(screen.getByText('Citas del día')).toBeInTheDocument()
    expect(screen.getByText('Caja y turno de la sede')).toBeInTheDocument()
    expect(screen.getByText('Equipo clínico en sede')).toBeInTheDocument()
  })
})

describe('InicioPage · Caja según el rol', () => {
  it('el odontólogo no consulta ni ve la caja de la sede (trae cobros y totales de todos los doctores)', async () => {
    const rutasPedidas: string[] = []
    const registrar = ({ request }: { request: Request }) => {
      rutasPedidas.push(new URL(request.url).pathname)
    }
    servidor.events.on('request:start', registrar)
    try {
      guardarSesion(sesionDe('ODONTOLOGO', 'sede-chilca-1'))
      await renderEn('/app')

      expect(await screen.findByText('Citas del día')).toBeInTheDocument()
      expect(screen.getByText('Equipo clínico en sede')).toBeInTheDocument()
      expect(screen.queryByText('Caja y turno de la sede')).not.toBeInTheDocument()
      expect(rutasPedidas.filter((ruta) => ruta.includes('/caja'))).toEqual([])
    } finally {
      servidor.events.removeListener('request:start', registrar)
    }
  })

  it('recepción sí ve la tarjeta de caja de la sede', async () => {
    guardarSesion(sesionDe('RECEPCION', 'sede-chilca-1'))
    await renderEn('/app')

    expect(await screen.findByText('Caja y turno de la sede')).toBeInTheDocument()
  })
})
