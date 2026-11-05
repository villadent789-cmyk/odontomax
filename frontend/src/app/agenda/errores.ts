import { HttpError } from '@/shared/http'
import { mensajeError } from '@/shared/errores'

export { esSinPermiso, mensajeError } from '@/shared/errores'

/** Un 409 del backend: colisión de horario (CitaColisionException) o de la base (solapamiento, sede/sillón). */
export function mensajeConflicto(error: unknown): string {
  const texto = mensajeError(error)
  if (error instanceof HttpError && error.status === 409 && /registro inexistente|duplica/.test(texto)) {
    return `${texto} Es probable que el horario se cruce con otra cita del especialista o del sillón: elige otro hueco.`
  }
  return texto
}
