import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion, obtenerSesion, type Rol, type Sesion } from '@/shared/auth'
import { http } from 'msw'
import { API, HttpResponse, manejadores, servidor } from '@/test/msw'
import { agenda } from '@/test/msw-agenda'

const SEDES = [
  { id: 'sede-1', nombre: 'Villadent Chilca', marca: 'VILLADENT' as const },
  { id: 'sede-2', nombre: 'Huancayo Centro', marca: 'VILLADENT' as const },
  { id: 'sede-3', nombre: 'Villamax Chilca', marca: 'VILLAMAX' as const },
]

function sesionDe(rol: Rol, sedes = SEDES.slice(0, 1)): Sesion {
  return { token: 't', usuario: { nombre: 'Ana Torres', rol, sedes }, sedeActivaId: null }
}

async function renderEn(ruta: string) {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  // /app/agenda es ya una pantalla con datos (B3): necesita la caché de React Query.
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

describe('carcasa B0 con menú por rol', () => {
  it('RECEPCION ve su menú por secciones y no ve Administración, Auditoría ni Reportes', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    await renderEn('/app')
    const nav = await screen.findByRole('navigation', { name: 'Menú principal' })
    for (const nombre of ['Inicio', 'Agenda', 'Pacientes', 'Caja']) {
      expect(within(nav).getByRole('link', { name: nombre })).toBeInTheDocument()
    }
    expect(within(nav).queryByText('Administración')).not.toBeInTheDocument()
    expect(within(nav).queryByText('Auditoría')).not.toBeInTheDocument()
    expect(within(nav).queryByText('Reportes')).not.toBeInTheDocument()
    // Villamax Radiología queda fuera del menú en esta etapa (MODULOS_OCULTOS).
    expect(within(nav).queryByText('Villamax Radiología')).not.toBeInTheDocument()
    // Las secciones sin ítems para este rol no aparecen.
    expect(within(nav).getByRole('group', { name: 'Día a día' })).toBeInTheDocument()
    expect(within(nav).getByRole('group', { name: 'Gestión' })).toBeInTheDocument()
    expect(within(nav).queryByRole('group', { name: 'Sistema' })).not.toBeInTheDocument()
  })

  it('ADMIN_GENERAL ve Administración, Auditoría, Reportes y el selector de las tres sedes', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES))
    await renderEn('/app')
    const nav = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(within(nav).getByRole('link', { name: 'Administración' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Auditoría' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Reportes' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Almacén' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Laboratorio Dental' })).toBeInTheDocument()
    expect(within(nav).queryByText('Villamax Radiología')).not.toBeInTheDocument()
    expect(within(nav).getByRole('group', { name: 'Sistema' })).toBeInTheDocument()

    // Dos negocios: primero se elige Villadent o Villamax y solo entonces se ven las sedes de ese negocio.
    const negocios = screen.getByRole('group', { name: 'Negocio activo' })
    expect(within(negocios).getAllByRole('button')).toHaveLength(2)
    expect(within(negocios).getByRole('button', { name: 'Villadent' })).toHaveAttribute('aria-pressed', 'true')

    const grupo = screen.getByRole('group', { name: 'Sede activa' })
    expect(within(grupo).getAllByRole('button')).toHaveLength(3)
    expect(within(grupo).getByRole('button', { name: 'Villadent Chilca' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(grupo).queryByRole('button', { name: /Villamax Chilca/ })).not.toBeInTheDocument()
    await userEvent.click(within(grupo).getByRole('button', { name: 'Todas' }))
    expect(obtenerSesion()?.sedeActivaId).toBeNull()

    // Villamax tiene una sola sede: queda activa y el selector de sedes se reemplaza por su nombre.
    await userEvent.click(within(negocios).getByRole('button', { name: /Villamax/ }))
    expect(obtenerSesion()?.negocioActivo).toBe('VILLAMAX')
    expect(obtenerSesion()?.sedeActivaId).toBe('sede-3')
    expect(screen.queryByRole('group', { name: 'Sede activa' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Villamax Chilca').length).toBeGreaterThan(0)

    // Al volver a Villadent, "Todas" son las sedes de Villadent.
    await userEvent.click(within(negocios).getByRole('button', { name: 'Villadent' }))
    expect(obtenerSesion()?.negocioActivo).toBe('VILLADENT')
    expect(obtenerSesion()?.sedeActivaId).toBeNull()
  })

  it('un usuario con una sola marca no ve el selector de negocio', async () => {
    guardarSesion(sesionDe('ADMIN_SEDE', SEDES.slice(0, 2)))
    await renderEn('/app')
    await screen.findByRole('navigation', { name: 'Menú principal' })

    expect(screen.queryByRole('group', { name: 'Negocio activo' })).not.toBeInTheDocument()
    expect(within(screen.getByRole('group', { name: 'Sede activa' })).getAllByRole('button')).toHaveLength(3)
  })

  it('ODONTOLOGO no ve Caja ni los accesos rápidos de recepción', async () => {
    guardarSesion(sesionDe('ODONTOLOGO'))
    await renderEn('/app')
    const nav = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(within(nav).queryByText('Caja')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Registrar paciente/i })).not.toBeInTheDocument()
  })

  it('marca la página actual con aria-current y tiene landmarks y datos del usuario', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    servidor.use(
      manejadores.sedes(),
      agenda.especialistas([]),
      agenda.citas([]),
      agenda.listaEspera([]),
      agenda.reservasWeb([]),
    )
    await renderEn('/app/agenda')
    const nav = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(within(nav).getByRole('link', { name: 'Agenda' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Inicio' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByText('Ana Torres')).toBeInTheDocument()
    expect(screen.getByText('Recepción y admisión')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()
  })

  it('la página de inicio muestra saludo, accesos rápidos y el pulso del día de la sede (sin cifras inventadas)', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    servidor.use(agenda.citas([]), agenda.especialistas([]), http.get(`${API}/caja/activa`, () => new HttpResponse(null, { status: 204 })))
    await renderEn('/app')
    expect(await screen.findByRole('heading', { level: 1, name: /Ana\./ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Registrar paciente/i })).toHaveAttribute('href', '/app/pacientes')
    expect(screen.getByRole('link', { name: /Agendar cita/i })).toHaveAttribute('href', '/app/agenda')
    // Con datos vacíos del backend no aparece ninguna cifra de dinero ni de sillones.
    expect(await screen.findByRole('heading', { name: 'Citas del día' })).toBeInTheDocument()
    expect(screen.queryByText(/S\/ /)).not.toBeInTheDocument()
    expect(screen.queryByText(/Sillones/i)).not.toBeInTheDocument()
  })

  it('el menú lateral se contrae y expande con un botón accesible', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    await renderEn('/app')
    const boton = await screen.findByRole('button', { name: 'Contraer menú lateral' })
    expect(boton).toHaveAttribute('aria-expanded', 'true')
    await userEvent.click(boton)
    expect(screen.getByRole('button', { name: 'Expandir menú lateral' })).toHaveAttribute('aria-expanded', 'false')
    // Los nombres siguen disponibles para lectores de pantalla.
    expect(screen.getByRole('link', { name: 'Agenda' })).toBeInTheDocument()
  })

  it('RECEPCION que entra a /app/configuracion termina en "Sin permiso"', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    const router = await renderEn('/app/configuracion')
    expect(await screen.findByRole('heading', { name: 'Sin permiso' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/app/sin-permiso')
  })

  it('sin sesión redirige al login', async () => {
    const router = await renderEn('/app/agenda')
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/app/login')
  })

  it('cerrar sesión limpia la sesión y vuelve al login', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    const router = await renderEn('/app')
    await userEvent.click(await screen.findByRole('button', { name: 'Cerrar sesión' }))
    expect(obtenerSesion()).toBeNull()
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/app/login')
  })
})

describe('búsqueda rápida de pacientes', () => {
  it('encuentra por DNI desde la cabecera y muestra el WhatsApp del paciente', async () => {
    guardarSesion(sesionDe('RECEPCION'))
    servidor.use(agenda.pacientes())
    await renderEn('/app')
    await userEvent.click(await screen.findByRole('button', { name: 'Buscar paciente' }))
    await userEvent.type(await screen.findByRole('searchbox', { name: 'Nombre, DNI o celular' }), '42891023')
    expect(await screen.findByText('Beatriz Tovar Lazo')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /WhatsApp a Beatriz/ })).toHaveAttribute('href', 'https://wa.me/51964123456')
  })
})
