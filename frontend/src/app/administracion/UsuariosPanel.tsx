import { CalendarClock, KeyRound, Pencil, Plus, Power, Trash2, UserCog, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import {
  Alert,
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  ErrorState,
  Skeleton,
  toast,
} from '@/design-system'
import { useSesion } from '@/shared/auth'
import { mensajeError } from '@/shared/errores'
import { useSedesActivas } from '../agenda/api-agenda'
import {
  EliminacionParcialError,
  etiquetaCargo,
  useCambiarEstado,
  useDoctores,
  useEliminarDoctor,
  useEliminarUsuario,
  useUsuarios,
  type Doctor,
} from './api-administracion'
import { EditarDoctorModal } from './EditarDoctorModal'
import { EditarUsuarioModal } from './EditarUsuarioModal'
import { HorariosDoctorModal } from './HorariosDoctorModal'
import { NuevoUsuarioModal } from './NuevoUsuarioModal'
import { SeguridadUsuarioModal } from './SeguridadUsuarioModal'

interface FilaPersonal {
  clave: string
  nombre: string
  email?: string
  rol?: string
  sedeIds: string[]
  activo: boolean
  usuarioId?: string
  /** Sede de la cuenta de acceso (un doctor puede atender en varias, pero su cuenta tiene una). */
  sedeCuenta?: string
  /** Perfil de especialista: solo los doctores lo tienen y habilita "Editar perfil" y "Horarios". */
  doctor?: Doctor
}

/** Usuarios del sistema (doctores, recepción, administradores): altas, cuentas, seguridad y estado. Solo ADMIN_GENERAL. */
export function UsuariosPanel() {
  const usuarios = useUsuarios()
  const doctores = useDoctores()
  const sedesActivas = useSedesActivas()
  const cambiarEstado = useCambiarEstado()
  const eliminarDoctor = useEliminarDoctor()
  const eliminarUsuario = useEliminarUsuario()
  const miId = useSesion()?.usuario.id

  const sedes = useMemo(
    () => (sedesActivas.data ?? []).map((s) => ({
      id: s.id ?? '',
      nombre: s.nombre ?? s.id ?? '',
      sillones: s.sillones,
    })),
    [sedesActivas.data],
  )
  const sedesIdsActivas = useMemo(() => new Set(sedes.map((s) => s.id)), [sedes])
  const nombreSede = (id: string) => sedes.find((s) => s.id === id)?.nombre ?? id

  const filas = useMemo<FilaPersonal[]>(() => {
    const perfiles = doctores.data ?? []
    const deUsuarios = (usuarios.data ?? []).map((u) => {
      const doctor = perfiles.find((d) => d.usuarioId === u.id)
      const sedesCandidatas = doctor?.sedes?.length ? doctor.sedes : u.sedeId ? [u.sedeId] : []
      return {
        clave: u.id ?? u.email ?? '',
        nombre: u.nombre ?? '',
        email: u.email,
        rol: u.rol,
        sedeIds: sedesCandidatas.filter((s) => s !== 'sede-chilca-2' && (!sedesIdsActivas.size || sedesIdsActivas.has(s))),
        activo: !!u.activo,
        usuarioId: u.id,
        sedeCuenta: u.sedeId,
        doctor,
      }
    })
    // Doctores de datos antiguos sin cuenta: se siguen pudiendo editar y activar.
    const sinCuenta = perfiles
      .filter((d) => !d.usuarioId)
      .map((d) => ({
        clave: d.id ?? d.nombre ?? '',
        nombre: d.nombre ?? '',
        email: d.email,
        rol: 'ODONTOLOGO',
        sedeIds: (d.sedes ?? []).filter((s) => s !== 'sede-chilca-2' && (!sedesIdsActivas.size || sedesIdsActivas.has(s))),
        activo: !!d.activo,
        doctor: d,
      }))
    return [...deUsuarios, ...sinCuenta]
  }, [usuarios.data, doctores.data, sedesIdsActivas])

  const [nuevoAbierto, setNuevoAbierto] = useState(false)
  const [editandoCuenta, setEditandoCuenta] = useState<FilaPersonal | null>(null)
  const [seguridad, setSeguridad] = useState<FilaPersonal | null>(null)
  const [editando, setEditando] = useState<Doctor | null>(null)
  const [conHorarios, setConHorarios] = useState<Doctor | null>(null)
  const [aDesactivar, setADesactivar] = useState<FilaPersonal | null>(null)
  const [errorEstado, setErrorEstado] = useState<string | null>(null)
  const [aEliminar, setAEliminar] = useState<FilaPersonal | null>(null)
  const [avisoParcial, setAvisoParcial] = useState<string | null>(null)
  const [errorEliminar, setErrorEliminar] = useState<{ mensaje: string; tieneHistorial: boolean } | null>(null)

  const cargando = usuarios.isPending || doctores.isPending
  const error = usuarios.error ?? doctores.error

  async function aplicarEstado(fila: FilaPersonal, activo: boolean) {
    setErrorEstado(null)
    try {
      await cambiarEstado.mutateAsync({ usuarioId: fila.usuarioId, especialistaId: fila.doctor?.id, activo })
      toast(`${fila.nombre}: ${activo ? 'activado' : 'desactivado'}`)
    } catch (e) {
      setErrorEstado(mensajeError(e))
    } finally {
      setADesactivar(null)
    }
  }

  function pedirEliminar(fila: FilaPersonal) {
    setErrorEliminar(null)
    setAEliminar(fila)
  }

  function cerrarEliminar() {
    if (eliminarDoctor.isPending || eliminarUsuario.isPending) return
    setAEliminar(null)
    setErrorEliminar(null)
  }

  async function confirmarEliminar() {
    const fila = aEliminar
    if (!fila) return
    setErrorEliminar(null)
    try {
      if (fila.doctor) {
        const resultado = await eliminarDoctor.mutateAsync(fila.doctor)
        toast(resultado.fisico ? `Doctor eliminado: ${fila.nombre}` : `${fila.nombre}: ${resultado.mensaje}`)
      } else if (fila.usuarioId) {
        const resultado = await eliminarUsuario.mutateAsync(fila.usuarioId)
        toast(resultado.fisico ? `Usuario eliminado: ${fila.nombre}` : `${fila.nombre}: ${resultado.mensaje}`)
      }
      setAEliminar(null)
    } catch (e) {
      if (e instanceof EliminacionParcialError) {
        // El perfil ya no existe: se cierra el diálogo y se avisa en la pantalla.
        setAEliminar(null)
        setAvisoParcial(e.message)
        return
      }
      setErrorEliminar({ mensaje: mensajeError(e), tieneHistorial: false })
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="text-on-surface-variant">Usuarios del sistema: doctores, recepción y administradores.</p>
        <Button onClick={() => setNuevoAbierto(true)} disabled={sedes.length === 0}>
          <Plus aria-hidden />
          Nuevo usuario
        </Button>
      </div>

      {errorEstado && (
        <Alert tono="error" titulo="No se pudo cambiar el estado" onCerrar={() => setErrorEstado(null)}>
          {errorEstado}
        </Alert>
      )}
      {avisoParcial && (
        <Alert tono="aviso" titulo="La eliminación quedó incompleta" onCerrar={() => setAvisoParcial(null)}>
          {avisoParcial}
        </Alert>
      )}

      <section aria-label="Directorio de usuarios" className="rounded-card bg-surface-container-lowest shadow-card">
        {cargando && !error ? (
          <div role="status" aria-label="Cargando usuarios" className="flex flex-col gap-3 p-6">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : error ? (
          <ErrorState
            titulo="No pudimos cargar los usuarios"
            descripcion={mensajeError(error)}
            onReintentar={() => {
              void usuarios.refetch()
              void doctores.refetch()
            }}
          />
        ) : filas.length === 0 ? (
          <EmptyState
            icono={<Users />}
            titulo="Aún no hay usuarios"
            descripcion="Crea el primero: un doctor aparecerá en la agenda automáticamente."
            accion={
              <Button onClick={() => setNuevoAbierto(true)} disabled={sedes.length === 0}>
                Nuevo usuario
              </Button>
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[56rem] text-left text-sm">
              <caption className="sr-only">Usuarios registrados</caption>
              <thead className="text-on-surface-variant">
                <tr className="border-b border-outline-variant/40">
                  <th scope="col" className="px-4 py-3 font-bold">Nombre</th>
                  <th scope="col" className="px-4 py-3 font-bold">Cargo</th>
                  <th scope="col" className="px-4 py-3 font-bold">Sedes</th>
                  <th scope="col" className="px-4 py-3 font-bold">Perfil profesional</th>
                  <th scope="col" className="px-4 py-3 font-bold">Estado</th>
                  <th scope="col" className="px-4 py-3 font-bold"><span className="sr-only">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {filas.map((f) => (
                  <tr key={f.clave} className="border-b border-outline-variant/20 last:border-0">
                    <th scope="row" className="px-4 py-3 font-bold text-on-surface">
                      {f.nombre}
                      {f.email && <span className="block text-xs font-normal text-on-surface-variant">{f.email}</span>}
                    </th>
                    <td className="px-4 py-3">{etiquetaCargo(f.rol)}</td>
                    <td className="px-4 py-3">
                      {f.sedeIds.length === 0 ? (
                        <span>{f.rol === 'ADMIN_GENERAL' ? 'Todas las sedes' : '—'}</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {f.sedeIds.map((s) => <Badge key={s}>{nombreSede(s)}</Badge>)}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {f.doctor ? (
                        <div className="flex flex-col">
                          <span>{f.doctor.especialidad || '—'}</span>
                          {f.doctor.cop && <span className="text-xs text-on-surface-variant">COP {f.doctor.cop}</span>}
                          {f.doctor.telefono && <span className="text-xs text-on-surface-variant">Tel. {f.doctor.telefono}</span>}
                        </div>
                      ) : (
                        '—'
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <Badge punto tono={f.activo ? 'primario' : 'neutro'}>{f.activo ? 'Activo' : 'Inactivo'}</Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        {f.usuarioId && (
                          <>
                            <Button size="sm" variant="secundario" onClick={() => setEditandoCuenta(f)} aria-label={`Editar cuenta de ${f.nombre}`}>
                              <UserCog aria-hidden />
                              Cuenta
                            </Button>
                            <Button size="sm" variant="secundario" onClick={() => setSeguridad(f)} aria-label={`Seguridad de ${f.nombre}`}>
                              <KeyRound aria-hidden />
                              Seguridad
                            </Button>
                          </>
                        )}
                        {f.doctor && (
                          <>
                            <Button size="sm" variant="secundario" onClick={() => setEditando(f.doctor!)} aria-label={`Editar perfil de ${f.nombre}`}>
                              <Pencil aria-hidden />
                              Editar perfil
                            </Button>
                            <Button size="sm" variant="secundario" onClick={() => setConHorarios(f.doctor!)} aria-label={`Configurar horarios de ${f.nombre}`}>
                              <CalendarClock aria-hidden />
                              Horarios
                            </Button>
                          </>
                        )}
                        <Button
                          size="sm"
                          variant={f.activo ? 'peligro' : 'primario'}
                          disabled={cambiarEstado.isPending}
                          onClick={() => (f.activo ? setADesactivar(f) : aplicarEstado(f, true))}
                          aria-label={`${f.activo ? 'Desactivar' : 'Activar'} a ${f.nombre}`}
                        >
                          <Power aria-hidden />
                          {f.activo ? 'Desactivar' : 'Activar'}
                        </Button>
                        <Button
                          size="sm"
                          variant="peligro"
                          disabled={!!f.usuarioId && f.usuarioId === miId}
                          title={f.usuarioId === miId ? 'No puedes eliminar tu propia cuenta' : undefined}
                          onClick={() => pedirEliminar(f)}
                          aria-label={`Eliminar a ${f.nombre}`}
                        >
                          <Trash2 aria-hidden />
                          Eliminar
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <NuevoUsuarioModal abierto={nuevoAbierto} alCerrar={() => setNuevoAbierto(false)} sedes={sedes} />
      <EditarUsuarioModal
        usuario={editandoCuenta?.usuarioId ? { id: editandoCuenta.usuarioId, nombre: editandoCuenta.nombre, email: editandoCuenta.email, rol: editandoCuenta.rol, sedeId: editandoCuenta.sedeCuenta } : null}
        alCerrar={() => setEditandoCuenta(null)}
        sedes={sedes}
      />
      <SeguridadUsuarioModal
        usuario={seguridad?.usuarioId ? { id: seguridad.usuarioId, nombre: seguridad.nombre, email: seguridad.email } : null}
        esMiCuenta={!!seguridad?.usuarioId && seguridad.usuarioId === miId}
        alCerrar={() => setSeguridad(null)}
      />
      <EditarDoctorModal doctor={editando} alCerrar={() => setEditando(null)} sedes={sedes} doctores={doctores.data ?? []} />
      <HorariosDoctorModal doctor={conHorarios} alCerrar={() => setConHorarios(null)} sedes={sedes} />

      <Dialog open={!!aDesactivar} onOpenChange={(v) => !v && setADesactivar(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>¿Desactivar a {aDesactivar?.nombre}?</DialogTitle>
            <DialogDescription>
              {aDesactivar?.doctor ? 'Dejará de recibir citas nuevas y no podrá iniciar sesión.' : 'No podrá iniciar sesión.'} Sus
              datos y registros anteriores se conservan; puedes volver a activarlo cuando quieras.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secundario" onClick={() => setADesactivar(null)}>
              Cancelar
            </Button>
            <Button variant="peligro" onClick={() => aDesactivar && aplicarEstado(aDesactivar, false)} cargando={cambiarEstado.isPending}>
              Desactivar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!aEliminar} onOpenChange={(v) => !v && cerrarEliminar()}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>¿Eliminar a {aEliminar?.nombre}?</DialogTitle>
            <DialogDescription>
              {aEliminar?.doctor
                ? 'Se borrará su perfil de doctor, su horario y su cuenta de acceso. Esta acción no se puede deshacer. Si el doctor ya cuenta con citas o historial clínico registrado, el sistema aplicará automáticamente la baja administrativa inmediata (inactivación y retiro de la agenda activa) para proteger la historia médica legal de los pacientes.'
                : 'Se eliminará su cuenta de acceso. Esta acción no se puede deshacer. Si el usuario ya tiene registros históricos vinculados, el sistema aplicará automáticamente una baja administrativa desactivando la cuenta por seguridad.'}
            </DialogDescription>
          </DialogHeader>
          {errorEliminar && (
            <Alert tono="error" titulo="No se pudo procesar la solicitud">
              {errorEliminar.mensaje}
            </Alert>
          )}
          <DialogFooter>
            <Button variant="secundario" onClick={cerrarEliminar} disabled={eliminarDoctor.isPending || eliminarUsuario.isPending}>
              Cancelar
            </Button>
            <Button variant="peligro" onClick={() => void confirmarEliminar()} cargando={eliminarDoctor.isPending || eliminarUsuario.isPending}>
              Eliminar definitivamente
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
