import { Link, Outlet } from '@tanstack/react-router'
import { MotionConfig } from 'motion/react'
import { EmptyState, Toaster, buttonVariants } from '@/design-system'

export function RootLayout() {
  return (
    // reducedMotion="user": Motion respeta prefers-reduced-motion del sistema.
    <MotionConfig reducedMotion="user">
      <Outlet />
      <Toaster />
    </MotionConfig>
  )
}

export function NoEncontrada() {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-surface p-6">
      <EmptyState
        titulo="Página no encontrada"
        descripcion="La dirección que buscas no existe o fue movida."
        accion={
          <Link to="/" className={buttonVariants({ variant: 'primario' })}>
            Ir al inicio
          </Link>
        }
      />
    </main>
  )
}
