import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { _reiniciarCacheSesion } from '@/shared/auth/sesion'
import { guardarSesion, limpiarSesion, obtenerSesion, type Rol, type Sesion } from '@/shared/auth'
import { HttpResponse, errorBackend, manejadores, respuestas, servidor } from '@/test/msw'
import { renderRuta } from '@/test/renderRuta'
import type { Esquema } from '@/shared/api'

const CONFIG: Esquema<'TotpConfigurarResponseDTO'> = {
  secret: 'JBSWY3DPEHPK3PXP',
  otpauth_url: 'otpauth://totp/OdontoMax:admin@villadent.pe?secret=JBSWY3DPEHPK3PXP&issuer=OdontoMax',
  otpauthUrl: 'otpauth://totp/OdontoMax:admin@villadent.pe?secret=JBSWY3DPEHPK3PXP&issuer=OdontoMax',
  qr_code: 'data:image/png;base64,AAAA',
  qrCode: 'data:image/png;base64,AAAA',
  backup_codes: ['ABCD2345', 'EFGH6723', 'IJKL2345', 'MNOP6723', 'QRST2345'],
  backupCodes: ['ABCD2345', 'EFGH6723', 'IJKL2345', 'MNOP6723', 'QRST2345'],
}

function sesion(rol: Rol, segundoFactor: Sesion['segundoFactor']): Sesion {
  return {
    token: 'jwt-final',
    usuario: { nombre: 'Prueba', correo: 'prueba@villadent.pe', rol, sedes: [] },
    sedeActivaId: null,
    segundoFactor,
  }
}

beforeEach(() => {
  sessionStorage.clear()
  _reiniciarCacheSesion()
})
afterEach(() => limpiarSesion())

describe('B1 · Ajustes 2FA', () => {
  it('sin sesión pide iniciar sesión', async () => {
    await renderRuta('/app/login/ajustes-2fa')
    expect(screen.getByText('Inicia sesión primero')).toBeInTheDocument()
  })

  it('ADMIN_GENERAL sin 2FA: genera QR y códigos de respaldo y activa con el primer código', async () => {
    let cuerpoConfigurar: unknown
    let cuerpoVerificar: unknown
    servidor.use(
      manejadores.configurar2fa((c) => {
        cuerpoConfigurar = c
        return HttpResponse.json(CONFIG)
      }),
      manejadores.verificarActivar((c) => {
        cuerpoVerificar = c
        return respuestas.estado2fa('Autenticación de dos factores (2FA) activada con éxito.', true)
      }),
    )
    guardarSesion(sesion('ADMIN_GENERAL', 'pendiente'))
    await renderRuta('/app/login/ajustes-2fa')
    expect(screen.queryByLabelText('Código actual o de respaldo')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Desactivar/ })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /Generar código QR/ }))
    expect(await screen.findByRole('img', { name: /Código QR/ })).toBeInTheDocument()
    expect(cuerpoConfigurar).toEqual({})
    expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument()
    const lista = screen.getByRole('list', { name: 'Códigos de respaldo' })
    expect(within(lista).getAllByRole('listitem')).toHaveLength(5)
    expect(screen.getByText(/No se volverán a mostrar/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Copiar códigos/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Descargar .txt/ })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Código de 6 dígitos'), '012345')
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y activar' }))
    expect(await screen.findByText('Autenticación de dos factores (2FA) activada con éxito.')).toBeInTheDocument()
    expect(cuerpoVerificar).toEqual({ codigo: 12345 })
    expect(obtenerSesion()?.segundoFactor).toBe('activo')
  })

  it('ADMIN_GENERAL con 2FA activo: exige el código actual en {codigo} y muestra el error del backend', async () => {
    let cuerpo: unknown
    servidor.use(
      manejadores.configurar2fa((c) => {
        cuerpo = c
        return errorBackend(400, 'Código de autenticación 2FA o código de respaldo inválido.')
      }),
    )
    guardarSesion(sesion('ADMIN_GENERAL', 'activo'))
    await renderRuta('/app/login/ajustes-2fa')
    await userEvent.click(screen.getByRole('button', { name: /Generar nueva configuración/ }))
    expect(await screen.findByText('Ingresa tu código actual o uno de respaldo.')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Código actual o de respaldo'), 'abcd2345')
    await userEvent.click(screen.getByRole('button', { name: /Generar nueva configuración/ }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Código de autenticación 2FA o código de respaldo inválido.')
    expect(cuerpo).toEqual({ codigo: 'abcd2345' })
  })

  it('código de activación erróneo: muestra el error y deja reintentar', async () => {
    servidor.use(
      manejadores.configurar2fa(() => HttpResponse.json(CONFIG)),
      manejadores.verificarActivar(() =>
        errorBackend(400, 'Código TOTP inválido. Verifique la sincronización de hora de su dispositivo e intente de nuevo.'),
      ),
    )
    guardarSesion(sesion('ADMIN_GENERAL', 'pendiente'))
    await renderRuta('/app/login/ajustes-2fa')
    await userEvent.click(screen.getByRole('button', { name: /Generar código QR/ }))
    await userEvent.type(await screen.findByLabelText('Código de 6 dígitos'), '999999')
    await userEvent.click(screen.getByRole('button', { name: 'Verificar y activar' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/Código TOTP inválido/)
    expect(screen.getByLabelText('Código de 6 dígitos')).toHaveFocus()
  })

  it('otro rol con 2FA activo: desactiva con {password_actual, codigo}', async () => {
    let cuerpo: unknown
    servidor.use(
      manejadores.desactivar2fa((c) => {
        cuerpo = c
        return respuestas.estado2fa('Autenticación de dos factores (2FA) desactivada.', false)
      }),
    )
    guardarSesion(sesion('RECEPCION', 'activo'))
    await renderRuta('/app/login/ajustes-2fa')
    expect(screen.queryByRole('button', { name: /Generar/ })).not.toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('Contraseña actual', { selector: 'input' }), 'Clave-1')
    await userEvent.type(screen.getByLabelText('Código 2FA o de respaldo'), '123456')
    await userEvent.click(screen.getByRole('button', { name: /Desactivar 2FA/ }))
    expect(await screen.findByText('Autenticación de dos factores (2FA) desactivada.')).toBeInTheDocument()
    expect(cuerpo).toEqual({ password_actual: 'Clave-1', codigo: '123456' })
    expect(obtenerSesion()?.segundoFactor).toBe('inactivo')
  })

  it('desactivar: respuesta unificada de error y bloqueo 429 con el texto del backend', async () => {
    let llamada = 0
    servidor.use(
      manejadores.desactivar2fa(() => {
        llamada += 1
        return llamada === 1
          ? errorBackend(400, 'Contraseña actual o código de autenticación 2FA incorrectos.')
          : errorBackend(429, 'Demasiados intentos fallidos de verificación 2FA. Intente de nuevo en 14 minutos.')
      }),
    )
    guardarSesion(sesion('ODONTOLOGO', 'activo'))
    const { router } = await renderRuta('/app/login/ajustes-2fa')
    const intentar = async () => {
      await userEvent.type(screen.getByLabelText('Contraseña actual', { selector: 'input' }), 'mala')
      await userEvent.type(screen.getByLabelText('Código 2FA o de respaldo'), '000000')
      await userEvent.click(screen.getByRole('button', { name: /Desactivar 2FA/ }))
    }
    await intentar()
    expect(await screen.findByRole('alert')).toHaveTextContent('Contraseña actual o código de autenticación 2FA incorrectos.')
    await intentar()
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Bloqueo preventivo del sistema'))
    expect(screen.getByText('14 minutos').tagName).toBe('STRONG')
    // Ni el 400 ni el 429 cierran la sesión.
    expect(router.state.location.pathname).toBe('/app/login/ajustes-2fa')
    expect(obtenerSesion()).not.toBeNull()
  })

  it('desactivar: un 403 de política se muestra claramente y no lleva a "Sin permiso"', async () => {
    servidor.use(
      manejadores.desactivar2fa(() =>
        errorBackend(403, 'El Administrador General tiene 2FA obligatorio por política institucional.'),
      ),
    )
    guardarSesion(sesion('ADMIN_SEDE', 'activo'))
    const { router } = await renderRuta('/app/login/ajustes-2fa')
    await userEvent.type(screen.getByLabelText('Contraseña actual', { selector: 'input' }), 'Clave-1')
    await userEvent.type(screen.getByLabelText('Código 2FA o de respaldo'), '123456')
    await userEvent.click(screen.getByRole('button', { name: /Desactivar 2FA/ }))
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('Operación no permitida por política')
    expect(alerta).toHaveTextContent(/2FA obligatorio por política institucional/)
    expect(router.state.location.pathname).toBe('/app/login/ajustes-2fa')
  })

  it('ADMIN_GENERAL nunca ve el botón de desactivar', async () => {
    guardarSesion(sesion('ADMIN_GENERAL', 'activo'))
    await renderRuta('/app/login/ajustes-2fa')
    expect(screen.queryByRole('button', { name: /Desactivar/ })).not.toBeInTheDocument()
    expect(screen.getByText(/no puede desactivarse desde aquí/)).toBeInTheDocument()
  })
})
