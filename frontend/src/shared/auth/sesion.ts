import { useSyncExternalStore } from 'react'
import { esRol, type Rol } from './roles'

/** Negocio del grupo: Villadent (clínica dental) o Villamax (centro radiológico). */
export type Negocio = 'VILLADENT' | 'VILLAMAX'

export interface SedeSesion {
  /** Identificador de la sede en la API (p. ej. "sede-chilca"). */
  id: string
  nombre: string
  /** Marca de la sede: las de Villamax se distinguen en índigo. */
  marca: Negocio
}

export interface UsuarioSesion {
  /** UUID del usuario (GET /auth/me). */
  id?: string
  nombre: string
  correo?: string
  rol: Rol
  /** Sedes a las que el usuario tiene acceso. */
  sedes: SedeSesion[]
}

export interface Sesion {
  token: string
  usuario: UsuarioSesion
  /** Sede elegida en el selector de la cabecera. */
  sedeActivaId: string | null
  /**
   * Negocio sobre el que trabaja quien ve más de uno (administrador general). Se envía en X-Negocio para que
   * "todas las sedes" signifique "todas las del negocio".
   */
  negocioActivo?: Negocio
  /**
   * Estado del 2FA deducido del login (la API no lo expone en /auth/me): 'activo' si se entró con segundo
   * factor o se acaba de activar; 'pendiente' si el login trajo requiereConfigurar2fa; 'inactivo' en el resto.
   */
  segundoFactor?: 'activo' | 'inactivo' | 'pendiente'
  /** true si la sesión viene de src/dev-fixtures (solo desarrollo). */
  demo?: boolean
}

/**
 * Se guarda en sessionStorage (se borra al cerrar la pestaña) y no en localStorage, para que un equipo
 * compartido de recepción no conserve la sesión. La política definitiva (cookie httpOnly/refresh) la decide
 * el backend; ver docs/frontend/brechas-api.md.
 */
const CLAVE = 'odontomax.sesion'
const TOKEN_DEMO_INVALIDO = 'dev-fixture-sin-valor'

type Oyente = () => void
const oyentes = new Set<Oyente>()
let cache: Sesion | null | undefined

function almacen(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.sessionStorage
  } catch {
    return null
  }
}

function esSesionValida(v: unknown): v is Sesion {
  if (!v || typeof v !== 'object') return false
  const s = v as Partial<Sesion>
  return (
    typeof s.token === 'string' &&
    s.token !== TOKEN_DEMO_INVALIDO &&
    !!s.usuario &&
    typeof s.usuario.nombre === 'string' &&
    esRol(s.usuario.rol) &&
    Array.isArray(s.usuario.sedes)
  )
}

/** Negocios distintos de las sedes, con Villadent primero. */
export function negociosDe(sedes: readonly SedeSesion[]): Negocio[] {
  const marcas = new Set(sedes.map((s) => s.marca))
  return (['VILLADENT', 'VILLAMAX'] as const).filter((n) => marcas.has(n))
}

/**
 * Garantiza el invariante absoluto de sesión:
 * Es IMPOSIBLE que quede una sede de un negocio con el menú de otro.
 * Si sedeActivaId no pertenece a negocioActivo, se reasigna a la sede por defecto de ese negocio.
 */
export function garantizarInvarianteSesion(sesion: Sesion): Sesion {
  const sedesLimpias = (sesion.usuario.sedes ?? []).filter((s) => s.id !== 'sede-chilca-2')
  const negocios = negociosDe(sedesLimpias)
  if (negocios.length === 0) {
    return { ...sesion, usuario: { ...sesion.usuario, sedes: sedesLimpias } }
  }

  // 1. Negocio activo válido
  const negocioActivo =
    sesion.negocioActivo && negocios.includes(sesion.negocioActivo)
      ? sesion.negocioActivo
      : (sedesLimpias.find((s) => s.id === sesion.sedeActivaId)?.marca ?? negocios[0])

  const sedesDelNegocio = sedesLimpias.filter((s) => s.marca === negocioActivo)

  // 2. Sede activa que obligatoriamente pertenece al negocio activo
  const sedeIdInicial = sesion.sedeActivaId === 'sede-chilca-2' ? 'sede-chilca-1' : sesion.sedeActivaId
  let sedeActivaId = sedeIdInicial

  const esAdminGeneral = sesion.usuario.rol === 'ADMIN_GENERAL'
  const puedeTodas = esAdminGeneral && sedesDelNegocio.length > 1

  if (sedeActivaId !== null) {
    const pertenece = sedesDelNegocio.some((s) => s.id === sedeActivaId)
    if (!pertenece) {
      sedeActivaId = puedeTodas ? (negocioActivo === 'VILLADENT' ? null : (sedesDelNegocio[0]?.id ?? null)) : (sedesDelNegocio[0]?.id ?? null)
    }
  } else if (!puedeTodas) {
    sedeActivaId = sedesDelNegocio[0]?.id ?? null
  }

  return {
    ...sesion,
    usuario: { ...sesion.usuario, sedes: sedesLimpias },
    negocioActivo,
    sedeActivaId,
  }
}

function leer(): Sesion | null {
  const crudo = almacen()?.getItem(CLAVE)
  if (!crudo) return null
  try {
    const v: unknown = JSON.parse(crudo)
    return esSesionValida(v) ? garantizarInvarianteSesion(v) : null
  } catch {
    return null
  }
}

function notificar() {
  for (const o of oyentes) o()
}

export function obtenerSesion(): Sesion | null {
  if (cache === undefined) cache = leer()
  if (cache?.token === TOKEN_DEMO_INVALIDO) {
    cache = null
    almacen()?.removeItem(CLAVE)
  }
  return cache ?? null
}

export function guardarSesion(sesion: Sesion): void {
  const normalizada = garantizarInvarianteSesion(sesion)
  const sedesDelNegocio = (normalizada.usuario.sedes ?? []).filter((s) => s.marca === normalizada.negocioActivo)
  const sedeActivaId =
    normalizada.sedeActivaId !== null && sedesDelNegocio.some((s) => s.id === normalizada.sedeActivaId)
      ? normalizada.sedeActivaId
      : (sedesDelNegocio[0]?.id ?? null)

  cache = {
    ...normalizada,
    sedeActivaId,
  }
  almacen()?.setItem(CLAVE, JSON.stringify(cache))
  notificar()
}

export function limpiarSesion(): void {
  cache = null
  almacen()?.removeItem(CLAVE)
  notificar()
}

export function cambiarSedeActiva(sedeId: string | null): void {
  const actual = obtenerSesion()
  if (!actual) return

  if (sedeId === null) {
    // "Todas" las sedes del negocio activo
    cache = {
      ...actual,
      sedeActivaId: null,
    }
    almacen()?.setItem(CLAVE, JSON.stringify(cache))
    notificar()
    return
  }

  const sede = actual.usuario.sedes.find((s) => s.id === sedeId)
  if (!sede) return

  // Invariante: cambiar a una sede sincroniza automáticamente el negocio activo con su marca,
  // impidiendo que una sede quede con el menú de otro negocio.
  cache = {
    ...actual,
    negocioActivo: sede.marca,
    sedeActivaId: sede.id,
  }
  almacen()?.setItem(CLAVE, JSON.stringify(cache))
  notificar()
}

/**
 * Cambia el negocio activo. La sede activa pasa a "todas las del negocio" (null) o, si el negocio tiene una sola
 * sede, a esa sede. Quien llama debe refrescar los datos: los de un negocio no sirven para el otro.
 */
export function cambiarNegocioActivo(negocio: Negocio): void {
  const actual = obtenerSesion()
  if (!actual || !negociosDe(actual.usuario.sedes).includes(negocio)) return
  const sedesDelNegocio = actual.usuario.sedes.filter((s) => s.marca === negocio)
  cache = {
    ...actual,
    negocioActivo: negocio,
    sedeActivaId: negocio === 'VILLADENT' ? null : (sedesDelNegocio[0]?.id ?? null),
  }
  almacen()?.setItem(CLAVE, JSON.stringify(cache))
  notificar()
}

/** Negocio para el header X-Negocio. El servidor solo lo usa para acotar "todas las sedes" y para elegir el catálogo. */
export function obtenerNegocioActivo(): Negocio | null {
  const sesion = obtenerSesion()
  if (!sesion) return null
  return sesion.negocioActivo ?? null
}

export function obtenerToken(): string | null {
  return obtenerSesion()?.token ?? null
}

function suscribir(oyente: Oyente) {
  oyentes.add(oyente)
  return () => oyentes.delete(oyente)
}

export function useSesion(): Sesion | null {
  return useSyncExternalStore(suscribir, obtenerSesion, obtenerSesion)
}

/** Solo para tests: olvida la caché en memoria. */
export function _reiniciarCacheSesion(): void {
  cache = undefined
}