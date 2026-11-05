import type { ComponentProps } from 'react'
import { cn } from '../cn'

/** Tecla o atajo de teclado (B0: "Ctrl+K"). */
export function Kbd({ className, ...props }: ComponentProps<'kbd'>) {
  return (
    <kbd
      className={cn(
        'inline-flex h-6 min-w-6 items-center justify-center rounded-md bg-surface-container-low px-1.5 font-body text-xs font-bold text-on-surface-variant',
        className,
      )}
      {...props}
    />
  )
}
