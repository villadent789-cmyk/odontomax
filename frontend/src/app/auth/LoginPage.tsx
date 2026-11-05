import { zodResolver } from '@hookform/resolvers/zod'
import { useNavigate } from '@tanstack/react-router'
import { ArrowRight, Contact } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Button, Field, Input, PasswordInput } from '@/design-system'
import { TIPO_TOKEN_2FA_PENDIENTE, interpretarError, login } from './api-auth'
import { AvisoAccesoAlert } from './AvisoAcceso'
import { useFlujoAcceso, type AvisoAcceso } from './flujo'
import { TituloAcceso } from './PantallaAcceso'
import { AVISOS_LOGIN, RUTAS_ACCESO, type ClaveAvisoLogin } from './rutas'
import { destinoSeguro, establecerSesion } from './sesion-real'

const esquema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Ingresa tu correo corporativo.')
    .pipe(z.email('Ingresa un correo válido, por ejemplo recepcion@villadent.pe.')),
  password: z.string().min(1, 'Ingresa tu contraseña.'),
})

type Datos = z.infer<typeof esquema>

const ERROR_CREDENCIALES = 'Correo corporativo o contraseña incorrectos.'

export function LoginPage({ redirect, avisoInicial }: { redirect?: string; avisoInicial?: ClaveAvisoLogin }) {
  const navigate = useNavigate()
  const flujo = useFlujoAcceso()
  const [aviso, setAviso] = useState<AvisoAcceso | null>(() => {
    if (flujo.aviso) return flujo.aviso
    if (avisoInicial) return { tipo: 'exito', titulo: 'Sesión cerrada', mensaje: AVISOS_LOGIN[avisoInicial] }
    return null
  })

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Datos>({ resolver: zodResolver(esquema) })

  const enviar = async ({ email, password }: Datos) => {
    setAviso(null)
    flujo.setAviso(null)
    try {
      const r = await login(email.trim(), password.trim())
      if (!r.token) throw new Error('La respuesta del servidor no trae un token de sesión.')
      if (r.tokenTipo === TIPO_TOKEN_2FA_PENDIENTE) {
        flujo.setPendiente2fa({ tempToken: r.token, email: r.usuario?.email ?? email.trim() })
        await navigate({ to: RUTAS_ACCESO.segundoFactor, search: (s) => s })
        return
      }
      const requiereConfigurar = r.requiereConfigurar2fa === true
      await establecerSesion(r.token, requiereConfigurar ? 'pendiente' : 'inactivo')
      // Sin 2FA configurado entra igual (no se lleva a la fuerza a Ajustes 2FA); puede configurarlo desde el menú de su cuenta.
      await navigate({ href: destinoSeguro(redirect) })
    } catch (e) {
      setAviso(interpretarError(e, ERROR_CREDENCIALES))
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <TituloAcceso eyebrow="Control de acceso" titulo="Iniciar sesión">
        Ingresa tus credenciales de la red odontológica e imagenológica.
      </TituloAcceso>

      {/* El aviso usa role="alert"/"status" (región viva): se anuncia al aparecer. */}
      <div className="empty:hidden">
        {aviso && (
          <AvisoAccesoAlert
            aviso={aviso}
            onCerrar={() => {
              setAviso(null)
              flujo.setAviso(null)
            }}
          />
        )}
      </div>

      <form noValidate onSubmit={handleSubmit(enviar)} className="flex flex-col gap-5" aria-label="Iniciar sesión">
        <Field label="Correo corporativo" error={errors.email?.message}>
          <Input
            type="email"
            icono={<Contact />}
            placeholder="recepcion.chilca@villadent.pe"
            autoComplete="username"
            {...register('email')}
          />
        </Field>
        <Field
          label="Contraseña"
          error={errors.password?.message}
          extra={<span className="text-xs text-on-surface-variant">¿La olvidaste? Pide a un administrador que la restablezca.</span>}
        >
          <PasswordInput autoComplete="current-password" {...register('password')} />
        </Field>
        <Button type="submit" variant="accion" size="lg" cargando={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? 'Verificando…' : 'Iniciar sesión'}
          {!isSubmitting && <ArrowRight aria-hidden />}
        </Button>
      </form>

      <div className="pt-2 border-t border-outline-variant/30 flex items-center justify-between text-xs text-on-surface-variant/80">
        <span className="flex items-center gap-1.5 font-medium">
          <span className="size-2 rounded-full bg-emerald-500 inline-block animate-pulse" />
          Conexión Cifrada SSL/TLS
        </span>
        <span className="text-xs">Ley N.° 29733 • MINSA</span>
      </div>
    </div>
  )
}
