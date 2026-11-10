import { useNavigate } from '@tanstack/react-router'
import { tieneRol } from '@/shared/auth/sesion'
import { ChevronDown, KeyRound, LogOut, Menu, ShieldCheck, Stethoscope } from 'lucide-react'
import { DropdownMenu } from 'radix-ui'
import { useState } from 'react'
import { CambiarPasswordDialog } from '@/app/auth/CambiarPasswordDialog'
import { RUTAS_ACCESO } from '@/app/auth/rutas'
import { Avatar } from '@/design-system'
import { RUTA_LOGIN, nombreDeFunciones, limpiarSesion, negociosDe, type Sesion } from '@/shared/auth'
import { BusquedaRapida } from './BusquedaRapida'
import { MiEspecialidadDialog } from './MiEspecialidadDialog'
import { SelectorNegocio } from './SelectorNegocio'
import { SelectorSede } from './SelectorSede'

const claseBotonIcono =
  'inline-flex size-11 items-center justify-center rounded-pill text-on-surface-variant hover:bg-surface-container-high hover:text-primary focus-visible:outline-2 focus-visible:outline-focus [&_svg]:size-5'

const claseItemMenu =
  'flex cursor-pointer items-center gap-3 rounded-control px-3 py-2 text-sm font-bold outline-none select-none data-[highlighted]:bg-surface-container-high data-[highlighted]:text-primary [&_svg]:size-4'

export function Cabecera({ sesion, onAbrirMenu }: { sesion: Sesion; onAbrirMenu?: () => void }) {
  const navigate = useNavigate()
  const { usuario } = sesion
  const [cambiarPassword, setCambiarPassword] = useState(false)
  const [miEspecialidad, setMiEspecialidad] = useState(false)

  const cerrarSesion = () => {
    limpiarSesion()
    void navigate({ to: RUTA_LOGIN })
  }

  const tieneVariosNegocios = negociosDe(usuario.sedes.filter((s) => s.id !== 'sede-chilca-2')).length > 1

  return (
    <header className="sticky top-0 z-30 flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-outline-variant/70 bg-surface/90 px-4 py-2.5 backdrop-blur md:px-8 print:hidden no-print">
      <button type="button" onClick={onAbrirMenu} aria-label="Abrir menú" className={`${claseBotonIcono} md:hidden`}>
        <Menu aria-hidden />
      </button>
      {/* Panel único de negocio y sede (B0 / Paso A): en celular baja a una segunda fila */}
      <div className="order-last flex w-full max-w-full flex-wrap items-center gap-2 rounded-panel border border-outline-variant/70 bg-surface-container-lowest p-1 md:order-none md:w-auto">
        <SelectorNegocio sedes={usuario.sedes} negocioActivo={sesion.negocioActivo} />
        {tieneVariosNegocios && (
          <span className="h-5 w-px bg-outline-variant/50 hidden sm:inline-block" aria-hidden />
        )}
        <SelectorSede sedes={usuario.sedes} sedeActivaId={sesion.sedeActivaId} negocioActivo={sesion.negocioActivo} />
      </div>

      <div className="ml-auto flex items-center gap-1 md:gap-4">
        <BusquedaRapida sedeId={sesion.sedeActivaId} />
        <DropdownMenu.Root>
          <DropdownMenu.Trigger
            aria-label={`Menú de cuenta de ${usuario.nombre}`}
            className="flex items-center gap-2 rounded-control border border-transparent px-1.5 py-1 text-left hover:bg-surface-container hover:border-outline-variant/70 focus-visible:outline-2 focus-visible:outline-focus transition-colors"
          >
            <Avatar nombre={usuario.nombre} tamano="sm" />
            <span className="hidden text-left leading-tight xl:block">
              <span className="block font-display text-sm font-semibold text-primary">{usuario.nombre}</span>
              <span className="block text-xs text-on-surface-variant">{nombreDeFunciones(usuario.rol, usuario.rolesAdicionales)}</span>
            </span>
            <ChevronDown aria-hidden className="size-3.5 text-on-surface-variant ml-0.5" />
          </DropdownMenu.Trigger>
          <DropdownMenu.Portal>
            <DropdownMenu.Content
              align="end"
              sideOffset={8}
              className="z-40 flex min-w-56 flex-col gap-1 rounded-panel bg-surface-container-lowest p-2 text-on-surface shadow-raised"
            >
              <DropdownMenu.Label className="px-3 py-2 text-sm text-on-surface-variant">
                {usuario.correo ?? usuario.nombre}
              </DropdownMenu.Label>
              <DropdownMenu.Item onSelect={() => setCambiarPassword(true)} className={claseItemMenu}>
                <KeyRound aria-hidden />
                Cambiar contraseña
              </DropdownMenu.Item>
              {tieneRol(usuario, 'ODONTOLOGO') && (
                <DropdownMenu.Item onSelect={() => setMiEspecialidad(true)} className={claseItemMenu}>
                  <Stethoscope aria-hidden />
                  Mi especialidad
                </DropdownMenu.Item>
              )}
              {!sesion.demo && (
                <DropdownMenu.Item onSelect={() => void navigate({ to: RUTAS_ACCESO.ajustes2fa })} className={claseItemMenu}>
                  <ShieldCheck aria-hidden />
                  Ajustes 2FA
                </DropdownMenu.Item>
              )}
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>
        <span className="sr-only sm:hidden">
          {usuario.nombre}, {nombreDeFunciones(usuario.rol, usuario.rolesAdicionales)}
        </span>
        <CambiarPasswordDialog abierto={cambiarPassword} onCambio={setCambiarPassword} />
        <MiEspecialidadDialog abierto={miEspecialidad} onCambio={setMiEspecialidad} usuarioId={usuario.id} sedeId={sesion.sedeActivaId} />

        <button type="button" onClick={cerrarSesion} aria-label="Cerrar sesión" title="Cerrar sesión" className={claseBotonIcono}>
          <LogOut aria-hidden />
        </button>
      </div>
    </header>
  )
}