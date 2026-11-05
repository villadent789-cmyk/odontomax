import { Link } from '@tanstack/react-router'
import { Construction } from 'lucide-react'
import { EmptyState, buttonVariants } from '@/design-system'

/** Página provisional de un módulo cuyo diseño o API aún no se implementa. */
export function ModuloPendiente({ titulo }: { titulo: string }) {
  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <h1 className="font-display text-headline-lg font-bold">{titulo}</h1>
      <div className="rounded-card bg-surface-container-lowest shadow-card">
        <EmptyState
          icono={<Construction />}
          titulo="Módulo en construcción"
          descripcion="Esta sección se implementará en los próximos pasos del frontend."
          accion={
            <Link to="/app" className={buttonVariants({ variant: 'secundario' })}>
              Volver al inicio
            </Link>
          }
        />
      </div>
    </div>
  )
}
