import { Link } from '@tanstack/react-router'
import { LockKeyhole } from 'lucide-react'
import { ErrorState, buttonVariants } from '@/design-system'

/** Pantalla para 403 (del backend) o rutas que el rol no puede ver. */
export function SinPermisoPage() {
  return (
    <div className="mx-auto max-w-3xl rounded-card bg-surface-container-lowest shadow-card">
      <ErrorState
        icono={<LockKeyhole />}
        titulo="Sin permiso"
        descripcion="Tu rol no tiene acceso a esta sección. Si crees que es un error, pide a la administración de tu sede que revise tus permisos."
        accion={
          <Link to="/app" className={buttonVariants({ variant: 'primario' })}>
            Volver al inicio
          </Link>
        }
      />
    </div>
  )
}
