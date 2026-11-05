import { Armchair, Stethoscope } from 'lucide-react'
import { EstadoBadge } from '@/design-system'
import type { Cita } from './api-agenda'
import { horaCorta, horaFin } from './fechas'

interface Props {
  citas: Cita[]
  citaSeleccionada?: string
  onAbrir?: (cita: Cita) => void
}

/** Vista del día para celular: las citas como una lista de tarjetas por hora, en lugar de la cuadrícula por sillón. */
export function VistaDiaLista({ citas, citaSeleccionada, onAbrir }: Props) {
  const activas = citas.filter((c) => c.estado !== 'CANCELADA' && c.estado !== 'EXPIRADA').sort((a, b) => (a.hora ?? '').localeCompare(b.hora ?? ''))
  if (activas.length === 0) return null
  return (
    <ul aria-label="Citas del día" className="flex flex-col gap-2">
      {activas.map((cita) => (
        <li key={cita.id}>
          <button
            type="button"
            onClick={() => onAbrir?.(cita)}
            aria-current={citaSeleccionada === cita.id || undefined}
            className="flex w-full items-stretch gap-3 rounded-card border border-outline-variant/60 bg-surface-container-lowest p-3 text-left shadow-card active:bg-surface-container-low"
          >
            <span className="flex w-16 shrink-0 flex-col items-center justify-center rounded-control bg-acento/15 py-2">
              <span className="font-display text-lg font-bold text-primary">{horaCorta(cita.hora)}</span>
              <span className="text-xs text-on-surface-variant">{horaFin(cita.hora, cita.duracionMinutos)}</span>
            </span>
            <span className="flex min-w-0 flex-1 flex-col gap-1">
              <span className="flex flex-wrap items-center gap-2">
                <span className="font-bold text-on-surface">{cita.pacienteNombre || 'Paciente sin nombre'}</span>
                <EstadoBadge estado={cita.estado ?? ''} />
              </span>
              {cita.motivoConsulta && <span className="truncate text-sm text-on-surface-variant">{cita.motivoConsulta}</span>}
              <span className="flex flex-wrap gap-x-3 text-xs text-on-surface-variant">
                {cita.doctorNombre && (
                  <span className="flex items-center gap-1">
                    <Stethoscope aria-hidden className="size-3.5" />
                    {cita.doctorNombre}
                  </span>
                )}
                {cita.sillonNumero != null && (
                  <span className="flex items-center gap-1">
                    <Armchair aria-hidden className="size-3.5" />
                    Sillón {cita.sillonNumero}
                  </span>
                )}
              </span>
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}
