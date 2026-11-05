import { cva, type VariantProps } from 'class-variance-authority'
import { CircleAlert, CircleCheck, Info, LockKeyhole, TriangleAlert, X } from 'lucide-react'
import type { ComponentProps, ReactNode } from 'react'
import { cn } from '../cn'

const alertVariants = cva('relative flex gap-3 rounded-panel p-4 pr-12 text-sm [&_svg]:size-5 [&_svg]:shrink-0', {
  variants: {
    tono: {
      error: 'bg-error-container text-on-error-container',
      /** Bloqueo por intentos (429 / cuenta bloqueada), B1. */
      bloqueo: 'bg-error-container text-on-error-container',
      info: 'bg-surface-container text-on-surface',
      /** Advertencia que no bloquea: algo que conviene resolver antes de seguir (p. ej. caja cerrada). */
      aviso: 'bg-secondary-fixed text-on-secondary-fixed',
      exito: 'bg-primary-fixed text-on-primary-fixed',
    },
  },
  defaultVariants: { tono: 'info' },
})

const iconos = {
  error: CircleAlert,
  bloqueo: LockKeyhole,
  info: Info,
  aviso: TriangleAlert,
  exito: CircleCheck,
} as const

export interface AlertProps extends Omit<ComponentProps<'div'>, 'title'>, VariantProps<typeof alertVariants> {
  titulo: ReactNode
  /** Si se indica, se muestra el botón "Cerrar aviso". */
  onCerrar?: () => void
}

/**
 * Aviso en línea. Los tonos error y bloqueo usan role="alert" (se anuncian al aparecer);
 * info y éxito usan role="status".
 */
export function Alert({ className, tono = 'info', titulo, onCerrar, children, ...props }: AlertProps) {
  const Icono = iconos[tono ?? 'info']
  const urgente = tono === 'error' || tono === 'bloqueo'
  return (
    <div role={urgente ? 'alert' : 'status'} className={cn(alertVariants({ tono }), className)} {...props}>
      <Icono aria-hidden className="mt-0.5" />
      <div className="flex flex-col gap-1">
        <p className="font-bold">{titulo}</p>
        {children && <div className="leading-relaxed">{children}</div>}
      </div>
      {onCerrar && (
        <button
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar aviso"
          className="absolute top-3 right-3 inline-flex size-8 items-center justify-center rounded-pill hover:bg-surface-container-lowest/60 focus-visible:outline-2 focus-visible:outline-focus"
        >
          <X aria-hidden />
        </button>
      )}
    </div>
  )
}
