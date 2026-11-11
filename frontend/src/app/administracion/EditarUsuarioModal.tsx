import { useState } from 'react'
import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Field,
  Input,
  toast,
} from '@/design-system'
import { inputClases } from '@/design-system/components/Input'
import { mensajeError } from '@/shared/errores'
import { FuncionesAdicionales, type FuncionAdicional } from './FuncionesAdicionales'
import { CARGOS, etiquetaCargo, useEditarUsuario } from './api-administracion'
import type { OpcionSede } from './EditorHorarios'

export interface UsuarioEditable {
  id: string
  nombre: string
  email?: string
  rol?: string
  rolesAdicionales?: string[]
  sedeId?: string
}

interface Props {
  usuario: UsuarioEditable | null
  alCerrar: () => void
  sedes: readonly OpcionSede[]
}

export function EditarUsuarioModal({ usuario, alCerrar, sedes }: Props) {
  return (
    <Dialog open={!!usuario} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-lg">{usuario && <Formulario usuario={usuario} alCerrar={alCerrar} sedes={sedes} />}</DialogContent>
    </Dialog>
  )
}

function Formulario({ usuario, alCerrar, sedes }: { usuario: UsuarioEditable; alCerrar: () => void; sedes: readonly OpcionSede[] }) {
  const editar = useEditarUsuario()
  const rolInicial = usuario.rol ?? 'RECEPCION'
  const [nombre, setNombre] = useState(usuario.nombre)
  const [rol, setRol] = useState(rolInicial)
  const [adicionales, setAdicionales] = useState<FuncionAdicional[]>(
    (usuario.rolesAdicionales ?? []).filter((r): r is FuncionAdicional => r === 'ODONTOLOGO' || r === 'RECEPCION'),
  )
  const [sedeId, setSedeId] = useState(usuario.sedeId ?? sedes[0]?.id ?? '')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  // Funciones que de verdad se suman (el administrador general solo puede sumar ser doctor).
  const funciones = adicionales.filter((a) => a !== rol && (rol !== 'ADMIN_GENERAL' || a === 'ODONTOLOGO'))
  const adminDoctor = rol === 'ADMIN_GENERAL' && funciones.includes('ODONTOLOGO')
  const requiereSede = rol !== 'ADMIN_GENERAL' || adminDoctor
  // Un cargo que ya no se ofrece (p. ej. ADMIN_SEDE heredado) se conserva como opción para no cambiarlo sin querer.
  const cargoHeredado = !CARGOS.some((c) => c.rol === rolInicial)
  // Una sede ya inactiva sigue siendo la sede actual de la cuenta: se muestra en vez de reemplazarla en silencio.
  const sedeInactiva = !!usuario.sedeId && !sedes.some((s) => s.id === usuario.sedeId)
  const seraDoctor = (rol === 'ODONTOLOGO' || adicionales.includes('ODONTOLOGO')) && rolInicial !== 'ODONTOLOGO' && !(usuario.rolesAdicionales ?? []).includes('ODONTOLOGO')

  async function guardar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const nuevos: Record<string, string> = {}
    if (!nombre.trim()) nuevos.nombre = 'Escribe el nombre completo.'
    if (requiereSede && !sedeId) nuevos.sede = 'Elige la sede.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return
    try {
      await editar.mutateAsync({
        id: usuario.id,
        cuerpo: { nombre: nombre.trim(), rol, rolesAdicionales: funciones.length || (usuario.rolesAdicionales ?? []).length ? funciones : undefined, sedeId: requiereSede ? sedeId : undefined },
      })
      toast(`Cuenta actualizada: ${nombre.trim()}`)
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Editar cuenta</DialogTitle>
        <DialogDescription>{usuario.email ?? 'Cuenta sin correo registrado'} · el correo no se puede cambiar.</DialogDescription>
      </DialogHeader>

      {errorEnvio && (
        <Alert tono="error" titulo="No se pudo guardar la cuenta">
          {errorEnvio}
        </Alert>
      )}

      <Field label="Nombre completo" error={errores.nombre}>
        <Input value={nombre} onChange={(e) => setNombre(e.target.value)} autoComplete="off" />
      </Field>
      <Field label="Cargo / Rol">
        <select className={inputClases} value={rol} onChange={(e) => setRol(e.target.value)}>
          {cargoHeredado && <option value={rolInicial}>{etiquetaCargo(rolInicial)}</option>}
          {CARGOS.map((c) => (
            <option key={c.rol} value={c.rol}>
              {c.etiqueta}
            </option>
          ))}
        </select>
      </Field>
      <FuncionesAdicionales rolPrincipal={rol} valor={adicionales} onChange={setAdicionales} />
      {requiereSede ? (
        <Field
          label={adminDoctor ? 'Sede donde atiende como doctor' : 'Sede'}
          error={errores.sede}
          ayuda={adminDoctor ? 'Su cuenta de administración sigue viendo todas las sedes; aquí se elige donde aparece en la agenda.' : undefined}
        >
          <select className={inputClases} value={sedeId} onChange={(e) => setSedeId(e.target.value)}>
            {sedeInactiva && <option value={usuario.sedeId}>{usuario.sedeId} (inactiva)</option>}
            {sedes.map((s) => (
              <option key={s.id} value={s.id}>
                {s.nombre}
              </option>
            ))}
          </select>
        </Field>
      ) : (
        <p className="rounded-panel bg-surface-container-low p-3 text-sm text-on-surface-variant">
          El administrador general tiene acceso a todas las sedes.
        </p>
      )}
      {seraDoctor && (
        <Alert tono="info" titulo="Tendrá perfil de doctor en la agenda">
          Si aún no tenía perfil, se crea al guardar con el horario habitual de la sede elegida; luego puedes ajustarlo con "Horarios".
        </Alert>
      )}

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={editar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={editar.isPending}>
          Guardar cambios
        </Button>
      </DialogFooter>
    </form>
  )
}
