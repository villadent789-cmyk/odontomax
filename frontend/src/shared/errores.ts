import { HttpError, SinPermisoError } from './http'

export function mensajeError(error: unknown): string {
  if (error instanceof SinPermisoError) return `Sin permiso: ${error.message}`
  if (error instanceof HttpError) return error.message
  return 'No se pudo completar la operación. Revisa tu conexión e inténtalo de nuevo.'
}

export function esSinPermiso(error: unknown): boolean {
  return error instanceof SinPermisoError
}
