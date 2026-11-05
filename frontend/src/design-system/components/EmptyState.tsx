import { Inbox } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../cn'

interface EmptyStateProps {
  titulo: ReactNode
  descripcion?: ReactNode
  icono?: ReactNode
  /** Acción sugerida (normalmente un Button). */
  accion?: ReactNode
  className?: string
}

/** Estado vacío: no hay datos que mostrar (no es un error). */
export function EmptyState({ titulo, descripcion, icono = <Inbox />, accion, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center gap-3 px-6 py-10 text-center', className)}>
      <div
        aria-hidden
        className="mb-1 flex size-16 items-center justify-center rounded-full bg-surface-container text-primary [&_svg]:size-8"
      >
        {icono}
      </div>
      <h3 className="font-display text-headline-md font-bold text-primary">{titulo}</h3>
      {descripcion && <p className="max-w-prose text-on-surface-variant">{descripcion}</p>}
      {accion && <div className="mt-2">{accion}</div>}
    </div>
  )
}
