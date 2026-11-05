/**
 * Flujo crítico B1 con la API simulada mediante page.route: las respuestas tienen la forma exacta del contrato
 * (tipos generados desde docs/api/openapi.json). No necesita backend.
 */
import { expect, test, type Page, type Route } from '@playwright/test'
import type { components } from '../src/shared/api/schema'

type Esquemas = components['schemas']

const USUARIO: Esquemas['UsuarioDTO'] = {
  id: '6a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d',
  email: 'admin@villadent.pe',
  nombre: 'Andrea Quispe',
  rol: 'ADMIN_GENERAL',
}

const SEDES: Esquemas['SedeResponseDTO'][] = [
  { id: 'sede-chilca', nombre: 'Sede Chilca', activa: true, sillones: 4, direccion: 'Av. 9 de Diciembre 482' },
  { id: 'sede-centro', nombre: 'Sede Huancayo Centro', activa: true, sillones: 3, direccion: 'Calle Real 520' },
]

const LOGIN_2FA_PENDIENTE: Esquemas['LoginResponseDTO'] = {
  token: 'jwt-temporal-2fa',
  tokenTipo: '2FA_PENDING',
  expiracionMs: 300_000,
  usuario: USUARIO,
}

const LOGIN_FINAL: Esquemas['LoginResponseDTO'] = {
  token: 'jwt-final',
  tokenTipo: 'Bearer',
  expiracionMs: 28_800_000,
  usuario: USUARIO,
}

function json(route: Route, cuerpo: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(cuerpo) })
}

/** Cuerpo de error de GlobalExceptionHandler (brechas-api.md A.4). */
function errorBackend(route: Route, status: number, mensaje: string) {
  return json(route, { timestamp: '2026-10-07T10:00:00-05:00', status, error: 'Unauthorized', message: mensaje, mensaje }, status)
}

async function simularApiBase(page: Page) {
  // Lo que no se simula responde vacío: el backend real contestaría 401 al token falso y cerraría la sesión.
  await page.route('**/api/v1/**', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }))
  await page.route('**/api/v1/auth/login', async (route) => {
    const cuerpo = route.request().postDataJSON() as Esquemas['LoginRequestDTO']
    expect(cuerpo).toEqual({ email: 'admin@villadent.pe', password: 'Clave-segura-1' })
    await json(route, LOGIN_2FA_PENDIENTE)
  })
  await page.route('**/api/v1/auth/me', (route) =>
    route.request().headers()['authorization'] === 'Bearer jwt-final'
      ? json(route, USUARIO)
      : route.fulfill({ status: 401 }),
  )
  await page.route('**/api/v1/sedes', (route) => json(route, SEDES))
}

async function loginConCredenciales(page: Page) {
  await page.goto('/app/login')
  await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
  await page.getByLabel('Correo corporativo').fill('admin@villadent.pe')
  await page.getByLabel('Contraseña', { exact: true }).fill('Clave-segura-1')
  await page.getByRole('button', { name: 'Iniciar sesión' }).click()
  await expect(page).toHaveURL(/\/app\/login\/2fa$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Código 2FA' })).toBeVisible()
}

test('login con 2FA: credenciales → código → entra en /app con la sesión real', async ({ page }) => {
  await simularApiBase(page)
  await page.route('**/api/v1/auth/2fa/login', async (route) => {
    const cuerpo = route.request().postDataJSON() as Esquemas['TotpLoginRequestDTO']
    expect(cuerpo).toEqual({ temp_token: 'jwt-temporal-2fa', codigo: '123456' })
    await json(route, LOGIN_FINAL)
  })

  await loginConCredenciales(page)
  await page.getByLabel('Código de verificación').fill('123456')
  await page.getByRole('button', { name: 'Verificar y entrar' }).click()

  await expect(page).toHaveURL(/\/app$/)
  await expect(page.getByRole('navigation', { name: 'Menú principal' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Menú de cuenta de Andrea Quispe' })).toBeVisible()
  // ADMIN_GENERAL: selector con «Todas» y las 2 sedes activas que devolvió GET /sedes.
  await expect(page.getByRole('group', { name: 'Sede activa' }).getByRole('button')).toHaveCount(3)
})

test('código erróneo se queda en 2FA; token temporal agotado vuelve al login', async ({ page }) => {
  await simularApiBase(page)
  let intentos = 0
  await page.route('**/api/v1/auth/2fa/login', async (route) => {
    intentos += 1
    await errorBackend(
      route,
      401,
      intentos === 1
        ? 'Código de autenticación 2FA o código de respaldo inválido.'
        : 'Token temporal inválido o expirado. Inicie sesión nuevamente.',
    )
  })

  await loginConCredenciales(page)

  // 1.er intento: código erróneo → error en la misma pestaña, campo vacío y enfocado.
  const campo = page.getByLabel('Código de verificación')
  await campo.fill('000000')
  await page.getByRole('button', { name: 'Verificar y entrar' }).click()
  await expect(page.getByRole('alert')).toContainText('Código de autenticación 2FA o código de respaldo inválido.')
  await expect(page).toHaveURL(/\/app\/login\/2fa$/)
  await expect(campo).toHaveValue('')
  await expect(campo).toBeFocused()

  // 2.º intento: el backend da el token temporal por agotado → vuelta al login con su mensaje.
  await campo.fill('111111')
  await page.getByRole('button', { name: 'Verificar y entrar' }).click()
  await expect(page).toHaveURL(/\/app\/login$/)
  await expect(page.getByRole('alert')).toContainText('Token temporal inválido o expirado. Inicie sesión nuevamente.')
  await expect(page.getByRole('link', { name: '2. 2FA Token' })).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('odontomax.sesion'))).toBeNull()
})
