import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from '@tanstack/react-router'
import { Copy, Download, KeyRound, QrCode, ShieldCheck, ShieldOff } from 'lucide-react'
import QRCode from 'qrcode'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { Alert, Button, Field, Input, PasswordInput, buttonVariants } from '@/design-system'
import { guardarSesion, useSesion, type Sesion } from '@/shared/auth'
import {
  configurar2fa,
  desactivar2fa,
  interpretarError,
  verificarActivar2fa,
  type ConfiguracionTotp,
  type ErrorAcceso,
} from './api-auth'
import { AvisoAccesoAlert } from './AvisoAcceso'
import { useFlujoAcceso, type AvisoAcceso } from './flujo'
import { tituloErrorOperacion } from './formularios'
import { TituloAcceso } from './PantallaAcceso'
import { RUTAS_ACCESO } from './rutas'

const codigoActualOpcional = z.object({ codigo: z.string().trim() })
const codigoActualObligatorio = z.object({
  codigo: z
    .string()
    .trim()
    .min(1, 'Ingresa tu código actual o uno de respaldo.')
    .regex(/^(\d{6}|[A-Za-z2-7]{8})$/, 'El código tiene 6 dígitos (aplicación) u 8 caracteres (código de respaldo).'),
})
const esquemaVerificar = z.object({
  codigo: z.string().trim().regex(/^\d{6}$/, 'Ingresa los 6 dígitos que muestra tu aplicación.'),
})
const esquemaDesactivar = z.object({
  password: z.string().min(1, 'Ingresa tu contraseña actual.'),
  codigo: z
    .string()
    .trim()
    .min(1, 'Ingresa tu código 2FA o uno de respaldo.')
    .regex(/^(\d{6}|[A-Za-z2-7]{8})$/, 'El código tiene 6 dígitos (aplicación) u 8 caracteres (código de respaldo).'),
})

function actualizarSegundoFactor(sesion: Sesion, segundoFactor: NonNullable<Sesion['segundoFactor']>) {
  guardarSesion({ ...sesion, segundoFactor })
}

/** La respuesta trae cada dato en snake_case y en camelCase (TotpConfigurarResponseDTO). */
function leerConfiguracion(r: ConfiguracionTotp) {
  return {
    otpauthUrl: r.otpauth_url ?? r.otpauthUrl,
    qrBackend: r.qr_code ?? r.qrCode,
    secreto: r.secret,
    codigosRespaldo: r.backup_codes ?? r.backupCodes ?? [],
  }
}

type Configuracion = ReturnType<typeof leerConfiguracion>

function ImagenQr({ otpauthUrl, qrBackend }: { otpauthUrl?: string; qrBackend?: string }) {
  // QR generado en el navegador desde otpauth_url; si no se puede, el data URI PNG que también envía el backend.
  const [generado, setGenerado] = useState<{ para: string; src: string | null } | null>(null)
  useEffect(() => {
    if (!otpauthUrl) return
    let vigente = true
    QRCode.toDataURL(otpauthUrl, { width: 220, margin: 1, errorCorrectionLevel: 'M' })
      .then((url) => vigente && setGenerado({ para: otpauthUrl, src: url }))
      .catch(() => vigente && setGenerado({ para: otpauthUrl, src: null }))
    return () => {
      vigente = false
    }
  }, [otpauthUrl])
  const respaldo = qrBackend?.startsWith('data:image/') ? qrBackend : null
  const src = otpauthUrl ? (generado?.para === otpauthUrl ? (generado.src ?? respaldo) : null) : respaldo
  if (!src) return null
  return (
    <img
      src={src}
      width={220}
      height={220}
      alt="Código QR para registrar la cuenta en tu aplicación autenticadora"
      className="rounded-panel bg-surface-container-lowest p-2 shadow-card"
    />
  )
}

function CodigosRespaldo({ codigos }: { codigos: string[] }) {
  const [copiado, setCopiado] = useState<string | null>(null)
  const texto = codigos.join('\n')

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(texto)
      setCopiado('Códigos copiados al portapapeles.')
    } catch {
      setCopiado('No se pudo copiar. Selecciona los códigos y cópialos manualmente.')
    }
  }

  const descargar = () => {
    const contenido = `Códigos de respaldo 2FA · OdontoMax (Villadent · Villamax)\nCada código sirve una sola vez.\n\n${texto}\n`
    const url = URL.createObjectURL(new Blob([contenido], { type: 'text/plain;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'codigos-respaldo-2fa.txt'
    a.click()
    URL.revokeObjectURL(url)
    setCopiado('Archivo descargado.')
  }

  return (
    <section aria-labelledby="titulo-respaldo" className="flex flex-col gap-3 rounded-panel bg-surface-container p-4">
      <h2 id="titulo-respaldo" className="font-display text-lg font-bold text-primary">
        Códigos de respaldo
      </h2>
      <p className="rounded-panel bg-secondary-fixed p-3 text-sm text-on-secondary-fixed">
        <strong>Guárdalos ahora en un lugar seguro.</strong> No se volverán a mostrar. Cada código sirve una sola vez
        y te permite entrar si pierdes el teléfono.
      </p>
      <ul aria-label="Códigos de respaldo" className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {codigos.map((c) => (
          <li key={c} className="rounded-control bg-surface-container-lowest px-3 py-2 text-center font-mono font-bold tracking-widest">
            {c}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button variant="secundario" size="sm" onClick={() => void copiar()}>
          <Copy aria-hidden />
          Copiar códigos
        </Button>
        <Button variant="secundario" size="sm" onClick={descargar}>
          <Download aria-hidden />
          Descargar .txt
        </Button>
      </div>
      <p role="status" className="text-sm text-on-surface-variant empty:hidden">
        {copiado}
      </p>
    </section>
  )
}

/** ADMIN_GENERAL: generar secreto + QR + códigos de respaldo y activar con el primer código. */
function ConfigurarSegundoFactor({ sesion }: { sesion: Sesion }) {
  const activo = sesion.segundoFactor === 'activo'
  const [aviso, setAviso] = useState<AvisoAcceso | null>(null)
  const [config, setConfig] = useState<Configuracion | null>(null)
  const [activado, setActivado] = useState<string | null>(null)

  const formGenerar = useForm<{ codigo: string }>({
    resolver: zodResolver(activo ? codigoActualObligatorio : codigoActualOpcional),
    defaultValues: { codigo: '' },
  })
  const formVerificar = useForm<{ codigo: string }>({
    resolver: zodResolver(esquemaVerificar),
    defaultValues: { codigo: '' },
  })

  const generar = async ({ codigo }: { codigo: string }) => {
    setAviso(null)
    try {
      const r = await configurar2fa(activo ? codigo : undefined)
      setConfig(leerConfiguracion(r))
      // Con la nueva configuración el 2FA queda inactivo hasta verificar el primer código (iniciarConfiguracionTotp).
      actualizarSegundoFactor(sesion, 'pendiente')
    } catch (e) {
      setAviso(interpretarError(e, 'No se pudo iniciar la configuración del 2FA.'))
      formGenerar.resetField('codigo')
    }
  }

  const verificar = async ({ codigo }: { codigo: string }) => {
    setAviso(null)
    try {
      const r = await verificarActivar2fa(codigo)
      setActivado(r.mensaje)
      setConfig(null)
      actualizarSegundoFactor(sesion, 'activo')
    } catch (e) {
      setAviso(interpretarError(e, 'Código TOTP inválido.'))
      formVerificar.resetField('codigo')
      formVerificar.setFocus('codigo')
    }
  }

  if (activado) {
    return (
      <div className="flex flex-col gap-4">
        <Alert tono="exito" titulo="Verificación en dos pasos activa">
          {activado}
        </Alert>
        <Link to="/app" className={buttonVariants({ variant: 'accion', className: 'w-full' })}>
          Continuar al sistema
        </Link>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="empty:hidden">{aviso && <AvisoAccesoAlert aviso={aviso} titulo={tituloErrorOperacion(aviso)} onCerrar={() => setAviso(null)} />}</div>

      {!config ? (
        <form
          noValidate
          onSubmit={formGenerar.handleSubmit(generar)}
          className="flex flex-col gap-4"
          aria-label="Generar configuración 2FA"
        >
          <p className="text-on-surface-variant">
            {activo
              ? 'Tu verificación en dos pasos está activa. Para registrarla en otro teléfono, confirma con tu código actual o uno de respaldo: se generará un secreto nuevo y el anterior dejará de servir.'
              : 'Genera el código QR y regístralo en tu aplicación autenticadora (Google Authenticator, Microsoft Authenticator, Authy…).'}
          </p>
          {activo && (
            <Field label="Código actual o de respaldo" error={formGenerar.formState.errors.codigo?.message}>
              <Input
                icono={<KeyRound />}
                autoComplete="one-time-code"
                maxLength={8}
                spellCheck={false}
                className="font-mono tracking-[0.3em]"
                {...formGenerar.register('codigo')}
              />
            </Field>
          )}
          <Button type="submit" variant="primario" cargando={formGenerar.formState.isSubmitting}>
            <QrCode aria-hidden />
            {activo ? 'Generar nueva configuración' : 'Generar código QR'}
          </Button>
        </form>
      ) : (
        <>
          <section aria-labelledby="titulo-qr" className="flex flex-col items-center gap-3 text-center">
            <h2 id="titulo-qr" className="font-display text-lg font-bold text-primary">
              1. Escanea el código QR
            </h2>
            <ImagenQr otpauthUrl={config.otpauthUrl} qrBackend={config.qrBackend} />
            {config.secreto && (
              <p className="text-sm text-on-surface-variant">
                ¿No puedes escanear? Ingresa esta clave manualmente:{' '}
                <code className="rounded bg-surface-container px-2 py-1 font-mono font-bold break-all text-on-surface">
                  {config.secreto}
                </code>
              </p>
            )}
          </section>
          {config.codigosRespaldo.length > 0 && <CodigosRespaldo codigos={config.codigosRespaldo} />}
          <form
            noValidate
            onSubmit={formVerificar.handleSubmit(verificar)}
            className="flex flex-col gap-4"
            aria-label="Verificar y activar 2FA"
          >
            <h2 className="font-display text-lg font-bold text-primary">2. Confirma con el primer código</h2>
            <Field label="Código de 6 dígitos" error={formVerificar.formState.errors.codigo?.message}>
              <Input
                icono={<ShieldCheck />}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                className="font-mono tracking-[0.3em]"
                {...formVerificar.register('codigo')}
              />
            </Field>
            <Button type="submit" variant="accion" cargando={formVerificar.formState.isSubmitting}>
              Verificar y activar
            </Button>
          </form>
        </>
      )}
    </div>
  )
}

/** Roles distintos de ADMIN_GENERAL con el 2FA activo: desactivarlo con contraseña + código. */
function DesactivarSegundoFactor({ sesion }: { sesion: Sesion }) {
  const [aviso, setAviso] = useState<ErrorAcceso | null>(null)
  const [hecho, setHecho] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof esquemaDesactivar>>({
    resolver: zodResolver(esquemaDesactivar),
    defaultValues: { password: '', codigo: '' },
  })

  const enviar = async ({ password, codigo }: z.infer<typeof esquemaDesactivar>) => {
    setAviso(null)
    try {
      const r = await desactivar2fa(password, codigo)
      setHecho(r.mensaje)
      actualizarSegundoFactor(sesion, 'inactivo')
    } catch (e) {
      // 400: respuesta unificada (contraseña o código), 429: bloqueo, 403: política.
      setAviso(interpretarError(e, 'Contraseña actual o código de autenticación 2FA incorrectos.'))
      reset({ password: '', codigo: '' })
    }
  }

  if (hecho) {
    return (
      <Alert tono="exito" titulo="Verificación en dos pasos desactivada">
        {hecho}
      </Alert>
    )
  }

  return (
    <form noValidate onSubmit={handleSubmit(enviar)} className="flex flex-col gap-4" aria-label="Desactivar 2FA">
      <h2 className="font-display text-lg font-bold text-primary">Desactivar la verificación en dos pasos</h2>
      <p className="text-on-surface-variant">Confirma con tu contraseña actual y un código 2FA o de respaldo.</p>
      <div className="empty:hidden">{aviso && <AvisoAccesoAlert aviso={aviso} titulo={tituloErrorOperacion(aviso)} onCerrar={() => setAviso(null)} />}</div>
      <Field label="Contraseña actual" error={errors.password?.message}>
        <PasswordInput autoComplete="current-password" {...register('password')} />
      </Field>
      <Field label="Código 2FA o de respaldo" error={errors.codigo?.message}>
        <Input
          icono={<ShieldCheck />}
          autoComplete="one-time-code"
          maxLength={8}
          spellCheck={false}
          className="font-mono tracking-[0.3em]"
          {...register('codigo')}
        />
      </Field>
      <Button type="submit" variant="peligro" cargando={isSubmitting}>
        <ShieldOff aria-hidden />
        Desactivar 2FA
      </Button>
    </form>
  )
}

export function Ajustes2faPage() {
  const flujo = useFlujoAcceso()
  const sesion = useSesion()
  // Se decide al entrar: tras desactivar, el formulario sigue montado para mostrar la confirmación.
  const [ofrecerDesactivar] = useState(() => sesion?.segundoFactor === 'activo')

  if (!sesion || sesion.demo) {
    return (
      <div className="flex flex-col gap-6">
        <TituloAcceso eyebrow="Seguridad de la cuenta" titulo="Ajustes 2FA" />
        <Alert tono="info" titulo="Inicia sesión primero">
          Los ajustes de la verificación en dos pasos requieren una sesión real.
        </Alert>
        <Link to={RUTAS_ACCESO.login} className={buttonVariants({ variant: 'primario' })}>
          Ir a iniciar sesión
        </Link>
      </div>
    )
  }

  const esAdminGeneral = sesion.usuario.rol === 'ADMIN_GENERAL'

  return (
    <div className="flex flex-col gap-6">
      <TituloAcceso eyebrow="Seguridad de la cuenta" titulo="Ajustes 2FA">
        Verificación en dos pasos (TOTP) de {sesion.usuario.correo ?? sesion.usuario.nombre}.
      </TituloAcceso>

      {flujo.requiereConfigurar && sesion.segundoFactor !== 'activo' && (
        <Alert tono="info" titulo="Configura la verificación en dos pasos">
          Tu rol exige la verificación en dos pasos (2FA). Configúrala ahora para proteger los datos de los pacientes.
        </Alert>
      )}

      {esAdminGeneral ? (
        <>
          <ConfigurarSegundoFactor sesion={sesion} />
          <p className="rounded-panel bg-surface-container p-4 text-sm text-on-surface-variant">
            Como Administración General, la verificación en dos pasos es obligatoria por política institucional y no
            puede desactivarse desde aquí.
          </p>
        </>
      ) : ofrecerDesactivar ? (
        <DesactivarSegundoFactor sesion={sesion} />
      ) : (
        <Alert tono="info" titulo="Sin verificación en dos pasos">
          Tu cuenta no tiene la verificación en dos pasos activa. Por ahora, el sistema solo permite configurarla a la
          Administración General.
        </Alert>
      )}

      <Link to="/app" className={buttonVariants({ variant: 'enlace' })}>
        Ir al sistema
      </Link>
    </div>
  )
}
