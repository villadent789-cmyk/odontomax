/**
 * Datos de Administración (usuarios, doctores y horarios). Todo sale del contrato (docs/api/openapi.json):
 * GET|POST /usuarios, PATCH /usuarios/{id}/estado, GET /especialistas/admin, PUT /especialistas/{id} y
 * GET|PUT /especialistas/{id}/horarios. No se calcula nada que el backend no devuelva.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiTipada, type Esquema } from '@/shared/api'
import { HttpError } from '@/shared/http'

export type Usuario = Esquema<'UsuarioResponseDTO'>
export type CuerpoUsuario = Esquema<'UsuarioCreateRequestDTO'>
export type Doctor = Esquema<'EspecialistaResponseDTO'>
export type CuerpoDoctor = Esquema<'EspecialistaRequestDTO'>
export type HorarioDoctor = Esquema<'HorarioEspecialistaResponseDTO'>
export type CuerpoHorario = Esquema<'HorarioEspecialistaRequestDTO'>
export type CuerpoSillones = Esquema<'SillonesEspecialistaRequestDTO'>

/** Cargos que se pueden dar de alta desde Administración. */
export const CARGOS = [
  { rol: 'ODONTOLOGO', etiqueta: 'Doctor / Odontólogo' },
  { rol: 'RECEPCION', etiqueta: 'Recepcionista' },
  { rol: 'ADMIN_GENERAL', etiqueta: 'Administrador general' },
] as const

export type RolAlta = (typeof CARGOS)[number]['rol']

export function etiquetaCargo(rol: string | undefined): string {
  return CARGOS.find((c) => c.rol === rol)?.etiqueta ?? rol ?? '—'
}

export const claves = {
  usuarios: ['administracion', 'usuarios'] as const,
  doctores: ['administracion', 'doctores'] as const,
  horarios: (id: string) => ['administracion', 'horarios', id] as const,
}

export function useUsuarios() {
  return useQuery({
    queryKey: claves.usuarios,
    queryFn: () => apiTipada.get('/usuarios'),
  })
}

export function useDoctores() {
  return useQuery({
    queryKey: claves.doctores,
    queryFn: () => apiTipada.get('/especialistas/admin'),
  })
}

export function useHorariosDoctor(id: string | undefined) {
  return useQuery({
    queryKey: claves.horarios(id ?? ''),
    queryFn: () => apiTipada.get('/especialistas/{id}/horarios', { path: { id: id! } }),
    enabled: !!id,
  })
}

/** Tras tocar un usuario o doctor se refrescan administración, especialistas, disponibilidad, sedes y citas. */
function useInvalidar() {
  const qc = useQueryClient()
  return () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['administracion'] }),
      qc.invalidateQueries({ queryKey: ['especialistas'] }),
      qc.invalidateQueries({ queryKey: ['disponibilidad'] }),
      qc.invalidateQueries({ queryKey: ['sedes'] }),
      qc.invalidateQueries({ queryKey: ['citas'] }),
    ])
}

/**
 * Alta de un usuario con una sola petición. Con rol ODONTOLOGO el backend crea solo su perfil de especialista
 * (con el horario habitual) en la sede indicada, así que aparece en la agenda sin pasos extra.
 */
export function useCrearUsuario() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: (cuerpo: CuerpoUsuario) => apiTipada.post('/usuarios', { cuerpo }),
    onSuccess: invalidar,
  })
}

/** El propio doctor cambia su especialidad: se ve en la web pública y en el sistema. */
export function useActualizarMiEspecialidad() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: (especialidad: string) => apiTipada.patch('/especialistas/mi-especialidad', { cuerpo: { especialidad } }),
    onSuccess: invalidar,
  })
}

/**
 * Activar/desactivar. Con cuenta se usa PATCH /usuarios/{id}/estado (el backend sincroniza al especialista);
 * un doctor sin cuenta (dato antiguo) se cambia con PATCH /especialistas/{id}/estado.
 */
export function useCambiarEstado() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: ({ usuarioId, especialistaId, activo }: { usuarioId?: string; especialistaId?: string; activo: boolean }) =>
      usuarioId
        ? apiTipada.patch('/usuarios/{id}/estado', { path: { id: usuarioId }, query: { activo } })
        : apiTipada.patch('/especialistas/{id}/estado', { path: { id: especialistaId! }, query: { activo } }),
    onSuccess: invalidar,
  })
}

/** Se borró el perfil de doctor pero la cuenta de acceso no: el mensaje ya está listo para mostrar. */
export class EliminacionParcialError extends Error {}

export interface ResultadoEliminacion {
  /** true si la cuenta se borró de la base; false si se conservó desactivada por tener historial. */
  fisico: boolean
  mensaje: string
}

/**
 * Eliminar un doctor.
 * 1) DELETE /especialistas/{id}: intenta borrado físico de perfil y horarios. Si el doctor ya cuenta
 *    con citas o historial clínico, el backend responde 409 (Ley de Historias Clínicas).
 *    En ese caso, para no bloquear al Dueño/Director General, el sistema aplica automáticamente
 *    la baja administrativa inmediata (inactiva al especialista para retirarlo de la agenda).
 * 2) DELETE /usuarios/{id}: borra la cuenta de acceso o la da de baja (desactiva) si tiene registros históricos.
 */
export function useEliminarDoctor() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: async (doctor: Doctor): Promise<ResultadoEliminacion> => {
      let borradoFisicoEspecialista = true
      try {
        await apiTipada.delete('/especialistas/{id}', { path: { id: doctor.id! } })
      } catch (error) {
        if (error instanceof HttpError && (error.status === 409 || error.status === 400)) {
          borradoFisicoEspecialista = false
          await apiTipada.patch('/especialistas/{id}/estado', {
            path: { id: doctor.id! },
            query: { activo: false },
          })
        } else {
          throw error
        }
      }

      if (!doctor.usuarioId) {
        return {
          fisico: borradoFisicoEspecialista,
          mensaje: borradoFisicoEspecialista
            ? 'Doctor eliminado exitosamente.'
            : 'El doctor tiene historial clínico: fue dado de baja y retirado de la agenda activa.',
        }
      }

      try {
        const r = await apiTipada.delete('/usuarios/{id}', { path: { id: doctor.usuarioId } })
        const esFisico = borradoFisicoEspecialista && r?.tipo !== 'LOGICO'
        return {
          fisico: esFisico,
          mensaje: esFisico
            ? (r?.mensaje ?? 'Doctor y cuenta de acceso eliminados exitosamente.')
            : (r?.tipo === 'LOGICO' && r?.mensaje ? r.mensaje : 'El doctor cuenta con historial clínico: fue dado de baja y su cuenta y horarios fueron desactivados.'),
        }
      } catch (error) {
        if (!borradoFisicoEspecialista) {
          return {
            fisico: false,
            mensaje: 'El doctor fue dado de baja y retirado de la agenda activa.',
          }
        }
        const motivo = error instanceof Error ? error.message : 'Error desconocido.'
        throw new EliminacionParcialError(
          `Se eliminó el perfil de doctor, pero su cuenta de acceso no se pudo eliminar: ${motivo} Desactívala desde la lista.`,
        )
      }
    },
    onSettled: invalidar,
  })
}

/**
 * Eliminar un usuario (administrador, recepcionista o cuenta sin perfil de especialista).
 * DELETE /usuarios/{id}: borra la cuenta; si tiene registros históricos la da de baja (la desactiva).
 */
export function useEliminarUsuario() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: async (usuarioId: string): Promise<ResultadoEliminacion> => {
      const r = await apiTipada.delete('/usuarios/{id}', { path: { id: usuarioId } })
      return { fisico: r?.tipo !== 'LOGICO', mensaje: r?.mensaje ?? 'Usuario eliminado.' }
    },
    onSettled: invalidar,
  })
}

/**
 * El PUT del perfil reemplaza TODOS los campos (también usuarioId y correo): si se omitiera usuarioId el doctor
 * quedaría desvinculado de su cuenta. Por eso siempre se reenvía el perfil completo.
 */
export function cuerpoDesdeDoctor(doctor: Doctor, cambios: Partial<CuerpoDoctor> = {}): CuerpoDoctor {
  return {
    nombre: doctor.nombre ?? '',
    especialidad: doctor.especialidad ?? '',
    sedes: doctor.sedes ?? [],
    cop: doctor.cop,
    email: doctor.email,
    telefono: doctor.telefono,
    fotoUrl: doctor.fotoUrl,
    usuarioId: doctor.usuarioId,
    ...cambios,
  }
}

export function useEditarDoctor() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: CuerpoDoctor }) =>
      apiTipada.put('/especialistas/{id}', { path: { id }, cuerpo }),
    onSuccess: invalidar,
  })
}

export function useActualizarSillonesDoctor() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: CuerpoSillones }) =>
      apiTipada.patch('/especialistas/{id}/sillones', { path: { id }, cuerpo }),
    onSuccess: invalidar,
  })
}

/**
 * Reemplaza el horario semanal. El backend exige que cada sede del horario esté entre las sedes del doctor:
 * si el horario usa sedes nuevas, primero se agregan al perfil (reenviando el perfil completo).
 */
export function useGuardarHorarios() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: async ({ doctor, horarios }: { doctor: Doctor; horarios: CuerpoHorario[] }) => {
      const id = doctor.id!
      const actuales = doctor.sedes ?? []
      const sedes = [...new Set([...actuales, ...horarios.map((h) => h.sedeId)])]
      if (sedes.length !== actuales.length) {
        await apiTipada.put('/especialistas/{id}', { path: { id }, cuerpo: cuerpoDesdeDoctor(doctor, { sedes }) })
      }
      return apiTipada.put('/especialistas/{id}/horarios', { path: { id }, cuerpo: horarios })
    },
    onSuccess: invalidar,
  })
}

/* ───────── Cuenta y seguridad de un usuario ───────── */

export type CuerpoEdicionUsuario = Esquema<'UsuarioUpdateRequestDTO'>

/**
 * PUT /usuarios/{id}: nombre, cargo y sede. `activo` se omite a propósito (el backend conserva el estado actual).
 * Al pasar a ODONTOLOGO el backend crea su perfil de doctor; no se puede quitar el rol de administrador general a la
 * propia cuenta.
 */
export function useEditarUsuario() {
  const invalidar = useInvalidar()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: CuerpoEdicionUsuario }) =>
      apiTipada.put('/usuarios/{id}', { path: { id }, cuerpo }),
    onSuccess: invalidar,
  })
}

/** POST /usuarios/{id}/reset-password: el backend cierra las sesiones abiertas de esa persona. */
export function useRestablecerPassword() {
  return useMutation({
    mutationFn: ({ id, newPassword }: { id: string; newPassword: string }) =>
      apiTipada.post('/usuarios/{id}/reset-password', { path: { id }, cuerpo: { newPassword } }),
  })
}

/** POST /usuarios/{id}/2fa/restablecer: borra su secreto y códigos de respaldo y cierra sus sesiones. */
export function useRestablecer2fa() {
  return useMutation({
    mutationFn: (id: string) => apiTipada.post('/usuarios/{id}/2fa/restablecer', { path: { id } }),
  })
}

/** Meta mensual y comisión sobre el excedente de un doctor. Solo la consulta el administrador general. */
export function useMetaDoctor(id: string | undefined) {
  return useQuery({
    queryKey: ['administracion', 'meta', id ?? ''],
    queryFn: () => apiTipada.get('/especialistas/{id}/meta', { path: { id: id! } }),
    enabled: !!id,
  })
}

export function useGuardarMetaDoctor() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, cuerpo }: { id: string; cuerpo: { metaMensual?: number; comisionExcedentePorcentaje?: number } }) =>
      apiTipada.patch('/especialistas/{id}/meta', { path: { id }, cuerpo }),
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: ['administracion', 'meta'] }),
        qc.invalidateQueries({ queryKey: ['reportes', 'rendimiento-doctores'] }),
        qc.invalidateQueries({ queryKey: ['rendimiento'] }),
      ]),
  })
}
