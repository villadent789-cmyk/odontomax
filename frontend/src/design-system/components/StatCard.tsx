import type { ReactNode } from 'react'
import { cn } from '../cn'
import { Eyebrow } from './Label'
import { Skeleton } from './Skeleton'

type TonoIcono = 'primario' | 'accion' | 'villamax'

const colorIcono: Record<TonoIcono, string> = {
  primario: 'text-on-surface-variant',
  accion: 'text-acento',
  villamax: 'text-tertiary',
}

interface StatCardProps {
  etiqueta: string
  valor?: ReactNode
  /** Texto a la derecha del valor (p. ej. "Sede Chilca"). */
  complemento?: ReactNode
  /** Chip arriba a la derecha. */
  insignia?: ReactNode
  icono?: ReactNode
  tonoIcono?: TonoIcono
  /** Pie de la tarjeta (barra de progreso, desglose...). */
  pie?: ReactNode
  cargando?: boolean
  className?: string
}

/**
 * Tarjeta de indicador (KPI) de B0. Solo se debe usar con datos reales de la API:
 * no hay que dibujar indicadores con datos inventados.
 */
export function StatCard({
  etiqueta,
  valor,
  complemento,
  insignia,
  icono,
  tonoIcono = 'primario',
  pie,
  cargando = false,
  className,
}: StatCardProps) {
  return (
    <section
      aria-busy={cargando || undefined}
      aria-label={etiqueta}
      className={cn('flex flex-col gap-2 rounded-card border border-outline-variant/70 bg-surface-container-lowest p-5 shadow-card', className)}
    >
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
        <div className="flex max-w-full items-center gap-2">
          {icono && (
            <span aria-hidden className={cn('flex shrink-0 items-center [&_svg]:size-4', colorIcono[tonoIcono])}>
              {icono}
            </span>
          )}
          <Eyebrow className="leading-tight">{etiqueta}</Eyebrow>
        </div>
        {insignia}
      </div>
      {cargando ? (
        <>
          <Skeleton className="h-9 w-2/3" />
          <span className="sr-only">Cargando…</span>
        </>
      ) : (
        <p className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span className="font-display text-2xl font-semibold tracking-tight whitespace-nowrap text-on-surface xl:text-3xl">{valor}</span>
          {complemento && <span className="text-primary">{complemento}</span>}
        </p>
      )}
      {pie && !cargando && <div className="mt-auto pt-1 text-xs text-on-surface-variant">{pie}</div>}
    </section>
  )
}
