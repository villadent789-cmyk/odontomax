import { Loader2 } from 'lucide-react'
import { Slot } from 'radix-ui'
import type { ComponentProps } from 'react'
import { cn } from '../cn'
import { buttonVariants, type ButtonVariantProps } from '../variants'

export interface ButtonProps extends ComponentProps<'button'>, ButtonVariantProps {
  /** Renderiza el hijo (p. ej. un Link) con los estilos del botón. */
  asChild?: boolean
  /** Muestra un indicador de carga y deshabilita el botón. */
  cargando?: boolean
}

export function Button({
  className,
  variant,
  size,
  asChild = false,
  cargando = false,
  disabled,
  children,
  type,
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot.Root : 'button'
  return (
    <Comp
      data-slot="button"
      className={cn(buttonVariants({ variant, size }), className)}
      disabled={asChild ? undefined : disabled || cargando}
      aria-busy={cargando || undefined}
      type={asChild ? undefined : (type ?? 'button')}
      {...props}
    >
      {asChild ? (
        children
      ) : (
        <>
          {cargando && <Loader2 aria-hidden className="animate-spin" />}
          {children}
        </>
      )}
    </Comp>
  )
}
