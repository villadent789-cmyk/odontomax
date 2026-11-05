import { Link } from '@tanstack/react-router'
import { Alert, buttonVariants } from '@/design-system'
import { puede, useSesion } from '@/shared/auth'

/** Banner preventivo: sin turno abierto no se pueden registrar cobros ni anticipos. */
export function AvisoCajaCerrada() {
  const sesion = useSesion()
  const puedeAbrir = puede(sesion?.usuario.rol, 'caja')
  return (
    <Alert tono="aviso" titulo="La caja de esta sede no está abierta hoy.">
      <p>Para poder registrar cobros o anticipos de los pacientes, abra el turno de caja.</p>
      {puedeAbrir && (
        <Link to="/app/caja" className={buttonVariants({ variant: 'secundario', size: 'sm', className: 'mt-2 w-fit' })}>
          Ir a Caja
        </Link>
      )}
    </Alert>
  )
}
