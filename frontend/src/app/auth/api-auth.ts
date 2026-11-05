/**
 * Llamadas de identidad (B1). Todas pasan por el cliente tipado (tipos del contrato + tipos-manuales.ts).
 *
 * `sinRedireccion: true` en todas: aquí un 401 significa "credenciales/código incorrectos" o "cuenta bloqueada"
 * y un 403 es una regla de política que la pantalla explica; no deben llevar al login ni a "Sin permiso".
 */
import { apiTipada, type Esquema } from '@/shared/api'
import { HttpError } from '@/shared/http'

export type LoginResponse = Esquema<'LoginResponseDTO'>
export type UsuarioApi = Esquema<'UsuarioDTO'>
export type SedeApi = Esquema<'SedeResponseDTO'>
export type ConfiguracionTotp = Esquema<'TotpConfigurarResponseDTO'>

/** El login con 2FA activo devuelve un token temporal con este tipo (LoginService, paso 6). */
export const TIPO_TOKEN_2FA_PENDIENTE = '2FA_PENDING'

const sinRedireccion = true

export function login(email: string, password: string) {
  return apiTipada.post('/auth/login', { cuerpo: { email, password }, sinRedireccion })
}

export function completarLogin2fa(tempToken: string, codigo: string) {
  return apiTipada.post('/auth/2fa/login', { cuerpo: { temp_token: tempToken, codigo }, sinRedireccion })
}

/** Perfil del token indicado (aún no guardado en la sesión). */
export function perfil(token: string) {
  return apiTipada.get('/auth/me', { headers: { Authorization: `Bearer ${token}` }, sinRedireccion })
}

/** GET /sedes es público: sedes activas. */
export function sedesActivas() {
  return apiTipada.get('/sedes', { sinRedireccion })
}

/** Con el 2FA activo hay que enviar el código actual (TOTP o de respaldo). */
export function configurar2fa(codigoActual?: string) {
  return apiTipada.post('/auth/2fa/configurar', {
    cuerpo: codigoActual ? { codigo: codigoActual } : {},
    sinRedireccion,
  })
}

export function verificarActivar2fa(codigo: string) {
  return apiTipada.post('/auth/2fa/verificar-activar', { cuerpo: { codigo: Number(codigo) }, sinRedireccion })
}

export function desactivar2fa(passwordActual: string, codigo: string) {
  return apiTipada.post('/auth/2fa/desactivar', {
    cuerpo: { password_actual: passwordActual, codigo },
    sinRedireccion,
  })
}

export function cambiarPassword(passwordActual: string, passwordNuevo: string) {
  return apiTipada.post('/auth/cambiar-password', {
    cuerpo: { password_actual: passwordActual, password_nuevo: passwordNuevo },
    sinRedireccion,
  })
}

/* ───────────── Interpretación de errores ───────────── */

export const MENSAJE_SIN_CONEXION = 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.'

/**
 * El bloqueo por intentos llega como 401 (login, LoginService) o 429 (gestión del 2FA) con un texto que incluye
 * los minutos ("Intente de nuevo en N minutos"); no hay Retry-After. Se reconoce por el texto.
 */
export function esMensajeDeBloqueo(mensaje: string): boolean {
  return /bloquead|demasiados intentos/i.test(mensaje)
}

/** "Token temporal inválido o expirado" o "no es un token temporal": hay que volver a iniciar sesión. */
export function esTokenTemporalInvalido(mensaje: string): boolean {
  return /token temporal|token proporcionado no es un token temporal/i.test(mensaje)
}

export type ErrorAcceso =
  | { tipo: 'bloqueo'; mensaje: string }
  | { tipo: 'error'; mensaje: string }
  /** 403 de política (p. ej. ADMIN_GENERAL no puede desactivar su 2FA). */
  | { tipo: 'politica'; mensaje: string }

/** Convierte cualquier error de las llamadas en un aviso para la pantalla (siempre con el texto del backend). */
export function interpretarError(error: unknown, porDefecto: string): ErrorAcceso {
  if (error instanceof HttpError) {
    const mensaje = error.message || porDefecto
    if (error.status === 429 || esMensajeDeBloqueo(mensaje)) return { tipo: 'bloqueo', mensaje }
    if (error.status === 403) return { tipo: 'politica', mensaje }
    return { tipo: 'error', mensaje }
  }
  if (error instanceof TypeError) return { tipo: 'error', mensaje: MENSAJE_SIN_CONEXION }
  if (error instanceof Error && error.message) return { tipo: 'error', mensaje: error.message }
  return { tipo: 'error', mensaje: porDefecto }
}
