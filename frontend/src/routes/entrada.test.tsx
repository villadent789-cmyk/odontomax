import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { guardarSesion, limpiarSesion, type Rol, type Sesion } from '@/shared/auth'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { renderRuta } from '@/test/renderRuta'

function sesion(rol: Rol = 'ADMIN_GENERAL', extra: Partial<Sesion> = {}): Sesion {
  return {
    token: 't',
    usuario: { id: 'u-1', nombre: 'Andrea Quispe', rol, sedes: [{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }] },
    sedeActivaId: 'sede-chilca',
    ...extra,
  }
}

const dashboard = () => screen.findByRole('heading', { level: 1, name: /Andrea\./ })

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
})
afterEach(() => limpiarSesion())

describe('Entrada al sistema: el dashboard es lo primero', { timeout: 20_000 }, () => {
  it('sin sesión, la raíz sigue siendo la web pública (para los pacientes)', async () => {
    const { router } = await renderRuta('/')
    expect(router.state.location.pathname).toBe('/')
    expect(await screen.findByRole('heading', { name: 'Villamax Radiología' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 1, name: /Andrea\./ })).not.toBeInTheDocument()
  })

  it.each<Rol>(['ADMIN_GENERAL', 'RECEPCION', 'ODONTOLOGO'])('con sesión (%s), abrir la raíz lleva al dashboard', async (rol) => {
    guardarSesion(sesion(rol))
    const { router } = await renderRuta('/')
    await dashboard()
    expect(router.state.location.pathname).toBe('/app')
  })

  it('con sesión, el resto de la web pública sigue accesible (servicios)', async () => {
    guardarSesion(sesion())
    const { router } = await renderRuta('/servicios')
    expect(router.state.location.pathname).toBe('/servicios')
    expect(screen.queryByRole('heading', { level: 1, name: /Andrea\./ })).not.toBeInTheDocument()
  })

  it('con sesión, la pantalla de login lleva al dashboard en vez de pedir las credenciales otra vez', async () => {
    guardarSesion(sesion())
    const { router } = await renderRuta('/app/login')
    await dashboard()
    expect(router.state.location.pathname).toBe('/app')
    expect(screen.queryByLabelText(/Contraseña/)).not.toBeInTheDocument()
  })

  it('con sesión y ?redirect= interno, el login respeta ese destino', async () => {
    guardarSesion(sesion())
    const { router } = await renderRuta('/app/login?redirect=/app/pacientes')
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/pacientes'))
  })

  it.each(['//sitio-malicioso.com', 'https://sitio-malicioso.com', '/app/login'])(
    'con sesión, un ?redirect= externo o circular (%s) se ignora y va al dashboard',
    async (destino) => {
      guardarSesion(sesion())
      const { router } = await renderRuta(`/app/login?redirect=${encodeURIComponent(destino)}`)
      await dashboard()
      expect(router.state.location.pathname).toBe('/app')
    },
  )

  it('sin sesión, el login sigue mostrando el formulario', async () => {
    const { router } = await renderRuta('/app/login')
    expect(router.state.location.pathname).toBe('/app/login')
    expect(await screen.findByLabelText(/Contraseña/)).toBeInTheDocument()
  })

  it('con sesión pendiente de 2FA, la pantalla de activación NO se redirige (se usa justo después de iniciar sesión)', async () => {
    guardarSesion(sesion('ADMIN_GENERAL', { segundoFactor: 'pendiente' }))
    const { router } = await renderRuta('/app/login/ajustes-2fa')
    expect(router.state.location.pathname).toBe('/app/login/ajustes-2fa')
    expect(await screen.findByRole('button', { name: /Generar código QR/ })).toBeInTheDocument()
  })

  it('sin sesión, entrar a /app sigue llevando al login con el destino recordado', async () => {
    const { router } = await renderRuta('/app/pacientes')
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/login'))
    expect(router.state.location.search).toMatchObject({ redirect: '/app/pacientes' })
  })
})
