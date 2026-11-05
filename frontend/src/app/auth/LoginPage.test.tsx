import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { limpiarSesion, obtenerSesion } from '@/shared/auth'
import {
  HttpResponse,
  SEDES,
  SEDE_VILLAMAX,
  USUARIO_ADMIN,
  USUARIO_RECEPCION,
  delay,
  errorBackend,
  login2faPendiente,
  loginOk,
  manejadores,
  servidor,
} from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'

const MSG_CREDENCIALES = 'Credenciales inválidas. Correo o contraseña incorrectos.'
const MSG_BLOQUEO = 'Demasiados intentos fallidos. Cuenta temporalmente bloqueada. Intente de nuevo en 15 minutos.'
const MSG_CODIGO = 'Código de autenticación 2FA o código de respaldo inválido.'
const MSG_TOKEN_TEMPORAL = 'Token temporal inválido o expirado. Inicie sesión nuevamente.'

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
})
afterEach(() => limpiarSesion())

async function llenarLogin(email = 'recepcion.chilca@villadent.pe', password = 'Clave-segura-1') {
  await userEvent.type(screen.getByLabelText('Correo corporativo'), email)
  await userEvent.type(screen.getByLabelText('Contraseña', { selector: 'input' }), password)
  await userEvent.click(screen.getByRole('button', { name: /Iniciar sesión/ }))
}

describe('B1 · Login', { timeout: 15_000 }, () => {
  it('valida los campos antes de llamar a la API y enfoca el primero con error', async () => {
    await renderRuta('/app/login')
    await userEvent.click(screen.getByRole('button', { name: /Iniciar sesión/ }))
    expect(await screen.findByText('Ingresa tu correo corporativo.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa tu contraseña.')).toBeInTheDocument()
    expect(screen.getByLabelText('Correo corporativo')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Correo corporativo')).toHaveFocus()

    await userEvent.type(screen.getByLabelText('Correo corporativo'), 'no-es-correo')
    await userEvent.click(screen.getByRole('button', { name: /Iniciar sesión/ }))
    expect(await screen.findByText(/Ingresa un correo válido/)).toBeInTheDocument()
  })

  it('credenciales inválidas: muestra el error del backend en una alerta', async () => {
    servidor.use(manejadores.login(() => errorBackend(401, MSG_CREDENCIALES)))
    await renderRuta('/app/login')
    await llenarLogin()
    const alerta = await screen.findByRole('alert')
    expect(within(alerta).getByText('Error de autenticación')).toBeInTheDocument()
    expect(within(alerta).getByText(MSG_CREDENCIALES)).toBeInTheDocument()
    await userEvent.click(within(alerta).getByRole('button', { name: 'Cerrar aviso' }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('bloqueo por intentos (401): "Bloqueo preventivo" con el texto y los minutos del backend', async () => {
    servidor.use(manejadores.login(() => errorBackend(401, MSG_BLOQUEO)))
    await renderRuta('/app/login')
    await llenarLogin()
    const alerta = await screen.findByRole('alert')
    expect(within(alerta).getByText('Bloqueo preventivo del sistema')).toBeInTheDocument()
    expect(alerta).toHaveTextContent(MSG_BLOQUEO)
    expect(within(alerta).getByText('15 minutos').tagName).toBe('STRONG')
  })

  it('bloqueo sin minutos en el mensaje: no los inventa', async () => {
    const mensaje = 'Demasiados intentos fallidos. Cuenta temporalmente bloqueada.'
    servidor.use(manejadores.login(() => errorBackend(401, mensaje)))
    await renderRuta('/app/login')
    await llenarLogin()
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent(mensaje)
    expect(alerta).not.toHaveTextContent(/minuto/)
  })

  it('muestra el estado de carga mientras espera al servidor', async () => {
    servidor.use(
      manejadores.login(async () => {
        await delay(150)
        return errorBackend(401, MSG_CREDENCIALES)
      }),
    )
    await renderRuta('/app/login')
    await llenarLogin()
    const boton = screen.getByRole('button', { name: /Verificando/ })
    expect(boton).toBeDisabled()
    expect(boton).toHaveAttribute('aria-busy', 'true')
    await screen.findByRole('alert')
    expect(screen.getByRole('button', { name: /Iniciar sesión/ })).toBeEnabled()
  })

  it('sin conexión con el servidor muestra un mensaje claro', async () => {
    servidor.use(manejadores.login(() => HttpResponse.error()))
    await renderRuta('/app/login')
    await llenarLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent(/No se pudo conectar con el servidor/)
  })

  it('login sin 2FA: envía el contrato, pide /auth/me y entra con la sesión real', async () => {
    let cuerpo: unknown
    servidor.use(
      manejadores.login((c) => {
        cuerpo = c
        return HttpResponse.json(loginOk(USUARIO_RECEPCION))
      }),
      manejadores.me(USUARIO_RECEPCION),
      manejadores.sedes(),
    )
    const { router } = await renderRuta('/app/login')
    await llenarLogin('recepcion.chilca@villadent.pe', 'Clave-segura-1')
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'))
    expect(cuerpo).toEqual({ email: 'recepcion.chilca@villadent.pe', password: 'Clave-segura-1' })
    const sesion = obtenerSesion()
    expect(sesion?.token).toBe('jwt-final')
    expect(sesion?.demo).toBeUndefined()
    expect(sesion?.usuario).toMatchObject({ nombre: 'Katherin Mendoza', rol: 'RECEPCION' })
    expect(sesion?.usuario.sedes).toEqual([{ id: 'sede-chilca', nombre: 'Sede Chilca', marca: 'VILLADENT' }])
    expect(sesion?.sedeActivaId).toBe('sede-chilca')
    expect(await screen.findByText('Katherin Mendoza')).toBeInTheDocument()
  })

  it('respeta ?redirect= solo si es una ruta interna', async () => {
    servidor.use(manejadores.login(() => HttpResponse.json(loginOk(USUARIO_RECEPCION))), manejadores.me(USUARIO_RECEPCION), manejadores.sedes())
    const { router } = await renderRuta('/app/login?redirect=%2Fapp%2Fagenda')
    await llenarLogin()
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/agenda'))
  })

  it('ADMIN_GENERAL ve todas las sedes activas', async () => {
    servidor.use(
      manejadores.login(() => HttpResponse.json(loginOk(USUARIO_ADMIN))),
      manejadores.me(USUARIO_ADMIN),
      manejadores.sedes(),
    )
    const { router } = await renderRuta('/app/login')
    await llenarLogin('admin@villadent.pe')
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'))
    expect(obtenerSesion()?.usuario.sedes.map((s) => s.id)).toEqual(['sede-chilca', 'sede-centro'])
  })

  it('la sede de Villamax entra a la sesión con la marca Villamax, no como Villadent', async () => {
    servidor.use(
      manejadores.login(() => HttpResponse.json(loginOk(USUARIO_ADMIN))),
      manejadores.me(USUARIO_ADMIN),
      manejadores.sedes([...SEDES, SEDE_VILLAMAX]),
    )
    const { router } = await renderRuta('/app/login')
    await llenarLogin('admin@villadent.pe')
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'))
    const marcas = Object.fromEntries((obtenerSesion()?.usuario.sedes ?? []).map((s) => [s.id, s.marca]))
    expect(marcas['sede-villamax-chilca']).toBe('VILLAMAX')
    expect(marcas['sede-chilca']).toBe('VILLADENT')
  })

  it('requiereConfigurar2fa=true: entra directo al sistema, sin llevar a la fuerza a Ajustes 2FA', async () => {
    servidor.use(
      manejadores.login(() => HttpResponse.json(loginOk(USUARIO_ADMIN, { requiereConfigurar2fa: true }))),
      manejadores.me(USUARIO_ADMIN),
      manejadores.sedes(),
    )
    const { router } = await renderRuta('/app/login')
    await llenarLogin('admin@villadent.pe')
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'))
    // Queda registrado que falta configurarlo (se puede hacer desde el menú de la cuenta).
    expect(obtenerSesion()?.segundoFactor).toBe('pendiente')
  })

  it('el acceso es limpio: sin pestañas de desarrollo, solo correo, contraseña, botón y el aviso de a quién pedirle la contraseña', async () => {
    await renderRuta('/app/login')
    expect(screen.queryByRole('navigation', { name: 'Pasos de acceso' })).not.toBeInTheDocument()
    for (const nombre of [/1\. Login/, /2\. 2FA Token/, /3\. Ajustes 2FA/, /4\. Recuperar/]) {
      expect(screen.queryByText(nombre)).not.toBeInTheDocument()
    }
    expect(screen.getByLabelText('Correo corporativo')).toBeInTheDocument()
    expect(screen.getByLabelText('Contraseña')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Iniciar sesión/ })).toBeInTheDocument()
    expect(screen.getByText(/Pide a un administrador que la restablezca/)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Olvidaste/ })).not.toBeInTheDocument()
  })

  it('muestra el aviso "Contraseña cambiada" cuando llega con ?aviso=password-cambiado', async () => {
    await renderRuta('/app/login?aviso=password-cambiado')
    expect(screen.getByRole('status')).toHaveTextContent('Contraseña cambiada. Inicia sesión de nuevo.')
  })

  it('no entra automáticamente con la sesión de ejemplo', async () => {
    const { router } = await renderRuta('/app')
    expect(router.state.location.pathname).toBe('/app/login')
    expect(obtenerSesion()).toBeNull()
  })
})

describe('B1 · Segundo factor', () => {
  async function hastaPaso2fa() {
    servidor.use(manejadores.login(() => HttpResponse.json(login2faPendiente(USUARIO_ADMIN))))
    const vista = await renderRuta('/app/login')
    await llenarLogin('admin@villadent.pe')
    await waitFor(() => expect(vista.router.state.location.pathname).toBe('/app/login/2fa'))
    return vista
  }

  it('con 2FA activo pasa a pedir el código sin guardar sesión', async () => {
    await hastaPaso2fa()
    expect(await screen.findByRole('heading', { level: 1, name: 'Código 2FA' })).toBeInTheDocument()
    expect(obtenerSesion()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('código correcto: envía temp_token + codigo y entra en /app', async () => {
    let cuerpo: unknown
    servidor.use(
      manejadores.login2fa((c) => {
        cuerpo = c
        return HttpResponse.json(loginOk(USUARIO_ADMIN))
      }),
      manejadores.me(USUARIO_ADMIN),
      manejadores.sedes(),
    )
    const { router } = await hastaPaso2fa()
    await userEvent.type(screen.getByLabelText('Código de verificación'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y entrar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/app'))
    expect(cuerpo).toEqual({ temp_token: 'jwt-temporal-2fa', codigo: '123456' })
    expect(obtenerSesion()?.segundoFactor).toBe('activo')
  })

  it('valida el formato del código (6 dígitos o respaldo de 8)', async () => {
    await hastaPaso2fa()
    await userEvent.type(screen.getByLabelText('Código de verificación'), '12a')
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y entrar' }))
    expect(await screen.findByText(/6 dígitos \(aplicación\) u 8 caracteres/)).toBeInTheDocument()
  })

  it('código erróneo: se queda en 2FA con el error y el campo vacío y enfocado', async () => {
    servidor.use(manejadores.login2fa(() => errorBackend(401, MSG_CODIGO)))
    const { router } = await hastaPaso2fa()
    const campo = screen.getByLabelText('Código de verificación')
    await userEvent.type(campo, '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y entrar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(MSG_CODIGO)
    expect(router.state.location.pathname).toBe('/app/login/2fa')
    expect(campo).toHaveValue('')
    expect(campo).toHaveFocus()
  })

  it('token temporal caducado o agotado: vuelve al login con el mensaje del backend', async () => {
    servidor.use(manejadores.login2fa(() => errorBackend(401, MSG_TOKEN_TEMPORAL)))
    const { router } = await hastaPaso2fa()
    await userEvent.type(screen.getByLabelText('Código de verificación'), '654321')
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y entrar' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/login'))
    expect(await screen.findByRole('alert')).toHaveTextContent(MSG_TOKEN_TEMPORAL)
    // El token temporal se descarta: ya no se puede volver al paso del código.
    expect(screen.queryByRole('heading', { level: 1, name: 'Código 2FA' })).not.toBeInTheDocument()
  })

  it('sin login previo, /app/login/2fa pide iniciar sesión', async () => {
    await renderRuta('/app/login/2fa')
    expect(screen.getByText('Primero inicia sesión')).toBeInTheDocument()
  })
})
