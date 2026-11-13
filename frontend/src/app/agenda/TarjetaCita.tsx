import { AlertTriangle, Armchair, Globe } from 'lucide-react'
import { NombrePaciente } from './NombrePaciente'
import { EstadoBadge, definicionEstado } from '@/design-system'
import { cn } from '@/design-system/cn'
import type { Cita } from './api-agenda'
import { horaCorta, horaFin, minutosEntreInstantes } from './fechas'

/** Fondo de la tarjeta según el tono del estado (guía A1: el color nunca es la única señal, siempre va el texto). */
const FONDO: Record<string, string> = {
  primario: 'bg-primary-fixed/35 border-primary/20 border-l-4 border-l-primary',
  accion: 'bg-secondary-fixed/45 border-secondary/20 border-l-4 border-l-secondary',
  villamax: 'bg-tertiary-fixed/45 border-tertiary/25 border-l-4 border-l-tertiary',
  peligro: 'bg-error-container/45 border-error/20 border-l-4 border-l-error',
  neutro: 'bg-surface-container border-outline-variant/50 border-l-4 border-l-outline',
  claro: 'bg-surface-container-lowest border-outline-variant/50 border-l-4 border-l-primary',
}

interface Props {
  cita: Cita
  compacta?: boolean
  /** Si se indica, la tarjeta es un botón que abre el detalle (B4). */
  onAbrir?: (cita: Cita) => void
  seleccionada?: boolean
}

/** Tarjeta de cita de la agenda (B3). */
export function TarjetaCita({ cita, compacta = false, onAbrir, seleccionada = false }: Props) {
  const { tono } = definicionEstado(cita.estado ?? '')
  const paciente = cita.pacienteNombre || 'Paciente sin nombre'
  const tachada = cita.estado === 'CANCELADA'
  const esperaMinutos =
    cita.llegadaEn && cita.estado === 'EN_SALA' ? minutosEntreInstantes(cita.llegadaEn) : null
  const atencionMinutos =
    cita.inicioAtencionEn && cita.finAtencionEn
      ? minutosEntreInstantes(cita.inicioAtencionEn, cita.finAtencionEn)
      : null
  const etiqueta = `${horaCorta(cita.hora)} a ${horaFin(cita.hora, cita.duracionMinutos)}, ${paciente}, ${definicionEstado(cita.estado ?? '').texto}`
  const contenido = (
    <>
      <div className="flex flex-wrap items-center justify-between gap-1">
        <EstadoBadge estado={cita.estado ?? ''} className="px-2 py-0.5 text-xs" />
        {!compacta && cita.sillonNumero != null && (
          <span className="flex items-center gap-1 text-xs font-bold text-on-surface-variant">
            <Armchair aria-hidden className="size-3" />
            Sillón {cita.sillonNumero}
          </span>
        )}
        {cita.estado === 'PENDIENTE_WEB' && <Globe aria-hidden className="size-3.5 text-tertiary" />}
      </div>
      <p className={cn('truncate font-bold text-sm text-on-surface', tachada && 'line-through')}>
        <NombrePaciente cita={cita} />
      </p>
      {!compacta && cita.motivoConsulta && <p className="truncate text-xs text-on-surface-variant">{cita.motivoConsulta}</p>}
      <p className="text-xs font-medium text-on-surface-variant">
        {horaCorta(cita.hora)} – {horaFin(cita.hora, cita.duracionMinutos)}
        {compacta && cita.doctorNombre ? ` · ${cita.doctorNombre}` : ''}
      </p>
      {(cita.pacienteInasistencias ?? 0) >= 2 && (
        <p className="flex items-center gap-1 text-xs font-semibold text-error">
          <AlertTriangle aria-hidden className="size-3" />
          {cita.pacienteInasistencias} inasistencias
        </p>
      )}
      {esperaMinutos !== null && <p className="text-xs font-semibold text-on-surface-variant">Espera {esperaMinutos} min</p>}
      {atencionMinutos !== null && (
        <p className="text-xs font-semibold text-on-surface-variant">
          Atendida en {atencionMinutos} min · prevista {cita.duracionMinutos ?? 30}
        </p>
      )}
    </>
  )
  const clases = cn(
    'flex w-full min-w-0 flex-col gap-1 rounded-xl border p-2 text-left transition-all',
    FONDO[tono ?? 'neutro'],
    seleccionada && 'ring-2 ring-primary ring-offset-1',
  )
  if (!onAbrir) {
    return (
      <div className={clases} data-cita={cita.id}>
        {contenido}
      </div>
    )
  }
  return (
    // Es un div con rol de botón (y no un <button>) porque dentro lleva el enlace a la ficha del paciente.
    <div
      role="button"
      tabIndex={0}
      className={cn(clases, 'cursor-pointer transition-shadow hover:shadow-card focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus')}
      onClick={() => onAbrir(cita)}
      onKeyDown={(e) => {
        if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault()
          onAbrir(cita)
        }
      }}
      aria-label={`Ver cita: ${etiqueta}`}
      data-cita={cita.id}
    >
      {contenido}
    </div>
  )
}
