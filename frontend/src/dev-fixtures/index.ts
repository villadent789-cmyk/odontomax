/**
 * ⚠ DATOS DE EJEMPLO SOLO PARA DESARROLLO ⚠
 *
 * Alimentan la carcasa del sistema mientras no exista el cliente generado desde OpenAPI ni la pantalla de
 * login (B1). Este módulo solo se importa detrás de `import.meta.env.DEV` (import dinámico), así que no entra
 * en el build de producción. No son datos reales ni deben copiarse a otras partes del código.
 */
import type { Rol, SedeSesion, Sesion } from '@/shared/auth'

export const SEDES_DEMO: SedeSesion[] = [
  { id: 'sede-demo-1', nombre: 'Villadent Chilca', marca: 'VILLADENT' },
  { id: 'sede-demo-2', nombre: 'Huancayo Centro', marca: 'VILLADENT' },
  { id: 'sede-demo-3', nombre: 'Villamax Chilca', marca: 'VILLAMAX' },
]

const NOMBRES_DEMO: Record<Rol, string> = {
  ADMIN_GENERAL: 'Doctor A (Director General)',
  ADMIN_SEDE: 'Doctor B (Director Centro)',
  ODONTOLOGO: 'Odontólogo Clínico (demo)',
  RECEPCION: 'Recepción Chilca (demo)',
}

export function sesionDemo(rol: Rol = 'RECEPCION'): Sesion {
  // Solo ADMIN_GENERAL ve todas las sedes; el resto, la suya.
  const sedes = rol === 'ADMIN_GENERAL' ? SEDES_DEMO : SEDES_DEMO.slice(0, 1)
  return {
    token: 'dev-fixture-sin-valor',
    usuario: { nombre: NOMBRES_DEMO[rol], correo: 'demo@ejemplo.invalid', rol, sedes },
    sedeActivaId: sedes[0].id,
    demo: true,
  }
}
