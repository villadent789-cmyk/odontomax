import { CircleAlert } from 'lucide-react'
import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react'
import { cn } from '../cn'
import { Label } from './Label'

interface FieldProps {
  label: ReactNode
  /** Texto de ayuda bajo el campo. */
  ayuda?: ReactNode
  /** Mensaje de error; marca el control con aria-invalid y lo enlaza con aria-describedby. */
  error?: ReactNode
  /** Contenido a la derecha de la etiqueta (p. ej. "¿Olvidaste tu contraseña?"). */
  extra?: ReactNode
  /** El control (Input, PasswordInput, select...). Recibe id, aria-invalid y aria-describedby. */
  children: ReactElement<Record<string, unknown>>
  className?: string
}

/** Agrupa etiqueta + control + ayuda/error con las relaciones ARIA correctas. */
export function Field({ label, ayuda, error, extra, children, className }: FieldProps) {
  const id = useId()
  const idAyuda = `${id}-ayuda`
  const idError = `${id}-error`
  const describedBy = [error ? idError : null, ayuda ? idAyuda : null].filter(Boolean).join(' ') || undefined
  const control = isValidElement(children)
    ? cloneElement(children, {
        id,
        'aria-invalid': error ? true : undefined,
        'aria-describedby': describedBy,
      })
    : children

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex items-baseline justify-between gap-4">
        <Label htmlFor={id}>{label}</Label>
        {extra}
      </div>
      {control}
      {error && (
        <p id={idError} className="flex items-center gap-1.5 text-sm font-bold text-error">
          <CircleAlert aria-hidden className="size-4 shrink-0" />
          {error}
        </p>
      )}
      {ayuda && (
        <p id={idAyuda} className="text-sm text-on-surface-variant">
          {ayuda}
        </p>
      )}
    </div>
  )
}
