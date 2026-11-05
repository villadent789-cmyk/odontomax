/** Error HTTP genérico con el cuerpo de la respuesta del backend (si lo hay). */
export class HttpError extends Error {
  readonly status: number
  readonly cuerpo: unknown

  constructor(status: number, mensaje: string, cuerpo?: unknown) {
    super(mensaje)
    this.name = 'HttpError'
    this.status = status
    this.cuerpo = cuerpo
  }
}

/** 401: sesión ausente o vencida. */
export class NoAutorizadoError extends HttpError {
  constructor(mensaje = 'Tu sesión venció. Vuelve a iniciar sesión.', cuerpo?: unknown) {
    super(401, mensaje, cuerpo)
    this.name = 'NoAutorizadoError'
  }
}

/** 403: el rol no tiene permiso para la operación. */
export class SinPermisoError extends HttpError {
  constructor(mensaje = 'No tienes permiso para realizar esta acción.', cuerpo?: unknown) {
    super(403, mensaje, cuerpo)
    this.name = 'SinPermisoError'
  }
}

/** 429: demasiados intentos (fuerza bruta o límite de peticiones). */
export class BloqueoError extends HttpError {
  /** Segundos de espera según la cabecera Retry-After (null si el backend no la envía). */
  readonly reintentarEnSegundos: number | null

  /**
   * @param mensajeBackend texto del cuerpo (`mensaje`). El backend lo envía con los minutos ("Intente de nuevo en
   *   N minutos") y no manda Retry-After, así que tiene prioridad sobre el mensaje calculado.
   */
  constructor(reintentarEnSegundos: number | null, cuerpo?: unknown, mensajeBackend?: string | null) {
    super(429, mensajeBackend || mensajeBloqueo(reintentarEnSegundos), cuerpo)
    this.name = 'BloqueoError'
    this.reintentarEnSegundos = reintentarEnSegundos
  }
}

export function mensajeBloqueo(segundos: number | null): string {
  if (segundos === null || segundos <= 0) {
    return 'Demasiados intentos. Por seguridad, espera unos minutos antes de volver a intentarlo.'
  }
  if (segundos < 60) {
    return `Demasiados intentos. Intenta de nuevo en ${segundos} ${segundos === 1 ? 'segundo' : 'segundos'}.`
  }
  const minutos = Math.ceil(segundos / 60)
  return `Demasiados intentos. Intenta de nuevo en ${minutos} ${minutos === 1 ? 'minuto' : 'minutos'}.`
}

/** Interpreta Retry-After: segundos ("900") o fecha HTTP. */
export function leerRetryAfter(valor: string | null, ahora: number = Date.now()): number | null {
  if (!valor) return null
  const segundos = Number(valor)
  if (Number.isFinite(segundos)) return Math.max(0, Math.round(segundos))
  const fecha = Date.parse(valor)
  if (Number.isNaN(fecha)) return null
  return Math.max(0, Math.round((fecha - ahora) / 1000))
}

/**
 * Texto de un error capturado (`catch` o `onError`, siempre `unknown`): su `message` si lo trae y, si no,
 * el texto por defecto de la pantalla.
 */
export function textoDeError(error: unknown, porDefecto: string): string {
  if (typeof error === 'object' && error !== null && 'message' in error) {
    const { message } = error
    if (typeof message === 'string' && message !== '') return message
  }
  return porDefecto
}
