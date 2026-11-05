/**
 * Cliente tipado de la API, construido sobre los tipos generados desde docs/api/openapi.json
 * (`npm run generate:api` → schema.d.ts) y sobre el cliente http existente (JWT, 401/403/429).
 *
 * Uso:
 *   const r = await apiTipada.post('/auth/login', { cuerpo: { email, password }, sinRedireccion: true })
 *   const p = await apiTipada.get('/pacientes/{id}', { path: { id } })
 *
 * La ruta, los parámetros, el cuerpo y la respuesta salen del contrato. Para los endpoints sin esquema tipado
 * (brechas-api.md, Parte A) manda tipos-manuales.ts.
 */
import { api, type ClienteHttp, type OpcionesPeticion } from '@/shared/http'
import type { components, paths } from './schema'
import type { CuerposManuales, RespuestasManuales } from './tipos-manuales'

export type Esquema<N extends keyof components['schemas']> = components['schemas'][N]

export type Metodo = 'get' | 'post' | 'put' | 'patch' | 'delete'

/** Rutas del contrato que admiten el método M. */
export type RutasCon<M extends Metodo> = {
  [P in keyof paths]-?: [NonNullable<paths[P][M]>] extends [never] ? never : P
}[keyof paths]

type Operacion<M extends Metodo, P extends RutasCon<M>> = NonNullable<paths[P][M]>

/** Clave "MÉTODO /ruta" (la de tipos-manuales.ts). */
type Clave<M extends Metodo, P extends string> = `${Uppercase<M>} ${P}`

type ContenidoJson<C> = C extends { 'application/json': infer B } ? B : C extends { '*/*': infer B } ? B : never

type CuerpoContrato<O> = O extends { requestBody?: { content: infer C } } ? ContenidoJson<C> : never

type RespuestaContrato<O> = O extends { responses: infer R }
  ? R extends { 200: { content: infer C } }
    ? ContenidoJson<C>
    : R extends { 201: { content: infer C } }
      ? ContenidoJson<C>
      : undefined
  : unknown

export type Cuerpo<M extends Metodo, P extends RutasCon<M>> =
  Clave<M, P> extends keyof CuerposManuales ? CuerposManuales[Clave<M, P>] : CuerpoContrato<Operacion<M, P>>

export type Respuesta<M extends Metodo, P extends RutasCon<M>> =
  Clave<M, P> extends keyof RespuestasManuales
    ? RespuestasManuales[Clave<M, P>]
    : RespuestaContrato<Operacion<M, P>>

type ParamsDe<O, K extends 'path' | 'query'> = O extends { parameters: { [k in K]?: infer X } }
  ? [X] extends [never]
    ? never
    : NonNullable<X>
  : never

export interface OpcionesOperacion<M extends Metodo, P extends RutasCon<M>>
  extends Omit<OpcionesPeticion, 'cuerpo' | 'method'> {
  path?: ParamsDe<Operacion<M, P>, 'path'>
  query?: ParamsDe<Operacion<M, P>, 'query'>
  cuerpo?: Cuerpo<M, P>
}

type ValorQuery = string | number | boolean | null | undefined

/** Sustituye {param} en la ruta y añade la query (se omiten null/undefined; los arreglos se repiten). */
export function construirRuta(
  plantilla: string,
  path?: Record<string, string | number>,
  query?: Record<string, ValorQuery | ValorQuery[]>,
): string {
  const ruta = plantilla.replace(/\{([^}]+)\}/g, (_, nombre: string) => {
    const valor = path?.[nombre]
    if (valor === undefined || valor === null || valor === '') {
      throw new Error(`Falta el parámetro de ruta "${nombre}" para ${plantilla}`)
    }
    return encodeURIComponent(String(valor))
  })
  if (!query) return ruta
  const qs = new URLSearchParams()
  for (const [clave, valor] of Object.entries(query)) {
    for (const v of Array.isArray(valor) ? valor : [valor]) {
      if (v !== undefined && v !== null) qs.append(clave, String(v))
    }
  }
  const texto = qs.toString()
  return texto ? `${ruta}?${texto}` : ruta
}

export function crearClienteApi(http: ClienteHttp) {
  function operacion<M extends Metodo>(metodo: M) {
    return <P extends RutasCon<M>>(ruta: P, opciones: OpcionesOperacion<M, P> = {}) => {
      const { path, query, cuerpo, ...resto } = opciones
      const url = construirRuta(
        ruta,
        path as Record<string, string | number> | undefined,
        query as Record<string, ValorQuery | ValorQuery[]> | undefined,
      )
      return http.peticion<Respuesta<M, P>>(url, { ...resto, method: metodo.toUpperCase(), cuerpo })
    }
  }

  return {
    get: operacion('get'),
    post: operacion('post'),
    put: operacion('put'),
    patch: operacion('patch'),
    delete: operacion('delete'),
  }
}

export type ClienteApi = ReturnType<typeof crearClienteApi>

/** Cliente tipado de la aplicación (usa el cliente http global con la sesión y los manejadores 401/403). */
export const apiTipada = crearClienteApi(api)

/* Comprobación en tiempo de compilación: cada entrada manual corresponde a una operación real del contrato. */
type ClavesContrato = {
  [M in Metodo]: { [P in RutasCon<M>]: Clave<M, P> }[RutasCon<M>]
}[Metodo]
type _ManualesExisten = Exclude<keyof RespuestasManuales | keyof CuerposManuales, ClavesContrato>
const _comprobarManuales: [_ManualesExisten] extends [never] ? true : _ManualesExisten = true
void _comprobarManuales
