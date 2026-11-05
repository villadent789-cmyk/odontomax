/**
 * Roles y permisos de interfaz.
 *
 * IMPORTANTE: esta tabla solo decide qué se MUESTRA (menú, accesos rápidos, rutas). La seguridad real la
 * decide el backend en cada endpoint (@PreAuthorize); si el backend responde 403, el cliente http lleva a
 * la pantalla "Sin permiso". Por eso la tabla es la más prudente: ante la duda, un rol no ve la opción.
 */

export const ROLES = ['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO', 'RECEPCION'] as const
export type Rol = (typeof ROLES)[number]

export const NOMBRE_ROL: Record<Rol, string> = {
  ADMIN_GENERAL: 'Administración general',
  ADMIN_SEDE: 'Administración de sede',
  ODONTOLOGO: 'Odontología',
  RECEPCION: 'Recepción y admisión',
}

export type Recurso =
  | 'inicio'
  | 'agenda'
  | 'pacientes'
  | 'caja'
  | 'inventario'
  | 'laboratorio'
  | 'villamax'
  | 'estadisticas'
  | 'configuracion'
  | 'registrarPaciente'
  | 'agendarCita'
  | 'bandeja'
  | 'gestionarCita'
  | 'atenderCita'
  | 'emitirReceta'
  | 'gestionarTratamiento'
  | 'nuevoTratamiento'
  | 'auditoria'

const TODOS: readonly Rol[] = ROLES

export const PERMISOS: Record<Recurso, readonly Rol[]> = {
  inicio: TODOS,
  agenda: TODOS,
  pacientes: TODOS,
  // Caja y facturación: operación de recepción y administración, no del odontólogo.
  caja: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION'],
  // Inventario y almacén clínico: compartido por todas las áreas con diferentes permisos de acción.
  inventario: TODOS,
  // Laboratorio dental y prótesis: compartido por clínica, odontólogos y recepción.
  laboratorio: TODOS,
  // Derivaciones y comisiones radiológicas: solo administración y doctores; recepción no ve comisiones.
  villamax: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'],
  // Reportes: el backend los restringe a ADMIN_GENERAL y ADMIN_SEDE.
  estadisticas: ['ADMIN_GENERAL', 'ADMIN_SEDE'],
  // Administración de usuarios, doctores y horarios: /usuarios y /especialistas (escritura) son solo ADMIN_GENERAL en el
  // backend. Mostrarla a ADMIN_SEDE era un callejón sin salida: veía el menú y recibía "Sin permiso".
  configuracion: ['ADMIN_GENERAL'],
  registrarPaciente: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION'],
  agendarCita: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION'],
  // B2 · Bandeja del día: mesa de operaciones de recepción y administración
  bandeja: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION'],
  // Confirmar, marcar llegada, no asistió, cancelar y reprogramar citas.
  gestionarCita: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'RECEPCION'],
  // Marcar una cita "en sala" como atendida: también el odontólogo que la atiende.
  atenderCita: TODOS,
  // POST /recetas/pdf: el backend lo limita a administración y odontólogos (recepción recibiría 403).
  emitirReceta: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'],
  // PATCH /tratamientos/{id} (avanzar sesiones, finalizar): el backend lo limita a administración y odontólogos.
  gestionarTratamiento: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'],
  // POST /tratamientos y /presupuestos: administración y odontólogos.
  nuevoTratamiento: ['ADMIN_GENERAL', 'ADMIN_SEDE', 'ODONTOLOGO'],
  // GET /auditoria es exclusivo de ADMIN_GENERAL en el backend.
  auditoria: ['ADMIN_GENERAL'],
}

export function esRol(valor: unknown): valor is Rol {
  return typeof valor === 'string' && (ROLES as readonly string[]).includes(valor)
}

export function puede(rol: Rol | null | undefined, recurso: Recurso): boolean {
  if (!rol) return false
  if (rol === 'ADMIN_GENERAL') return true
  return PERMISOS[recurso]?.includes(rol) ?? false
}
