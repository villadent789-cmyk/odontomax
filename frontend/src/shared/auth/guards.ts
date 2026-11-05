import { redirect } from '@tanstack/react-router'
import { puede, type Recurso } from './roles'
import { obtenerSesion, type Negocio, type Sesion } from './sesion'

export const RUTA_LOGIN = '/app/login'
export const RUTA_SIN_PERMISO = '/app/sin-permiso'
export const RUTA_INICIO = '/app'

export type ResultadoAcceso = 'permitido' | 'login' | 'sin-permiso' | 'redireccion-negocio'

/**
 * Determina si una ruta o pantalla pertenece al negocio activo.
 * - /app/radiologia y /app/villamax/* solo en VILLAMAX.
 * - /app/inventario y /app/laboratorio solo en VILLADENT.
 */
export function esRutaPermitidaParaNegocio(ruta: string, negocio?: Negocio): boolean {
  const n = negocio ?? 'VILLADENT'
  let path = ruta.split('?')[0].split('#')[0]
  try {
    if (path.startsWith('http://') || path.startsWith('https://')) {
      path = new URL(path).pathname
    }
  } catch {
    // fallback
  }
  path = path.replace(/\/$/, '') || '/'

  const esExclusivaVillamax =
    path === '/app/radiologia' ||
    path.startsWith('/app/radiologia/') ||
    path === '/app/villamax' ||
    path.startsWith('/app/villamax/')

  const esExclusivaVilladent =
    path === '/app/inventario' ||
    path.startsWith('/app/inventario/') ||
    path === '/app/laboratorio' ||
    path.startsWith('/app/laboratorio/')

  if (n === 'VILLADENT' && esExclusivaVillamax) {
    return false
  }
  if (n === 'VILLAMAX' && esExclusivaVilladent) {
    return false
  }
  return true
}

/** Decide el acceso a un recurso y ruta. Función pura para poder probarla sin router. */
export function evaluarAcceso(sesion: Sesion | null, recurso?: Recurso, destino?: string): ResultadoAcceso {
  if (!sesion) return 'login'
  if (destino && !esRutaPermitidaParaNegocio(destino, sesion.negocioActivo)) {
    return 'redireccion-negocio'
  }
  if (recurso && !puede(sesion.usuario.rol, recurso)) return 'sin-permiso'
  return 'permitido'
}

/**
 * Para `beforeLoad` de TanStack Router: redirige a login si no hay sesión, al inicio si la pantalla no
 * pertenece al negocio activo (redirección silenciosa), o a "Sin permiso" si el rol no puede ver el recurso.
 */
export function requerirAcceso(recurso: Recurso | undefined, destino: string) {
  const sesion = obtenerSesion()
  const resultado = evaluarAcceso(sesion, recurso, destino)
  if (resultado === 'login') {
    throw redirect({ to: RUTA_LOGIN, search: { redirect: destino } })
  }
  if (resultado === 'redireccion-negocio') {
    throw redirect({ to: RUTA_INICIO })
  }
  if (resultado === 'sin-permiso') {
    throw redirect({ to: RUTA_SIN_PERMISO })
  }
}
