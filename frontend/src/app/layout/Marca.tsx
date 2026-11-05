import { ScanLine } from 'lucide-react'
import { cn } from '@/design-system'
import type { Negocio } from '@/shared/auth'

/** Diente de Villadent para adornos de las páginas públicas (el emblema completo va en `EmblemaVilladent`). */
export function IconoDiente({ className }: { className?: string }) {
  return (
    <img
      src="/logo-villadent-icon-dark.png"
      alt=""
      aria-hidden
      className={cn('size-7 object-contain', className)}
    />
  )
}

/** Emblema oficial de Villadent: lleva su propio disco blanco, así que se lee igual sobre fondo claro u oscuro. */
export function EmblemaVilladent({ className }: { className?: string }) {
  return (
    <img
      src="/logo-villadent-128.png"
      srcSet="/logo-villadent-128.png 1x, /logo-villadent-512.png 4x"
      alt="Villadent"
      className={cn('size-11 shrink-0 rounded-full object-contain ring-2 ring-brand-gold/40', className)}
    />
  )
}

interface MarcaProps {
  compacta?: boolean
  className?: string
  negocio?: Negocio
}

/** Marca del negocio activo: el emblema de Villadent, o la insignia de Villamax 3D con su propio color. */
export function Marca({ compacta = false, className, negocio = 'VILLADENT' }: MarcaProps) {
  const esVillamax = negocio === 'VILLAMAX'
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      {esVillamax ? (
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-tertiary text-on-tertiary ring-2 ring-tertiary/30">
          <ScanLine aria-hidden className="size-6" />
          {compacta && <span className="sr-only">Villamax 3D</span>}
        </span>
      ) : (
        <EmblemaVilladent />
      )}
      {!compacta && (
        <div className="flex flex-col leading-none">
          <span className={cn('font-display text-lg font-bold tracking-tight whitespace-nowrap', esVillamax ? 'text-tertiary' : 'text-primary')}>
            {esVillamax ? 'VILLAMAX 3D' : 'Villadent'}
          </span>
          <span className="mt-1 text-xs font-medium tracking-widest text-on-surface-variant whitespace-nowrap uppercase">
            {esVillamax ? 'Centro Radiológico' : 'Red Odontológica'}
          </span>
        </div>
      )}
    </div>
  )
}
