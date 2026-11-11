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
  PasswordInput,
  toast,
} from '@/design-system'
import { enfocarAlMontar } from '@/design-system/enfocarAlMontar'
import { inputClases } from '@/design-system/components/Input'
import { mensajeError } from '@/shared/errores'
import { CARGOS, useCrearUsuario, type RolAlta } from './api-administracion'
import type { OpcionSede } from './EditorHorarios'
import { FuncionesAdicionales, type FuncionAdicional } from './FuncionesAdicionales'

interface Props {
  abierto: boolean
  alCerrar: () => void
  sedes: readonly OpcionSede[]
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const MIN_PASSWORD = 4

export function NuevoUsuarioModal({ abierto, alCerrar, sedes }: Props) {
  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-lg">
        <Formulario alCerrar={alCerrar} sedes={sedes} />
      </DialogContent>
    </Dialog>
  )
}

/** El estado vive dentro del contenido del diálogo: se reinicia cada vez que se abre. */
function Formulario({ alCerrar, sedes }: Omit<Props, 'abierto'>) {
  const crear = useCrearUsuario()
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rol, setRol] = useState<RolAlta>('ODONTOLOGO')
  const [adicionales, setAdicionales] = useState<FuncionAdicional[]>([])
  const [sedeId, setSedeId] = useState(sedes[0]?.id ?? '')
  const [especialidad, setEspecialidad] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)

  // Funciones que de verdad se suman (el administrador general solo puede sumar ser doctor).
  const funciones = adicionales.filter((a) => a !== rol && (rol !== 'ADMIN_GENERAL' || a === 'ODONTOLOGO'))
  const adminDoctor = rol === 'ADMIN_GENERAL' && funciones.includes('ODONTOLOGO')
  const requiereSede = rol !== 'ADMIN_GENERAL' || adminDoctor

  async function guardar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    const nuevos: Record<string, string> = {}
    if (!nombre.trim()) nuevos.nombre = 'Escribe el nombre completo.'
    if (!EMAIL.test(email.trim())) nuevos.email = 'Escribe un correo válido.'
    if (password.length < MIN_PASSWORD) nuevos.password = `La contraseña debe tener al menos ${MIN_PASSWORD} caracteres.`
    if (requiereSede && !sedeId) nuevos.sede = 'Elige la sede.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return

    try {
      await crear.mutateAsync({
        nombre: nombre.trim(),
        email: email.trim().toLowerCase(),
        password,
        rol,
        rolesAdicionales: funciones.length ? funciones : undefined,
        sedeId: requiereSede ? sedeId : undefined,
        especialidad: (rol === 'ODONTOLOGO' || adicionales.includes('ODONTOLOGO')) && especialidad.trim() ? especialidad.trim() : undefined,
      })
      toast(`Usuario creado: ${nombre.trim()}`)
      alCerrar()
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={guardar} noValidate className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>Nuevo usuario</DialogTitle>
        <DialogDescription>
          Crea la cuenta de acceso. Un doctor aparece solo en la agenda y en la web, con su sede, un sillón libre y el horario
          habitual; el horario se ajusta después con "Horarios".
        </DialogDescription>
      </DialogHeader>

      {errorEnvio && (
        <Alert tono="error" titulo="No se pudo crear el usuario">
          {errorEnvio}
        </Alert>
      )}

      <Field label="Nombre completo" error={errores.nombre}>
        <Input value={nombre} onChange={(e) => setNombre(e.target.value)} autoComplete="off" ref={enfocarAlMontar} />
      </Field>
      <Field label="Correo electrónico" error={errores.email} ayuda="Con este correo inicia sesión.">
        <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" />
      </Field>
      <Field label="Contraseña inicial" error={errores.password} ayuda={`Mínimo ${MIN_PASSWORD} caracteres.`}>
        <PasswordInput value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
      </Field>
      <Field label="Cargo / Rol">
        <select className={inputClases} value={rol} onChange={(e) => setRol(e.target.value as RolAlta)}>
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

      {(rol === 'ODONTOLOGO' || adicionales.includes('ODONTOLOGO')) && (
        <Field
          label="Especialidad"
          ayuda="Se muestra en la web. Si no la pones, será Odontología General; el propio doctor puede cambiarla desde su cuenta."
        >
          <Input value={especialidad} onChange={(e) => setEspecialidad(e.target.value)} maxLength={80} autoComplete="off" placeholder="Ej. Ortodoncia" />
        </Field>
      )}

      <DialogFooter>
        <Button type="button" variant="secundario" onClick={alCerrar} disabled={crear.isPending}>
          Cancelar
        </Button>
        <Button type="submit" cargando={crear.isPending}>
          Guardar usuario
        </Button>
      </DialogFooter>
    </form>
  )
}
