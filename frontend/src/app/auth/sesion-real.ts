import { esRol, guardarSesion, obtenerSesion, type SedeSesion, type Sesion } from '@/shared/auth'
import { perfil, sedesActivas, type SedeApi } from './api-auth'

export class RolNoReconocidoError extends Error {
  constructor() {
    super('Tu cuenta no tiene un rol con acceso al sistema interno. Comunícate con la administración.')
    this.name = 'RolNoReconocidoError'
  }
}

/** La marca de la sede (Villadent o Villamax) viene de `unidadDeNegocio`; sin dato se trata como Villadent. */
function aSedeSesion(s: SedeApi): SedeSesion | null {
  if (!s.id) return null
  return { id: s.id, nombre: s.nombre ?? s.id, marca: s.unidadDeNegocio === 'VILLAMAX' ? 'VILLAMAX' : 'VILLADENT' }
}

/**
 * Sedes del usuario. ADMIN_GENERAL no está atado a una sede (sedeId null): ve todas las activas. El resto, la
 * suya. GET /sedes es público; si falla, se usa el identificador como nombre para no bloquear la entrada.
 */
async function sedesDelUsuario(sedeId: string | undefined): Promise<SedeSesion[]> {
  const normalizadoId = sedeId === 'sede-chilca-2' ? 'sede-chilca-1' : sedeId
  let activas: SedeSesion[] = []
  try {
    activas = (await sedesActivas())
      .map(aSedeSesion)
      .filter((s): s is SedeSesion => s !== null && s.id !== 'sede-chilca-2')
  } catch {
    activas = []
  }
  if (!normalizadoId) return activas
  const propia = activas.find((s) => s.id === normalizadoId)
  return [propia ?? { id: normalizadoId, nombre: normalizadoId, marca: 'VILLADENT' }]
}

/**
 * Con el JWT definitivo: GET /auth/me (nombre, rol, sede) + sedes, y guarda la sesión real.
 * Si algo falla no se guarda nada (el error lo muestra la pantalla de acceso).
 */
export async function establecerSesion(
  token: string,
  segundoFactor: NonNullable<Sesion['segundoFactor']>,
): Promise<Sesion> {
  const usuario = await perfil(token)
  if (!esRol(usuario.rol)) throw new RolNoReconocidoError()
  const sedes = await sedesDelUsuario(usuario.sedeId)
  const sesion: Sesion = {
    token,
    usuario: {
      id: usuario.id,
      nombre: usuario.nombre ?? usuario.email ?? 'Usuario',
      correo: usuario.email,
      rol: usuario.rol,
      rolesAdicionales: (usuario.rolesAdicionales ?? []).filter(esRol),
      sedes,
    },
    sedeActivaId: usuario.sedeId ?? null,
    segundoFactor,
  }
  guardarSesion(sesion)
  return sesion
}

/** Solo acepta destinos internos del sistema (evita redirecciones abiertas con ?redirect=). */
export function destinoSeguro(redirect: string | undefined): string {
  if (redirect && redirect.startsWith('/app') && !redirect.startsWith('/app/login') && !redirect.startsWith('//')) {
    return redirect
  }
  return '/app'
}

/**
 * Vuelve a leer las sedes activas y actualiza la lista de la sesión (la que usa el selector de sede de la cabecera).
 * Solo para el administrador general, que ve todas; los demás roles conservan su sede. Si la lectura no devuelve
 * sedes no se pisa lo que ya había. guardarSesion elige otra sede activa si la actual dejó de estar disponible.
 */
export async function refrescarSedesSesion(): Promise<void> {
  const actual = obtenerSesion()
  if (!actual || actual.usuario.rol !== 'ADMIN_GENERAL') return
  const sedes = await sedesDelUsuario(undefined)
  if (sedes.length === 0) return
  guardarSesion({ ...actual, usuario: { ...actual.usuario, sedes } })
}
