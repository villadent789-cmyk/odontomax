import { Label as LabelPrimitive } from 'radix-ui'
import type { ComponentProps } from 'react'
import { cn } from '../cn'

/** Etiqueta de campo de formulario (B1: "Correo Corporativo"). */
export function Label({ className, ...props }: ComponentProps<typeof LabelPrimitive.Root>) {
  return (
    <LabelPrimitive.Root
      data-slot="label"
      className={cn(
        'text-sm font-bold text-on-surface select-none peer-disabled:cursor-not-allowed peer-disabled:opacity-60',
        className,
      )}
      {...props}
    />
  )
}

/** Etiqueta en mayúsculas con tracking (B1: "CONTROL DE ACCESO", B0: "SILLONES ODONTOLÓGICOS"). */
export function Eyebrow({ className, ...props }: ComponentProps<'p'>) {
  return (
    <p
      className={cn('text-xs font-semibold tracking-etiqueta text-on-surface-variant uppercase', className)}
      {...props}
    />
  )
}
