import type { UseQueryResult } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Badge, EmptyState, ErrorState, Skeleton } from '@/design-system'
import { cn } from '@/design-system/cn'
import { esSinPermiso, mensajeError } from './errores'

interface SeccionProps {
  id: string
  titulo: string
  descripcion?: ReactNode
  icono: ReactNode
  /** Número que se muestra junto al título (p. ej. "3 pendientes"). */
  contador?: string
  tono?: 'primario' | 'accion' | 'villamax' | 'peligro'
  acciones?: ReactNode
  children: ReactNode
  className?: string
}

const fondoIcono = {
  primario: 'bg-acento text-on-acento',
  accion: 'bg-secondary-container text-on-secondary-container',
  villamax: 'bg-tertiary text-on-tertiary',
  peligro: 'bg-error-container text-on-error-container',
}

/** Tarjeta de sección de la bandeja (B2): ícono, título, contador y contenido. */
export function Seccion({ id, titulo, descripcion, icono, contador, tono = 'primario', acciones, children, className }: SeccionProps) {
  return (
    <section aria-labelledby={id} className={cn('flex flex-col gap-4 rounded-card bg-surface-container-lowest p-5 shadow-card md:p-6', className)}>
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <div aria-hidden className={cn('flex size-10 shrink-0 items-center justify-center rounded-panel [&_svg]:size-5', fondoIcono[tono])}>
            {icono}
          </div>
          <div className="flex flex-col gap-0.5">
            <div className="flex flex-wrap items-center gap-2">
              <h2 id={id} className="font-display text-xl font-bold text-primary">
                {titulo}
              </h2>
              {contador && (
                <Badge tono={tono === 'primario' ? 'primario' : tono}>{contador}</Badge>
              )}
            </div>
            {descripcion && <p className="text-sm text-on-surface-variant">{descripcion}</p>}
          </div>
        </div>
        {acciones}
      </header>
      {children}
    </section>
  )
}

interface ConsultaProps<T> {
  consulta: UseQueryResult<T[]>
  /** Texto para lectores de pantalla mientras carga. */
  cargando: string
  vacio: { titulo: string; descripcion?: string; icono?: ReactNode }
  filas?: number
  children: (datos: T[]) => ReactNode
  /** Filtro aplicado antes de decidir si está vacío. */
  filtro?: (dato: T) => boolean
}

/** Pinta esqueleto, error (con reintentar), vacío o los datos de una consulta de lista. */
export function Consulta<T>({ consulta, cargando, vacio, filas = 3, children, filtro }: ConsultaProps<T>) {
  if (consulta.isPending) {
    return (
      <div aria-busy="true" className="flex flex-col gap-3">
        <span className="sr-only" role="status">
          {cargando}
        </span>
        {Array.from({ length: filas }, (_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    )
  }
  if (consulta.isError) {
    return esSinPermiso(consulta.error) ? (
      <ErrorState titulo="Sin permiso" descripcion={mensajeError(consulta.error)} />
    ) : (
      <ErrorState descripcion={mensajeError(consulta.error)} onReintentar={() => void consulta.refetch()} />
    )
  }
  const datos = filtro ? consulta.data.filter(filtro) : consulta.data
  if (datos.length === 0) {
    return <EmptyState titulo={vacio.titulo} descripcion={vacio.descripcion} icono={vacio.icono} className="py-6" />
  }
  return <>{children(datos)}</>
}
