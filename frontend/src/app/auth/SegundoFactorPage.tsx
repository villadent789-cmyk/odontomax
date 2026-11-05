import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useNavigate } from '@tanstack/react-router'
import { ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Alert, Button, Field, Input, buttonVariants } from '@/design-system'
import { completarLogin2fa, esTokenTemporalInvalido, interpretarError } from './api-auth'
import { AvisoAccesoAlert } from './AvisoAcceso'
import { useFlujoAcceso, type AvisoAcceso } from './flujo'
import { TituloAcceso } from './PantallaAcceso'
import { RUTAS_ACCESO } from './rutas'
import { destinoSeguro, establecerSesion } from './sesion-real'

/** TOTP de 6 dígitos o código de respaldo de 8 caracteres base32 (TotpService.generateBackupCodes). */
const esquema = z.object({
  codigo: z
    .string()
    .trim()
    .min(1, 'Ingresa el código de verificación.')
    .regex(/^(\d{6}|[A-Za-z2-7]{8})$/, 'El código tiene 6 dígitos (aplicación) u 8 caracteres (código de respaldo).'),
})

type Datos = z.infer<typeof esquema>

const ERROR_CODIGO = 'Código de autenticación 2FA o código de respaldo inválido.'

export function SegundoFactorPage({ redirect }: { redirect?: string }) {
  const navigate = useNavigate()
  const flujo = useFlujoAcceso()
  const [aviso, setAviso] = useState<AvisoAcceso | null>(null)
  const pendiente = flujo.pendiente2fa

  const {
    register,
    handleSubmit,
    resetField,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema), defaultValues: { codigo: '' } })

  const volverAlLogin = async (avisoLogin: AvisoAcceso | null) => {
    flujo.setPendiente2fa(null)
    flujo.setAviso(avisoLogin)
    await navigate({ to: RUTAS_ACCESO.login, search: (s) => s })
  }

  if (!pendiente) {
    return (
      <div className="flex flex-col gap-6">
        <TituloAcceso eyebrow="Verificación en dos pasos" titulo="Código 2FA" />
        <Alert tono="info" titulo="Primero inicia sesión">
          Ingresa tu correo y contraseña. Si tu cuenta tiene la verificación en dos pasos activa, aquí te pediremos el
          código.
        </Alert>
        <Link to={RUTAS_ACCESO.login} className={buttonVariants({ variant: 'primario' })}>
          Ir a iniciar sesión
        </Link>
      </div>
    )
  }

  const enviar = async ({ codigo }: Datos) => {
    setAviso(null)
    try {
      const r = await completarLogin2fa(pendiente.tempToken, codigo)
      if (!r.token) throw new Error('La respuesta del servidor no trae un token de sesión.')
      await establecerSesion(r.token, 'activo')
      flujo.setPendiente2fa(null)
      await navigate({ href: destinoSeguro(redirect) })
    } catch (e) {
      const error = interpretarError(e, ERROR_CODIGO)
      // Token temporal de un solo uso, caducado (5 min) o agotado por intentos; o cuenta bloqueada: volver al login.
      if (esTokenTemporalInvalido(error.mensaje) || error.tipo === 'bloqueo') {
        await volverAlLogin(error)
        return
      }
      setAviso(error)
      resetField('codigo')
      setFocus('codigo')
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <TituloAcceso eyebrow="Verificación en dos pasos" titulo="Código 2FA">
        Ingresa el código de 6 dígitos de tu aplicación autenticadora
        {pendiente.email ? (
          <>
            {' '}
            para <strong className="text-on-surface">{pendiente.email}</strong>
          </>
        ) : null}
        , o uno de tus códigos de respaldo.
      </TituloAcceso>

      <div className="empty:hidden">{aviso && <AvisoAccesoAlert aviso={aviso} onCerrar={() => setAviso(null)} />}</div>

      <form noValidate onSubmit={handleSubmit(enviar)} className="flex flex-col gap-5" aria-label="Verificar código 2FA">
        <Field
          label="Código de verificación"
          error={errors.codigo?.message}
          ayuda="El código temporal de acceso vence a los 5 minutos y admite pocos intentos."
        >
          <Input
            icono={<ShieldCheck />}
            autoComplete="one-time-code"
            inputMode="text"
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={8}
            placeholder="123456"
            className="font-mono tracking-[0.3em]"
            {...register('codigo')}
          />
        </Field>
        <Button type="submit" variant="accion" size="lg" cargando={isSubmitting} className="w-full">
          {isSubmitting ? 'Verificando…' : 'Verificar y entrar'}
        </Button>
        <Button type="button" variant="enlace" onClick={() => void volverAlLogin(null)}>
          Volver al inicio de sesión
        </Button>
      </form>
    </div>
  )
}
