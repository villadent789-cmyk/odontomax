import { describe, expect, it, vi } from 'vitest'
import { crearClienteHttp } from './cliente'
import { BloqueoError, HttpError, NoAutorizadoError, SinPermisoError, leerRetryAfter, mensajeBloqueo } from './errores'

function respuesta(status: number, cuerpo?: unknown, cabeceras: Record<string, string> = {}) {
  const body = cuerpo === undefined ? null : JSON.stringify(cuerpo)
  return new Response(body, {
    status,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...cabeceras },
  })
}

function preparar(r: Response, token: string | null = 'jwt-de-prueba') {
  const fetchMock = vi.fn().mockResolvedValue(r)
  const alNoAutorizado = vi.fn()
  const alProhibido = vi.fn()
  const cliente = crearClienteHttp({
    baseUrl: '/api/v1',
    obtenerToken: () => token,
    alNoAutorizado,
    alProhibido,
    fetch: fetchMock,
  })
  return { cliente, fetchMock, alNoAutorizado, alProhibido }
}

describe('cliente http', () => {
  it('envía el JWT y devuelve el JSON', async () => {
    const { cliente, fetchMock } = preparar(respuesta(200, { id: 1 }))
    await expect(cliente.get('/sedes')).resolves.toEqual({ id: 1 })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/v1/sedes')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer jwt-de-prueba')
  })

  it('envía X-Negocio solo cuando hay un negocio activo', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(respuesta(200, {})))
    const conNegocio = crearClienteHttp({ baseUrl: '/api/v1', obtenerToken: () => 'jwt', obtenerNegocio: () => 'VILLAMAX', fetch: fetchMock })
    await conNegocio.get('/pacientes')
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get('X-Negocio')).toBe('VILLAMAX')

    const sinNegocio = crearClienteHttp({ baseUrl: '/api/v1', obtenerToken: () => 'jwt', obtenerNegocio: () => null, fetch: fetchMock })
    await sinNegocio.get('/pacientes')
    expect(new Headers(fetchMock.mock.calls[1][1].headers).has('X-Negocio')).toBe(false)
  })

  it('no envía Authorization sin sesión y serializa el cuerpo JSON', async () => {
    const { cliente, fetchMock } = preparar(respuesta(201, { ok: true }), null)
    await cliente.post('/reservas-publicas', { dni: '12345678' })
    const [, init] = fetchMock.mock.calls[0]
    const h = new Headers(init.headers)
    expect(h.has('Authorization')).toBe(false)
    expect(h.get('Content-Type')).toBe('application/json')
    expect(init.body).toBe('{"dni":"12345678"}')
    expect(init.method).toBe('POST')
  })

  it('binario: devuelve el archivo como Blob, intacto, y pide application/pdf', async () => {
    const bytes = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x00, 0xff, 0xfe])
    const { cliente, fetchMock } = preparar(new Response(bytes, { status: 200, headers: { 'Content-Type': 'application/pdf' } }))
    const pdf = await cliente.post<Blob>('/recetas/pdf', { a: 1 }, { binario: true })
    expect(pdf).toBeInstanceOf(Blob)
    expect(new Uint8Array(await pdf.arrayBuffer())).toEqual(bytes)
    const [, init] = fetchMock.mock.calls[0]
    expect(new Headers(init.headers).get('Accept')).toContain('application/pdf')
    expect('binario' in init).toBe(false)
  })

  it('binario: un error sigue llegando como JSON y se lanza igual que siempre', async () => {
    const { cliente } = preparar(respuesta(400, { message: 'Debe incluir al menos un medicamento o indicación' }))
    await expect(cliente.post('/recetas/pdf', {}, { binario: true })).rejects.toMatchObject({
      status: 400,
      message: 'Debe incluir al menos un medicamento o indicación',
    })
  })

  it('204 devuelve undefined', async () => {
    const { cliente } = preparar(new Response(null, { status: 204 }))
    await expect(cliente.delete('/citas/1')).resolves.toBeUndefined()
  })

  it('401 → avisa para ir al login y lanza NoAutorizadoError', async () => {
    const { cliente, alNoAutorizado, alProhibido } = preparar(respuesta(401, { message: 'Token vencido' }))
    const error = await cliente.get('/pacientes').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(NoAutorizadoError)
    expect((error as NoAutorizadoError).message).toBe('Token vencido')
    expect(alNoAutorizado).toHaveBeenCalledOnce()
    expect(alProhibido).not.toHaveBeenCalled()
  })

  it('401 con sinRedireccion (login) no redirige pero lanza el error', async () => {
    const { cliente, alNoAutorizado } = preparar(respuesta(401))
    await expect(cliente.post('/auth/login', {}, { sinRedireccion: true })).rejects.toBeInstanceOf(NoAutorizadoError)
    expect(alNoAutorizado).not.toHaveBeenCalled()
  })

  it('403 → avisa para mostrar "Sin permiso" y lanza SinPermisoError', async () => {
    const { cliente, alProhibido, alNoAutorizado } = preparar(respuesta(403))
    const error = await cliente.get('/reportes/ventas').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(SinPermisoError)
    expect((error as SinPermisoError).message).toBe('No tienes permiso para realizar esta acción.')
    expect(alProhibido).toHaveBeenCalledOnce()
    expect(alNoAutorizado).not.toHaveBeenCalled()
  })

  it('429 → BloqueoError con el tiempo de Retry-After y mensaje de bloqueo', async () => {
    const { cliente, alNoAutorizado, alProhibido } = preparar(respuesta(429, undefined, { 'Retry-After': '900' }))
    const error = await cliente.post('/auth/login', {}).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(BloqueoError)
    expect((error as BloqueoError).reintentarEnSegundos).toBe(900)
    expect((error as BloqueoError).message).toBe('Demasiados intentos. Intenta de nuevo en 15 minutos.')
    expect(alNoAutorizado).not.toHaveBeenCalled()
    expect(alProhibido).not.toHaveBeenCalled()
  })

  it('429 sin Retry-After usa un mensaje genérico', async () => {
    const { cliente } = preparar(respuesta(429))
    const error = (await cliente.get('/x').catch((e: unknown) => e)) as BloqueoError
    expect(error.reintentarEnSegundos).toBeNull()
    expect(error.message).toMatch(/Demasiados intentos/)
  })

  it('429 con mensaje del backend usa ese texto (el backend no envía Retry-After)', async () => {
    const mensaje = 'Demasiados intentos fallidos de verificación 2FA. Intente de nuevo en 12 minutos.'
    const { cliente } = preparar(respuesta(429, { status: 429, mensaje }))
    const error = (await cliente.post('/auth/2fa/desactivar', {}).catch((e: unknown) => e)) as BloqueoError
    expect(error).toBeInstanceOf(BloqueoError)
    expect(error.message).toBe(mensaje)
  })

  it('otros errores lanzan HttpError con el mensaje del backend', async () => {
    const { cliente } = preparar(respuesta(409, { mensaje: 'El horario ya está ocupado' }))
    const error = (await cliente.post('/citas', {}).catch((e: unknown) => e)) as HttpError
    expect(error).toBeInstanceOf(HttpError)
    expect(error.status).toBe(409)
    expect(error.message).toBe('El horario ya está ocupado')
  })
})

describe('utilidades de bloqueo', () => {
  it('leerRetryAfter acepta segundos y fechas HTTP', () => {
    expect(leerRetryAfter('120')).toBe(120)
    expect(leerRetryAfter(null)).toBeNull()
    expect(leerRetryAfter('basura')).toBeNull()
    const ahora = Date.parse('2024-10-24T10:00:00Z')
    expect(leerRetryAfter('Thu, 24 Oct 2024 10:05:00 GMT', ahora)).toBe(300)
  })

  it('mensajeBloqueo usa singular y plural', () => {
    expect(mensajeBloqueo(60)).toBe('Demasiados intentos. Intenta de nuevo en 1 minuto.')
    expect(mensajeBloqueo(30)).toBe('Demasiados intentos. Intenta de nuevo en 30 segundos.')
    expect(mensajeBloqueo(61)).toBe('Demasiados intentos. Intenta de nuevo en 2 minutos.')
  })
})
