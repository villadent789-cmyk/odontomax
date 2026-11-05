import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory } from '@tanstack/react-router'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { crearRouter } from '@/routes/router'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion, obtenerSesion, type Rol, type Sesion } from '@/shared/auth'
import { menuParaRol } from '../layout/menu'
import { RadiologiaPage } from '../radiologia/RadiologiaPage'
import { EstudiosVillamaxPage } from './EstudiosVillamaxPage'

const SEDES_MIXTO = [
  { id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' as const },
  { id: 'sede-huancayo', nombre: 'Huancayo Centro', marca: 'VILLADENT' as const },
  { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' as const },
]

const SEDES_SOLO_VILLAMAX = [
  { id: 'sede-villamax-chilca', nombre: 'Villamax Chilca', marca: 'VILLAMAX' as const },
]

const SEDES_SOLO_VILLADENT = [
  { id: 'sede-chilca', nombre: 'Villadent Chilca', marca: 'VILLADENT' as const },
  { id: 'sede-huancayo', nombre: 'Huancayo Centro', marca: 'VILLADENT' as const },
]

function sesionDe(rol: Rol, sedes = SEDES_MIXTO): Sesion {
  return { token: 't', usuario: { nombre: 'Dr. Radiólogo', rol, sedes }, sedeActivaId: null }
}

async function renderConRuta(ruta: string, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  const router = crearRouter(createMemoryHistory({ initialEntries: [ruta] }))
  render(
    <QueryClientProvider client={qc}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
  await act(() => router.load())
  return { router, queryClient: qc }
}

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
  _reiniciarCacheSesion()
})
afterEach(() => limpiarSesion())

describe('Paso 6: Módulo Villamax como negocio independiente', () => {
  it('ADMIN_GENERAL con acceso a ambos negocios ve el switcher "Villadent | Villamax 3D"', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_MIXTO))
    await renderConRuta('/app')

    const switcher = await screen.findByRole('group', { name: 'Negocio activo' })
    expect(within(switcher).getByRole('button', { name: 'Villadent' })).toBeInTheDocument()
    expect(within(switcher).getByRole('button', { name: /Villamax/ })).toBeInTheDocument()
  })

  it('un doctor cuyas sedes son solo de Villamax no ve el switcher y entra directo a Villamax', async () => {
    // Al guardar sesión con solo sedes Villamax, negocioActivo se inicializa en VILLAMAX
    guardarSesion(sesionDe('ODONTOLOGO', SEDES_SOLO_VILLAMAX))
    const sesion = obtenerSesion()
    expect(sesion?.negocioActivo).toBe('VILLAMAX')

    await renderConRuta('/app')
    // No hay selector de negocio porque solo tiene 1 negocio disponible
    expect(screen.queryByRole('group', { name: 'Negocio activo' })).not.toBeInTheDocument()

    // El menú lateral muestra los ítems de Villamax
    const nav = await screen.findByRole('navigation', { name: 'Menú principal' })
    expect(within(nav).getByRole('link', { name: 'Resumen' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Estudios' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Derivaciones y comisiones' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Odontograma' })).not.toBeInTheDocument()
  })

  it('un usuario con solo sedes de Villadent no ve el switcher de negocio', async () => {
    guardarSesion(sesionDe('RECEPCION', SEDES_SOLO_VILLADENT))
    await renderConRuta('/app')

    expect(screen.queryByRole('group', { name: 'Negocio activo' })).not.toBeInTheDocument()
    const selectorSedes = screen.getByRole('group', { name: 'Sede activa' })
    expect(within(selectorSedes).queryByRole('button', { name: /Villamax/ })).not.toBeInTheDocument()
  })

  it('SelectorSede lista solo las sedes del negocio activo (Villadent nunca muestra Villamax y viceversa)', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_MIXTO))
    await renderConRuta('/app')

    // En Villadent: sedes de Villadent
    const grupoSedes = screen.getByRole('group', { name: 'Sede activa' })
    expect(within(grupoSedes).getByRole('button', { name: 'Villadent Chilca' })).toBeInTheDocument()
    expect(within(grupoSedes).getByRole('button', { name: 'Huancayo Centro' })).toBeInTheDocument()
    expect(within(grupoSedes).queryByRole('button', { name: /Villamax/ })).not.toBeInTheDocument()

    // Cambiar a Villamax
    const switcher = screen.getByRole('group', { name: 'Negocio activo' })
    await userEvent.click(within(switcher).getByRole('button', { name: /Villamax/ }))

    // En Villamax: no lista sedes de Villadent, fija Villamax Chilca
    expect(screen.queryByRole('group', { name: 'Sede activa' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Villamax Chilca').length).toBeGreaterThan(0)
  })

  it('cambiar de negocio limpia el cache de React Query para no mezclar datos', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_MIXTO))
    const qc = new QueryClient()
    qc.setQueryData(['citas-villadent'], { id: 123 })
    expect(qc.getQueryData(['citas-villadent'])).toBeDefined()

    await renderConRuta('/app', qc)
    const switcher = screen.getByRole('group', { name: 'Negocio activo' })
    await userEvent.click(within(switcher).getByRole('button', { name: /Villamax/ }))

    // El query data anterior fue limpiado
    expect(qc.getQueryData(['citas-villadent'])).toBeUndefined()
  })

  it('menú de Villamax contiene los módulos requeridos y oculta los exclusivamente odontológicos', () => {
    const admin = menuParaRol('ADMIN_GENERAL', 'VILLAMAX').map((m) => m.etiqueta)
    expect(admin).toEqual([
      'Resumen',
      'Agenda',
      'Pacientes',
      'Nuevo tratamiento',
      'Estudios',
      'Derivaciones y comisiones',
      'Caja',
      'Reportes',
      'Auditoría',
      'Administración',
    ])

    const doc = menuParaRol('ODONTOLOGO', 'VILLAMAX').map((m) => m.etiqueta)
    expect(doc).toEqual(['Resumen', 'Agenda', 'Pacientes', 'Nuevo tratamiento', 'Estudios', 'Derivaciones y comisiones'])
    expect(doc).not.toContain('Caja')
    expect(doc).not.toContain('Odontograma')
    expect(doc).not.toContain('Almacén')
    expect(doc).not.toContain('Laboratorio Dental')
    expect(doc).not.toContain('Reportes')
    expect(doc).not.toContain('Auditoría')
    expect(doc).not.toContain('Administración')

    const recep = menuParaRol('RECEPCION', 'VILLAMAX').map((m) => m.etiqueta)
    expect(recep).toEqual(['Resumen', 'Agenda', 'Pacientes', 'Estudios', 'Caja'])
    expect(recep).not.toContain('Derivaciones y comisiones')
    expect(recep).not.toContain('Reportes')
    expect(recep).not.toContain('Auditoría')
    expect(recep).not.toContain('Administración')
  })

  it('EstudiosVillamaxPage muestra tarifas oficiales y comisiones a ADMIN_GENERAL, mientras RECEPCION no ve comisión', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    // Test como ADMIN_GENERAL
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_SOLO_VILLAMAX))
    const { unmount } = render(
      <QueryClientProvider client={qc}>
        <EstudiosVillamaxPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Radiografía Panorámica')).toBeInTheDocument()
    expect(screen.getByText('Tomografía Cone Beam 3D')).toBeInTheDocument()
    // Admin ve % y monto de comisión
    expect(screen.getByText(/% Comisión/)).toBeInTheDocument()
    expect(screen.getByText(/Monto Comisión/)).toBeInTheDocument()
    unmount()

    // Test como RECEPCION: no ve columnas de comisiones
    guardarSesion(sesionDe('RECEPCION', SEDES_SOLO_VILLAMAX))
    render(
      <QueryClientProvider client={qc}>
        <EstudiosVillamaxPage />
      </QueryClientProvider>,
    )

    expect(await screen.findByText('Radiografía Panorámica')).toBeInTheDocument()
    expect(screen.queryByText(/% Comisión/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Monto Comisión/)).not.toBeInTheDocument()
  })

  it('RadiologiaPage restringe la visibilidad de comisiones y botón liquidar solo a ADMIN_GENERAL', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    // RECEPCION no ve el botón de registrar comisión
    guardarSesion(sesionDe('RECEPCION', SEDES_SOLO_VILLAMAX))
    const { unmount } = render(
      <QueryClientProvider client={qc}>
        <RadiologiaPage />
      </QueryClientProvider>,
    )

    expect(screen.queryByRole('button', { name: /\+ Registrar Comisión/i })).not.toBeInTheDocument()
    unmount()

    // ODONTOLOGO (Doctor Villamax) no ve botón registrar comisión (solo ADMIN_GENERAL) ni KPI de montos liquidados
    guardarSesion(sesionDe('ODONTOLOGO', SEDES_SOLO_VILLAMAX))
    const resOdontologo = render(
      <QueryClientProvider client={qc}>
        <RadiologiaPage />
      </QueryClientProvider>,
    )

    expect(screen.queryByRole('button', { name: /\+ Registrar Comisión/i })).not.toBeInTheDocument()
    expect(screen.queryByText('Comisiones Pendientes')).not.toBeInTheDocument()
    expect(screen.queryByText('Comisiones Liquidadas')).not.toBeInTheDocument()
    expect(screen.getByText('Estudios Derivados')).toBeInTheDocument()
    resOdontologo.unmount()

    // ADMIN_GENERAL sí ve el botón registrar comisión y los KPI
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_SOLO_VILLAMAX))
    render(
      <QueryClientProvider client={qc}>
        <RadiologiaPage />
      </QueryClientProvider>,
    )
    expect(screen.getByRole('button', { name: /\+ Registrar Comisión/i })).toBeInTheDocument()
  })

  it('entrar por URL directa a /app/radiologia con Villadent activo redirige a /app (Inicio)', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_SOLO_VILLADENT))
    const { router } = await renderConRuta('/app/radiologia')
    expect(router.state.location.pathname).toBe('/app')
  })

  it('entrar por URL directa a /app/inventario con Villamax activo redirige a /app (Resumen)', async () => {
    guardarSesion(sesionDe('ADMIN_GENERAL', SEDES_SOLO_VILLAMAX))
    const { router } = await renderConRuta('/app/inventario')
    expect(router.state.location.pathname).toBe('/app')
  })

  it('al cambiar de negocio estando en una pantalla del otro negocio redirige a /app y limpia el cache', async () => {
    guardarSesion({ ...sesionDe('ADMIN_GENERAL', SEDES_MIXTO), negocioActivo: 'VILLAMAX' })
    const { router } = await renderConRuta('/app/radiologia')
    expect(router.state.location.pathname).toBe('/app/radiologia')

    const switcher = screen.getByRole('group', { name: 'Negocio activo' })
    await userEvent.click(within(switcher).getByRole('button', { name: 'Villadent' }))

    expect(router.state.location.pathname).toBe('/app')
  })

  it('al cambiar de negocio con una ficha abierta (/app/pacientes/:id) vuelve a /app en vez de dar "Sin permiso"', async () => {
    guardarSesion({ ...sesionDe('ADMIN_GENERAL', SEDES_MIXTO), negocioActivo: 'VILLADENT' })
    const { router } = await renderConRuta('/app/pacientes/p-1')
    expect(router.state.location.pathname).toBe('/app/pacientes/p-1')

    const switcher = screen.getByRole('group', { name: 'Negocio activo' })
    await userEvent.click(within(switcher).getByRole('button', { name: 'Villamax 3D' }))

    expect(router.state.location.pathname).toBe('/app')
  })
})

