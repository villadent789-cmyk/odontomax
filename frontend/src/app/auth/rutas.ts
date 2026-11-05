/** Rutas de B1. Ya no hay recuperación por correo: un administrador restablece la contraseña desde Administración. */
export const RUTAS_ACCESO = {
  login: '/app/login',
  segundoFactor: '/app/login/2fa',
  ajustes2fa: '/app/login/ajustes-2fa',
} as const

/** Avisos que se pueden pedir a la pestaña Login por query string (?aviso=...). Solo claves conocidas. */
export const AVISOS_LOGIN = {
  'password-cambiado': 'Contraseña cambiada. Inicia sesión de nuevo.',
  'sesion-vencida': 'Tu sesión venció o fue cerrada. Inicia sesión de nuevo.',
} as const

export type ClaveAvisoLogin = keyof typeof AVISOS_LOGIN

export function esClaveAvisoLogin(v: unknown): v is ClaveAvisoLogin {
  return typeof v === 'string' && Object.hasOwn(AVISOS_LOGIN, v)
}
