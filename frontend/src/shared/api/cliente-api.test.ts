import { describe, expect, expectTypeOf, it, vi } from 'vitest'
import { crearClienteHttp } from '@/shared/http/cliente'
import { NoAutorizadoError } from '@/shared/http/errores'
import { construirRuta, crearClienteApi, type Esquema, type Respuesta } from './cliente-api'
import type { RespuestaEstado2fa, RespuestaMensaje } from './tipos-manuales'

function preparar(cuerpo: unknown, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
      status,
      headers: cuerpo === undefined ? {} : { 'Content-Type': 'application/json' },
    }),
  )
  const alNoAutorizado = vi.fn()
  const http = crearClienteHttp({ baseUrl: '/api/v1', obtenerToken: () => 'jwt', alNoAutorizado, fetch: fetchMock })
  return { api: crearClienteApi(http), fetchMock, alNoAutorizado }
}

describe('construirRuta', () => {
  it('sustituye parámetros de ruta codificados y añade la query sin vacíos', () => {
    expect(construirRuta('/pacientes/{id}', { id: 'a b/c' })).toBe('/pacientes/a%20b%2Fc')
    expect(construirRuta('/pacientes', undefined, { busqueda: 'Ñuñez', page: 0, sedeId: undefined })).toBe(
      '/pacientes?busqueda=%C3%91u%C3%B1ez&page=0',
    )
    expect(construirRuta('/x', undefined, { ids: [1, 2] })).toBe('/x?ids=1&ids=2')
  })

  it('falla si falta un parámetro de ruta', () => {
    expect(() => construirRuta('/pacientes/{id}', {})).toThrow(/id/)
  })
})

describe('cliente tipado', () => {
  it('POST /auth/login envía el cuerpo del contrato y devuelve LoginResponseDTO', async () => {
    const { api, fetchMock } = preparar({ token: 't', tokenTipo: 'Bearer', expiracionMs: 1000 })
    const r = await api.post('/auth/login', { cuerpo: { email: 'a@b.pe', password: 'x' }, sinRedireccion: true })
    expectTypeOf(r).toEqualTypeOf<Esquema<'LoginResponseDTO'>>()
    expect(r.token).toBe('t')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/auth/login')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ email: 'a@b.pe', password: 'x' })
  })

  it('usa los tipos manuales para los endpoints sin esquema (brechas-api.md A.1)', () => {
    expectTypeOf<Respuesta<'post', '/auth/cambiar-password'>>().toEqualTypeOf<RespuestaMensaje>()
    expectTypeOf<Respuesta<'post', '/auth/2fa/desactivar'>>().toMatchTypeOf<RespuestaEstado2fa>()
    expectTypeOf<Respuesta<'get', '/auth/me'>>().toEqualTypeOf<Esquema<'UsuarioDTO'>>()
  })

  it('GET con parámetros de ruta y query', async () => {
    const { api, fetchMock } = preparar([])
    await api.get('/pacientes', { query: { busqueda: 'ana', page: 1 } })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/pacientes?busqueda=ana&page=1')
  })

  it('conserva el manejo de errores del cliente http (401 → NoAutorizadoError)', async () => {
    const { api, alNoAutorizado } = preparar({ mensaje: 'Token JWT inválido o expirado' }, 401)
    await expect(api.get('/auth/me')).rejects.toBeInstanceOf(NoAutorizadoError)
    expect(alNoAutorizado).toHaveBeenCalledOnce()
  })
})
