import { cn } from '@/design-system'
import { useSesion } from '@/shared/auth'

function textoMostrando(sesion: ReturnType<typeof useSesion>): string {
  const negocio = sesion?.negocioActivo === 'VILLAMAX' ? 'Villamax 3D' : 'Villadent'
  const sedeObj = sesion?.usuario.sedes.find((s) => s.id === sesion?.sedeActivaId)
  const sedeNombre = sesion?.sedeActivaId ? (sedeObj?.nombre ?? sesion.sedeActivaId) : 'Todas'
  return `Mostrando: ${negocio} · ${sedeNombre}`
}

export function RotuloMostrando({ className }: { className?: string }) {
  const sesion = useSesion()
  if (!sesion) return null
  return (
    <span
      data-testid="rotulo-mostrando"
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border border-outline-variant/30 bg-surface-container-low/70 px-3 py-1 text-xs font-medium text-on-surface-variant',
        className,
      )}
    >
      <span className="size-1.5 rounded-full bg-primary" aria-hidden />
      <span>{textoMostrando(sesion)}</span>
    </span>
  )
}