import { useRouter } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { ScanLine } from 'lucide-react'
import { cn } from '@/design-system'
import { cambiarNegocioActivo, esRutaPermitidaParaNegocio, negociosDe, type Negocio, type SedeSesion } from '@/shared/auth'

interface SelectorNegocioProps {
  sedes: SedeSesion[]
  negocioActivo?: Negocio
}

/**
 * Selector de negocio de alto nivel (B0 / Paso A): "Villadent | Villamax 3D".
 * Solo visible para usuarios con sedes en ambos negocios (ADMIN_GENERAL/dueños).
 * Un usuario con sedes de un solo negocio no ve el selector y entra directo al negocio correspondiente.
 */
export function SelectorNegocio({ sedes, negocioActivo }: SelectorNegocioProps) {
  const queryClient = useQueryClient()
  const router = useRouter()
  const sedesValidas = sedes.filter((s) => s.id !== 'sede-chilca-2')
  const negocios = negociosDe(sedesValidas)

  // Solo lo ven los usuarios con acceso a ambos negocios (ADMIN_GENERAL/dueños).
  if (negocios.length <= 1) return null

  const activo = negocioActivo ?? negocios[0]


  async function elegirNegocio(nuevo: Negocio) {
    if (nuevo === activo) return
    // Una ficha abierta (p. ej. /app/pacientes/:id) es de un solo negocio: se sale de ella ANTES de cambiar de negocio,
    // para que no se pida con el negocio nuevo y termine en "Sin permiso".
    const rutaActual = router.state.location.pathname
    const esDetalle = rutaActual.split('/').filter(Boolean).length > 2
    if (esDetalle || !esRutaPermitidaParaNegocio(rutaActual, nuevo)) {
      await router.navigate({ to: '/app' })
    }
    cambiarNegocioActivo(nuevo)
    // Descartar caché completo de React Query para no mezclar datos entre negocios
    queryClient.clear()
    void queryClient.resetQueries()
  }

  return (
    <div
      role="group"
      aria-label="Negocio activo"
      className="flex shrink-0 items-center gap-1"
    >
      <button
        type="button"
        aria-pressed={activo === 'VILLADENT'}
        onClick={() => void elegirNegocio('VILLADENT')}
        className={cn(
          'inline-flex min-h-8 max-md:min-h-10 pointer-coarse:min-h-10 items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
          activo === 'VILLADENT'
            ? 'bg-brand-gold text-primary shadow-sm'
            : 'text-on-surface-variant hover:bg-surface-container-high hover:text-primary',
        )}
      >
        <img src="/logo-villadent-64.png" alt="" aria-hidden className="size-5 rounded-full object-contain" />
        <span>Villadent</span>
      </button>

      <button
        type="button"
        aria-pressed={activo === 'VILLAMAX'}
        onClick={() => void elegirNegocio('VILLAMAX')}
        className={cn(
          'inline-flex min-h-8 items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
          activo === 'VILLAMAX'
            ? 'bg-tertiary text-on-tertiary shadow-sm'
            : 'text-on-surface-variant hover:bg-surface-container-high hover:text-tertiary',
        )}
      >
        <ScanLine aria-hidden className="size-4 text-inherit" />
        <span>Villamax 3D</span>
      </button>
    </div>
  )
}