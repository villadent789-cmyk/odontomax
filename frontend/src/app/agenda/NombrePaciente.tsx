import { Link, useLocation } from '@tanstack/react-router'
import { cn } from '@/design-system/cn'
import type { Cita } from './api-agenda'

interface Props {
  cita: Pick<Cita, 'pacienteId' | 'pacienteNombre'>
  className?: string
}

/**
 * Nombre del paciente de una cita: con un clic abre su ficha (con «← Volver» a la agenda o a la bandeja). Si la cita aún no
 * tiene paciente registrado (p. ej. una reserva web) se muestra como texto. El clic no abre el detalle de la cita.
 */
export function NombrePaciente({ cita, className }: Props) {
  const nombre = cita.pacienteNombre || 'Paciente sin nombre'
  // Sin paciente registrado no hay ficha a la que ir (y no se necesita el enrutador): se muestra solo el texto.
  if (!cita.pacienteId) return <span className={className}>{nombre}</span>
  return <EnlacePaciente id={cita.pacienteId} nombre={nombre} className={className} />
}

function EnlacePaciente({ id, nombre, className }: { id: string; nombre: string; className?: string }) {
  const origen = useLocation({ select: (l) => (l.pathname.endsWith('/bandeja') ? 'bandeja' : 'agenda') as 'agenda' | 'bandeja' })
  return (
    <Link
      to="/app/pacientes/$id"
      params={{ id }}
      search={{ desde: origen }}
      onClick={(e) => e.stopPropagation()}
      title="Abrir la ficha del paciente"
      className={cn('hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-focus', className)}
    >
      {nombre}
    </Link>
  )
}
