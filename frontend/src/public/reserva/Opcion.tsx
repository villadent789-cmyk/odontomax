import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/design-system'

interface OpcionProps extends Omit<ComponentProps<'input'>, 'type' | 'children'> {
  children: ReactNode
  /** Clases de la tarjeta (label). */
  claseTarjeta?: string
}

/**
 * Tarjeta seleccionable (A4): un radio nativo dentro de su etiqueta. Se navega con Tab / flechas como cualquier
 * grupo de radios y el foco visible se dibuja en toda la tarjeta.
 */
export function Opcion({ children, claseTarjeta, className, ...props }: OpcionProps) {
  return (
    <label
      className={cn(
        'flex cursor-pointer gap-4 rounded-card bg-surface-container-lowest p-5 shadow-card transition-[background-color,box-shadow] hover:bg-surface-container-low',
        'has-[:checked]:bg-surface-container has-[:checked]:shadow-raised',
        'has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
        claseTarjeta,
      )}
    >
      <input
        type="radio"
        className={cn('mt-1 size-5 shrink-0 cursor-pointer accent-primary-container focus-visible:outline-none', className)}
        {...props}
      />
      <span className="flex min-w-0 flex-1 flex-col gap-1">{children}</span>
    </label>
  )
}

/** Mensaje de error de un grupo de opciones (se anuncia al aparecer). */
export function ErrorGrupo({ id, mensaje }: { id: string; mensaje?: string }) {
  if (!mensaje) return null
  return (
    <p id={id} role="alert" className="text-sm font-bold text-error">
      {mensaje}
    </p>
  )
}
