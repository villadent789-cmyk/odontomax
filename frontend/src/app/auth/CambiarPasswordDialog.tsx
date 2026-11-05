import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Field,
  PasswordInput,
} from '@/design-system'
import { limpiarSesion } from '@/shared/auth'
import { cambiarPassword, interpretarError } from './api-auth'
import { AvisoAccesoAlert } from './AvisoAcceso'
import type { AvisoAcceso } from './flujo'
import { esquemaPasswordNuevo, tituloErrorOperacion } from './formularios'
import { RUTAS_ACCESO } from './rutas'

const esquema = esquemaPasswordNuevo.and(z.object({ actual: z.string().min(1, 'Ingresa tu contraseña actual.') }))
type Datos = z.infer<typeof esquema>

/**
 * Cambiar la contraseña propia (menú de usuario de B0). Tras el éxito el backend invalida todos los JWT (B-008):
 * se cierra la sesión y se vuelve al login con el aviso "Contraseña cambiada".
 */
export function CambiarPasswordDialog({ abierto, onCambio }: { abierto: boolean; onCambio: (v: boolean) => void }) {
  const navigate = useNavigate()
  const [aviso, setAviso] = useState<AvisoAcceso | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({
    resolver: zodResolver(esquema),
    defaultValues: { actual: '', password: '', confirmacion: '' },
  })

  const cambiarVisibilidad = (v: boolean) => {
    if (!v) {
      reset()
      setAviso(null)
    }
    onCambio(v)
  }

  const enviar = async ({ actual, password }: Datos) => {
    setAviso(null)
    try {
      await cambiarPassword(actual, password)
      limpiarSesion()
      onCambio(false)
      await navigate({ to: RUTAS_ACCESO.login, search: { aviso: 'password-cambiado' } })
    } catch (e) {
      // 401 "La contraseña actual es incorrecta." o 400 (coincide con la actual o con las 3 últimas).
      setAviso(interpretarError(e, 'No se pudo cambiar la contraseña.'))
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={cambiarVisibilidad}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Cambiar contraseña</DialogTitle>
          <DialogDescription>
            Al guardarla se cerrarán todas tus sesiones y tendrás que iniciar sesión de nuevo.
          </DialogDescription>
        </DialogHeader>
        <div className="empty:hidden">
          {aviso && <AvisoAccesoAlert aviso={aviso} titulo={tituloErrorOperacion(aviso)} onCerrar={() => setAviso(null)} />}
        </div>
        <form noValidate onSubmit={handleSubmit(enviar)} className="flex flex-col gap-4" aria-label="Cambiar contraseña">
          <Field label="Contraseña actual" error={errors.actual?.message}>
            <PasswordInput autoComplete="current-password" {...register('actual')} />
          </Field>
          <Field label="Nueva contraseña" error={errors.password?.message}>
            <PasswordInput autoComplete="new-password" {...register('password')} />
          </Field>
          <Field label="Repite la nueva contraseña" error={errors.confirmacion?.message}>
            <PasswordInput autoComplete="new-password" {...register('confirmacion')} />
          </Field>
          <DialogFooter>
            <Button variant="secundario" onClick={() => cambiarVisibilidad(false)}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" cargando={isSubmitting}>
              Guardar contraseña
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
