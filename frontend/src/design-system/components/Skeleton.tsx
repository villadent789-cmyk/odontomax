import type { ComponentProps } from 'react'
import { cn } from '../cn'

/** Bloque de carga. Es decorativo: anuncia la carga el contenedor (aria-busy) o un texto "Cargando…". */
export function Skeleton({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      aria-hidden
      data-slot="skeleton"
      className={cn('animate-pulse rounded-control bg-surface-container-high', className)}
      {...props}
    />
  )
}

/** Texto solo para lectores de pantalla. */
export function SoloLector({ className, ...props }: ComponentProps<'span'>) {
  return <span className={cn('sr-only', className)} {...props} />
}
