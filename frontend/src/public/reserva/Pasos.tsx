import { Check } from 'lucide-react'
import { cn } from '@/design-system'

export interface DefinicionPaso {
  /** Pregunta corta a la derecha de "PASO N DE 4" (A4: "¿Dónde y qué?"). */
  pregunta: string
  titulo: string
}

/** Indicador de progreso del asistente (A4): "PASO 1 DE 4" + círculos numerados unidos por una barra. */
export function Pasos({ pasos, actual }: { pasos: DefinicionPaso[]; actual: number }) {
  const total = pasos.length
  return (
    <div className="flex flex-col gap-4 rounded-card bg-surface-container-low px-5 py-5 sm:px-8">
      <div className="flex items-baseline justify-between gap-4">
        <p className="text-xs font-bold tracking-etiqueta text-primary uppercase">
          Paso {actual + 1} de {total}
        </p>
        <p className="font-display font-bold text-primary">{pasos[actual].pregunta}</p>
      </div>
      <ol aria-label="Pasos de la reserva" className="relative flex items-center justify-between">
        {/* Barra de fondo y de progreso, detrás de los círculos */}
        <span aria-hidden className="absolute inset-x-5 top-1/2 h-1 -translate-y-1/2 rounded-pill bg-surface-container-highest" />
        <span
          aria-hidden
          className="absolute top-1/2 left-5 h-1 -translate-y-1/2 rounded-pill bg-primary-container transition-[width] duration-300"
          style={{ width: `calc((100% - 2.5rem) * ${actual / (total - 1)})` }}
        />
        {pasos.map((paso, i) => {
          const hecho = i < actual
          const activo = i === actual
          return (
            <li
              key={paso.titulo}
              aria-current={activo ? 'step' : undefined}
              className="relative z-10 flex flex-col items-center"
            >
              <span
                aria-hidden
                className={cn(
                  'flex size-10 items-center justify-center rounded-full text-sm font-bold sm:size-12',
                  activo && 'bg-acento text-on-acento shadow-raised',
                  hecho && 'bg-acento text-on-acento',
                  !activo && !hecho && 'bg-surface-container-highest text-on-surface-variant',
                )}
              >
                {hecho ? <Check className="size-5" /> : i + 1}
              </span>
              <span className="sr-only">
                Paso {i + 1}: {paso.titulo}
                {hecho ? ' (completado)' : activo ? ' (actual)' : ''}
              </span>
              <span
                aria-hidden
                className={cn(
                  'absolute top-full mt-2 hidden text-center text-xs font-bold whitespace-nowrap md:block',
                  activo ? 'text-primary' : 'text-on-surface-variant',
                )}
              >
                {paso.titulo}
              </span>
            </li>
          )
        })}
      </ol>
      <div aria-hidden className="hidden h-5 md:block" />
    </div>
  )
}
