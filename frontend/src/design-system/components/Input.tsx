import type { ComponentProps, ReactNode } from 'react'
import { cn } from '../cn'

export const inputClases =
  'h-11 w-full min-w-0 rounded-control border border-outline-variant/60 bg-surface-container-low/80 px-4 font-body text-base text-on-surface placeholder:text-placeholder transition-all outline-none hover:border-outline focus-visible:border-focus focus-visible:bg-surface-container-lowest focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-focus disabled:cursor-not-allowed disabled:bg-surface-container-highest disabled:text-outline aria-invalid:border-error aria-invalid:bg-error-container aria-invalid:text-on-error-container aria-invalid:focus-visible:outline-error'

export interface InputProps extends ComponentProps<'input'> {
  /** Ícono decorativo a la izquierda (se oculta a lectores de pantalla). */
  icono?: ReactNode
  /** Contenido a la derecha (ícono o botón). */
  sufijo?: ReactNode
}

export function Input({ className, icono, sufijo, type = 'text', ...props }: InputProps) {
  const input = (
    <input
      data-slot="input"
      type={type}
      className={cn(inputClases, icono && 'pl-11', sufijo && 'pr-12', className)}
      {...props}
    />
  )
  if (!icono && !sufijo) return input
  return (
    <div className="relative">
      {icono && (
        <span
          aria-hidden
          className="pointer-events-none absolute top-1/2 left-4 -translate-y-1/2 text-on-surface-variant [&_svg]:size-5"
        >
          {icono}
        </span>
      )}
      {input}
      {sufijo && <span className="absolute top-1/2 right-2 -translate-y-1/2">{sufijo}</span>}
    </div>
  )
}
