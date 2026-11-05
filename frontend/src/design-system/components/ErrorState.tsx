import { RotateCw, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../cn'
import { Button } from './Button'

interface ErrorStateProps {
  titulo?: ReactNode
  descripcion?: ReactNode
  icono?: ReactNode
  /** Si se indica, muestra el botón "Reintentar". */
  onReintentar?: () => void
  /** Acción alternativa en lugar de reintentar. */
  accion?: ReactNode
  className?: string
}

/** Estado de error de una vista o bloque. Se anuncia con role="alert". */
export function ErrorState({
  titulo = 'No pudimos cargar esta información',
  descripcion = 'Revisa tu conexión e inténtalo de nuevo. Si el problema continúa, avisa a soporte.',
  icono = <TriangleAlert />,
  onReintentar,
  accion,
  className,
}: ErrorStateProps) {
  return (
    <div role="alert" className={cn('flex flex-col items-center gap-3 px-6 py-10 text-center', className)}>
      <div
        aria-hidden
        className="mb-1 flex size-16 items-center justify-center rounded-full bg-error-container text-on-error-container [&_svg]:size-8"
      >
        {icono}
      </div>
      <h3 className="font-display text-headline-md font-bold text-primary">{titulo}</h3>
      {descripcion && <p className="max-w-prose text-on-surface-variant">{descripcion}</p>}
      {(onReintentar || accion) && (
        <div className="mt-2">
          {accion ?? (
            <Button variant="secundario" onClick={onReintentar}>
              <RotateCw aria-hidden />
              Reintentar
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
