import {
  BloqueoError,
  HttpError,
  NoAutorizadoError,
  SinPermisoError,
  leerRetryAfter,
} from './errores'

export interface OpcionesCliente {
  /** Prefijo de la API, p. ej. "/api/v1". */
  baseUrl: string
  /** Devuelve el JWT vigente o null. */
  obtenerToken: () => string | null
  /** Negocio activo (X-Negocio) para quien ve más de uno; null o ausente no envía el header. */
  obtenerNegocio?: () => string | null
  /** 401: se llama antes de lanzar el error (limpiar sesión e ir a login). */
  alNoAutorizado?: () => void
  /** 403: se llama antes de lanzar el error (ir a la pantalla "Sin permiso"). */
  alProhibido?: () => void
  fetch?: typeof fetch
}

export interface OpcionesPeticion extends Omit<RequestInit, 'body'> {
  /** Cuerpo: se serializa como JSON salvo que sea FormData/Blob/string. */
  cuerpo?: unknown
  /**
   * No redirigir en 401/403 (p. ej. en el propio login, donde 401 significa "credenciales incorrectas").
   * El error se lanza igual para que la pantalla lo muestre.
   */
  sinRedireccion?: boolean
  /**
   * La respuesta correcta es un archivo (p. ej. un PDF): se devuelve como Blob sin pasarla por texto. Los errores
   * siguen llegando como JSON y se tratan igual que en el resto de peticiones.
   */
  binario?: boolean
}

function mensajeDelCuerpo(cuerpo: unknown): string | null {
  if (cuerpo && typeof cuerpo === 'object') {
    const c = cuerpo as Record<string, unknown>
    for (const clave of ['mensaje', 'message', 'detail', 'error']) {
      if (typeof c[clave] === 'string' && c[clave]) return c[clave] as string
    }
  }
  return null
}

async function leerCuerpo(respuesta: Response): Promise<unknown> {
  if (respuesta.status === 204 || respuesta.status === 205) return undefined
  const texto = await respuesta.text()
  if (!texto) return undefined
  const tipo = respuesta.headers.get('content-type') ?? ''
  if (tipo.includes('json')) {
    try {
      return JSON.parse(texto)
    } catch {
      return texto
    }
  }
  return texto
}

export function crearClienteHttp(opciones: OpcionesCliente) {
  const hacerFetch = opciones.fetch ?? ((...args: Parameters<typeof fetch>) => fetch(...args))

  async function peticion<T>(ruta: string, { cuerpo, sinRedireccion, binario, headers, ...init }: OpcionesPeticion = {}) {
    const cabeceras = new Headers(headers)
    cabeceras.set('Accept', binario ? 'application/pdf, application/json' : 'application/json')
    const token = opciones.obtenerToken()
    // Una cabecera Authorization explícita (p. ej. GET /auth/me justo tras el login) tiene prioridad.
    if (token && !cabeceras.has('Authorization')) cabeceras.set('Authorization', `Bearer ${token}`)
    const negocio = opciones.obtenerNegocio?.()
    if (negocio && !cabeceras.has('X-Negocio')) cabeceras.set('X-Negocio', negocio)

    let body: BodyInit | undefined
    if (cuerpo !== undefined) {
      if (cuerpo instanceof FormData || cuerpo instanceof Blob || typeof cuerpo === 'string') {
        body = cuerpo
      } else {
        cabeceras.set('Content-Type', 'application/json')
        body = JSON.stringify(cuerpo)
      }
    }

    const respuesta = await hacerFetch(`${opciones.baseUrl}${ruta}`, { ...init, headers: cabeceras, body })
    if (binario && respuesta.ok) return (await respuesta.blob()) as T
    const datos = await leerCuerpo(respuesta)

    if (respuesta.ok) return datos as T

    const mensaje = mensajeDelCuerpo(datos)
    switch (respuesta.status) {
      case 401:
        if (!sinRedireccion) opciones.alNoAutorizado?.()
        throw new NoAutorizadoError(mensaje ?? undefined, datos)
      case 403:
        if (!sinRedireccion) opciones.alProhibido?.()
        throw new SinPermisoError(mensaje ?? undefined, datos)
      case 429:
        throw new BloqueoError(leerRetryAfter(respuesta.headers.get('Retry-After')), datos, mensaje)
      default:
        throw new HttpError(
          respuesta.status,
          mensaje ?? 'Ocurrió un error inesperado. Inténtalo de nuevo en unos momentos.',
          datos,
        )
    }
  }

  return {
    peticion,
    get: <T>(ruta: string, o?: OpcionesPeticion) => peticion<T>(ruta, { ...o, method: 'GET' }),
    post: <T>(ruta: string, cuerpo?: unknown, o?: OpcionesPeticion) =>
      peticion<T>(ruta, { ...o, method: 'POST', cuerpo }),
    put: <T>(ruta: string, cuerpo?: unknown, o?: OpcionesPeticion) =>
      peticion<T>(ruta, { ...o, method: 'PUT', cuerpo }),
    patch: <T>(ruta: string, cuerpo?: unknown, o?: OpcionesPeticion) =>
      peticion<T>(ruta, { ...o, method: 'PATCH', cuerpo }),
    delete: <T>(ruta: string, o?: OpcionesPeticion) => peticion<T>(ruta, { ...o, method: 'DELETE' }),
  }
}

export type ClienteHttp = ReturnType<typeof crearClienteHttp>
