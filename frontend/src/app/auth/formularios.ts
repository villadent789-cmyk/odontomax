import { z } from 'zod'
import type { AvisoAcceso } from './flujo'

/** Límites del contrato para password_nuevo (4-128). La política de historial la valida el backend. */
export const esquemaPasswordNuevo = z
  .object({
    password: z
      .string()
      .min(4, 'La contraseña debe tener al menos 4 caracteres.')
      .max(128, 'La contraseña no puede superar los 128 caracteres.'),
    confirmacion: z.string().min(1, 'Repite la nueva contraseña.'),
  })
  .refine((d) => d.password === d.confirmacion, {
    path: ['confirmacion'],
    message: 'Las contraseñas no coinciden.',
  })

/** Fuera del login un error no es "de autenticación": título neutro (bloqueo y política conservan el suyo). */
export function tituloErrorOperacion(aviso: AvisoAcceso): string | undefined {
  return aviso.tipo === 'error' ? 'No se pudo completar la operación' : undefined
}
