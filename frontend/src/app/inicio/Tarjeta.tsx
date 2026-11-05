import type { ReactNode } from 'react'
import { Button, Skeleton } from '@/design-system'

/** Marco de una tarjeta del inicio: título, estado de carga y de error con "Reintentar". */
export function Tarjeta({
  id,
  titulo,
  icono,
  cargando,
  error,
  reintentar,
  className = '',
  children,
}: {
  id: string
  titulo: string
  icono: ReactNode
  cargando: boolean
  error: string | null
  reintentar: () => void
  className?: string
  children: ReactNode
}) {
  return (
    <article
      aria-labelledby={id}
      className={`flex flex-col gap-4 rounded-card border border-outline-variant/40 bg-surface-container-lowest p-5 shadow-card ${className}`}
    >
      <h2 id={id} className="flex items-center gap-2 font-display text-lg font-bold text-on-surface [&_svg]:size-5 [&_svg]:text-primary">
        {icono}
        {titulo}
      </h2>
      {cargando ? (
        <div role="status" aria-label={`Cargando ${titulo.toLowerCase()}`} className="flex flex-col gap-2">
          <Skeleton className="h-8 w-1/2" />
          <Skeleton className="h-5 w-full" />
          <Skeleton className="h-5 w-3/4" />
        </div>
      ) : error ? (
        <div role="alert" className="flex flex-col items-start gap-2 text-sm">
          <p className="text-on-surface-variant">No se pudo cargar esta información. {error}</p>
          <Button size="sm" variant="secundario" onClick={reintentar}>
            Reintentar
          </Button>
        </div>
      ) : (
        children
      )}
    </article>
  )
}

export function Dato({ etiqueta, valor, destacado = false }: { etiqueta: string; valor: ReactNode; destacado?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-sm text-on-surface-variant">{etiqueta}</dt>
      <dd className={destacado ? 'font-display text-2xl font-bold text-primary' : 'font-bold text-on-surface'}>{valor}</dd>
    </div>
  )
}
