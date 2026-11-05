import type { ComponentProps } from 'react'
import { cn } from '../cn'
import { badgeVariants, type BadgeVariantProps, type TonoBadge } from '../variants'

const colorPunto: Record<TonoBadge, string> = {
  neutro: 'bg-outline',
  primario: 'bg-primary',
  accion: 'bg-secondary',
  villamax: 'bg-tertiary',
  peligro: 'bg-error',
  claro: 'bg-primary',
}

export interface BadgeProps extends ComponentProps<'span'>, BadgeVariantProps {
  /** Muestra un punto de color antes del texto (chips de estado de la guía A1). */
  punto?: boolean
}

/** Etiqueta/chip (en el diseño se llaman "chips"). El color nunca es la única señal: siempre lleva texto. */
export function Badge({ className, tono, mayusculas, punto, children, ...props }: BadgeProps) {
  return (
    <span data-slot="badge" className={cn(badgeVariants({ tono, mayusculas }), className)} {...props}>
      {punto && <span aria-hidden className={cn('size-2 rounded-full', colorPunto[tono ?? 'neutro'])} />}
      {children}
    </span>
  )
}
