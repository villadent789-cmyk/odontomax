import { Layers, MapPin } from 'lucide-react'
import { cn } from '@/design-system'
import { cambiarSedeActiva, type Negocio, type SedeSesion } from '@/shared/auth'

interface SelectorSedeProps {
  sedes: SedeSesion[]
  sedeActivaId: string | null
  negocioActivo?: Negocio
}

/**
 * Selector de sede en píldoras (B0 / Paso A).
 * Lista ÚNICAMENTE las sedes pertenecientes al negocio activo (Villadent o Villamax).
 * "Todas" = todas las sedes del negocio activo, nunca mezcla negocios.
 * Villamax no aparece si el negocio activo es Villadent, y viceversa.
 * Con una sola sede para el negocio, se muestra como etiqueta fija.
 */
export function SelectorSede({ sedes, sedeActivaId, negocioActivo }: SelectorSedeProps) {
  // Filtrar sedes inactivas u obsoletas (sede-chilca-2)
  const sedesValidas = sedes.filter((s) => s.id !== 'sede-chilca-2')
  if (sedesValidas.length === 0) return null

  const negocio = negocioActivo ?? 'VILLADENT'
  const sedesDelNegocio = sedesValidas.filter((s) => s.marca === negocio)

  if (sedesDelNegocio.length === 0) return null

  if (sedesDelNegocio.length === 1) {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <p className="flex items-center gap-1.5 px-2 text-sm font-semibold text-primary">
          <MapPin aria-hidden className="size-4" />
          <span className="sr-only">Sede: </span>
          {sedesDelNegocio[0].nombre}
        </p>
      </div>
    )
  }

  const esTodas = sedeActivaId === null

  return (
    <div className="flex max-w-full flex-wrap items-center gap-2">
      <div
        role="group"
        aria-label="Sede activa"
        className="flex max-w-full items-center gap-1 overflow-x-auto [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        <button
          type="button"
          aria-pressed={esTodas}
          onClick={() => cambiarSedeActiva(null)}
          className={cn(
            'inline-flex min-h-8 max-md:min-h-10 pointer-coarse:min-h-10 shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
            esTodas
              ? 'bg-acento/15 text-primary ring-1 ring-acento/50 shadow-sm'
              : 'text-on-surface-variant hover:bg-surface-container-high hover:text-primary',
          )}
        >
          <Layers aria-hidden className="size-3.5 shrink-0" />
          <span>Todas</span>
        </button>
        {sedesDelNegocio.map((sede) => {
          const activa = sede.id === sedeActivaId
          return (
            <button
              key={sede.id}
              type="button"
              aria-pressed={activa}
              onClick={() => cambiarSedeActiva(sede.id)}
              className={cn(
                'inline-flex min-h-8 max-md:min-h-10 pointer-coarse:min-h-10 shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-bold transition-all focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
                activa
                  ? 'bg-acento/15 text-primary ring-1 ring-acento/50 shadow-sm'
                  : 'text-on-surface-variant hover:bg-surface-container-high hover:text-primary',
              )}
            >
              {activa ? (
                <MapPin aria-hidden className="size-3.5 shrink-0 text-primary" />
              ) : (
                <span
                  aria-hidden
                  className={cn('size-1.5 rounded-full', sede.marca === 'VILLAMAX' ? 'bg-tertiary' : 'bg-primary/40')}
                />
              )}
              <span>{sede.nombre}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}