import { useState } from 'react'
import {
  Alert,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  Field,
  PasswordInput,
  toast,
} from '@/design-system'
import { mensajeError } from '@/shared/errores'
import { useRestablecer2fa, useRestablecerPassword } from './api-administracion'
import type { UsuarioEditable } from './EditarUsuarioModal'

const MIN_PASSWORD = 4

interface Props {
  usuario: UsuarioEditable | null
  /** true si es la cuenta de quien administra: el backend no permite restablecerse el 2FA a uno mismo. */
  esMiCuenta: boolean
  alCerrar: () => void
}

export function SeguridadUsuarioModal({ usuario, esMiCuenta, alCerrar }: Props) {
  return (
    <Dialog open={!!usuario} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-lg">{usuario && <Contenido usuario={usuario} esMiCuenta={esMiCuenta} />}</DialogContent>
    </Dialog>
  )
}

function Contenido({ usuario, esMiCuenta }: { usuario: UsuarioEditable; esMiCuenta: boolean }) {
  return (
    <>
      <DialogHeader>
        <DialogTitle>Seguridad de {usuario.nombre}</DialogTitle>
        <DialogDescription>Restablece su contraseña o su verificación en dos pasos. Cada cambio queda en la auditoría.</DialogDescription>
      </DialogHeader>
      <SeccionPassword usuario={usuario} />
      <SeccionDosPasos usuario={usuario} esMiCuenta={esMiCuenta} />
    </>
  )
}

function SeccionPassword({ usuario }: { usuario: UsuarioEditable }) {
  const restablecer = useRestablecerPassword()
  const [password, setPassword] = useState('')
  const [repetida, setRepetida] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)
  const [hecho, setHecho] = useState(false)

  async function guardar(e: React.FormEvent) {
    e.preventDefault()
    setErrorEnvio(null)
    setHecho(false)
    const nuevos: Record<string, string> = {}
    if (password.length < MIN_PASSWORD) nuevos.password = `La contraseña debe tener al menos ${MIN_PASSWORD} caracteres.`
    else if (repetida !== password) nuevos.repetida = 'Las contraseñas no coinciden.'
    setErrores(nuevos)
    if (Object.keys(nuevos).length > 0) return
    try {
      await restablecer.mutateAsync({ id: usuario.id, newPassword: password })
      setPassword('')
      setRepetida('')
      setHecho(true)
      toast(`Contraseña restablecida: ${usuario.nombre}`)
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <form onSubmit={guardar} noValidate aria-label="Nueva contraseña" className="flex flex-col gap-3 rounded-panel border border-outline-variant/40 p-4">
      <h3 className="font-display text-base font-bold text-on-surface">Nueva contraseña</h3>
      {errorEnvio && <Alert tono="error" titulo="No se pudo cambiar la contraseña">{errorEnvio}</Alert>}
      {hecho && (
        <Alert tono="exito" titulo="Contraseña restablecida">
          Sus sesiones abiertas dejan de valer: la próxima vez deberá entrar con la contraseña nueva.
        </Alert>
      )}
      <Field label="Nueva contraseña" error={errores.password} ayuda={`Mínimo ${MIN_PASSWORD} caracteres. No puede repetir la actual ni las últimas 3.`}>
        <PasswordInput value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" />
      </Field>
      <Field label="Repetir la contraseña" error={errores.repetida}>
        <PasswordInput value={repetida} onChange={(e) => setRepetida(e.target.value)} autoComplete="new-password" />
      </Field>
      <div>
        <Button type="submit" cargando={restablecer.isPending}>
          Cambiar contraseña
        </Button>
      </div>
    </form>
  )
}

function SeccionDosPasos({ usuario, esMiCuenta }: { usuario: UsuarioEditable; esMiCuenta: boolean }) {
  const restablecer = useRestablecer2fa()
  const [confirmando, setConfirmando] = useState(false)
  const [errorEnvio, setErrorEnvio] = useState<string | null>(null)
  const [hecho, setHecho] = useState(false)

  async function confirmar() {
    setErrorEnvio(null)
    try {
      await restablecer.mutateAsync(usuario.id)
      setConfirmando(false)
      setHecho(true)
      toast(`Verificación en dos pasos restablecida: ${usuario.nombre}`)
    } catch (error) {
      setErrorEnvio(mensajeError(error))
    }
  }

  return (
    <section aria-label="Verificación en dos pasos" className="flex flex-col gap-3 rounded-panel border border-outline-variant/40 p-4">
      <h3 className="font-display text-base font-bold text-on-surface">Verificación en dos pasos (2FA)</h3>
      <p className="text-sm text-on-surface-variant">
        Útil si perdió su celular o su app autenticadora. Se borran su secreto y sus códigos de respaldo y se cierran sus sesiones; tendrá
        que configurarla de nuevo al entrar.
      </p>
      {errorEnvio && <Alert tono="error" titulo="No se pudo restablecer el 2FA">{errorEnvio}</Alert>}
      {hecho && <Alert tono="exito" titulo="Verificación en dos pasos restablecida">Deberá configurarla otra vez la próxima vez que entre.</Alert>}
      {esMiCuenta ? (
        <p className="text-sm font-semibold text-on-surface-variant">
          No puedes restablecer tu propio 2FA desde aquí: hazlo desde tus ajustes de seguridad con un código válido.
        </p>
      ) : confirmando ? (
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="peligro" onClick={() => void confirmar()} cargando={restablecer.isPending}>
            Sí, restablecer el 2FA
          </Button>
          <Button variant="secundario" onClick={() => setConfirmando(false)} disabled={restablecer.isPending}>
            Cancelar
          </Button>
        </div>
      ) : (
        <div>
          <Button variant="secundario" onClick={() => setConfirmando(true)}>
            Restablecer 2FA
          </Button>
        </div>
      )}
    </section>
  )
}
