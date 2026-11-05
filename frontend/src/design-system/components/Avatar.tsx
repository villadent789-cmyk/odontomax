import { Avatar as AvatarPrimitive } from 'radix-ui'
import { cn } from '../cn'
import { iniciales } from '../iniciales'

interface AvatarProps {
  nombre: string
  src?: string
  tamano?: 'sm' | 'md' | 'lg'
  className?: string
}

const tamanos = { sm: 'size-8 text-xs', md: 'size-11 text-sm', lg: 'size-14 text-base' }

/** Avatar con iniciales de respaldo. El nombre ya se muestra junto al avatar, así que la imagen es decorativa. */
export function Avatar({ nombre, src, tamano = 'md', className }: AvatarProps) {
  return (
    <AvatarPrimitive.Root
      className={cn(
        'relative inline-flex shrink-0 overflow-hidden rounded-full bg-primary-container text-on-primary-container',
        tamanos[tamano],
        className,
      )}
    >
      {src && <AvatarPrimitive.Image src={src} alt="" className="size-full object-cover" />}
      <AvatarPrimitive.Fallback aria-hidden className="flex size-full items-center justify-center font-bold">
        {iniciales(nombre)}
      </AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  )
}
