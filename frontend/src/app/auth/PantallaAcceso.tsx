import { useRouterState } from '@tanstack/react-router'
import { useEffect, useRef, type ReactNode } from 'react'
import { Eyebrow } from '@/design-system'
import { PanelMarca } from './PanelMarca'

/** Encabezado de cada vista de acceso. El h1 recibe el foco al cambiar de vista (lo hace PantallaAcceso). */
export function TituloAcceso({ eyebrow, titulo, children }: { eyebrow: string; titulo: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <Eyebrow className="text-primary">{eyebrow}</Eyebrow>
      <h1 id="titulo-acceso" tabIndex={-1} className="font-display text-headline-lg font-bold text-on-surface focus:outline-none">
        {titulo}
      </h1>
      {children && <p className="text-on-surface-variant">{children}</p>}
    </div>
  )
}

/**
 * Pantalla B1: panel de marca a la izquierda y tarjeta de acceso a la derecha.
 * La usan /app/login/*.
 */
export function PantallaAcceso({ children }: { children: ReactNode }) {
  const ruta = useRouterState({ select: (s) => s.location.pathname })
  const primera = useRef(true)

  // Al cambiar de vista se lleva el foco al título para que el lector de pantalla anuncie la nueva vista.
  useEffect(() => {
    if (primera.current) {
      primera.current = false
      return
    }
    document.getElementById('titulo-acceso')?.focus()
  }, [ruta])

  return (
    <div className="min-h-dvh bg-surface">
      <main className="mx-auto grid w-full max-w-6xl items-center gap-10 px-4 py-8 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] lg:py-16">
        <PanelMarca />
        <div className="flex w-full flex-col gap-6 rounded-card border border-outline-variant/40 bg-surface-container-lowest p-6 shadow-raised sm:p-10">
          <div className="flex items-center justify-between pb-2 border-b border-outline-variant/30 lg:hidden">
            <div className="flex items-center gap-2">
              <img src="/logo-villadent-64.png" alt="" aria-hidden className="size-9 rounded-full object-contain" />
              <span className="font-display font-bold text-base text-primary">Villadent</span>
            </div>
            <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider">
              Acceso Clínico
            </span>
          </div>
          {children}
        </div>
      </main>
    </div>
  )
}
