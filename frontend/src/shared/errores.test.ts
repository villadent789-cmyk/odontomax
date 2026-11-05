import { describe, expect, it } from 'vitest'
import { HttpError, SinPermisoError } from './http'
import { esSinPermiso, mensajeError } from './errores'

describe('shared/errores', () => {
  it('formatea SinPermisoError con prefijo', () => {
    const error = new SinPermisoError('Acción restringida')
    expect(mensajeError(error)).toBe('Sin permiso: Acción restringida')
    expect(esSinPermiso(error)).toBe(true)
  })

  it('extrae mensaje de HttpError', () => {
    const error = new HttpError(400, 'Datos inválidos')
    expect(mensajeError(error)).toBe('Datos inválidos')
    expect(esSinPermiso(error)).toBe(false)
  })

  it('retorna texto por defecto para errores desconocidos', () => {
    expect(mensajeError(new Error('crash'))).toBe(
      'No se pudo completar la operación. Revisa tu conexión e inténtalo de nuevo.'
    )
    expect(mensajeError(null)).toBe(
      'No se pudo completar la operación. Revisa tu conexión e inténtalo de nuevo.'
    )
  })
})
