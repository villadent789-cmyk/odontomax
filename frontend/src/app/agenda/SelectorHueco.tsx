import { CalendarX } from 'lucide-react'
import { Alert, Button, Skeleton } from '@/design-system'
import { cn } from '@/design-system/cn'
import { hoyEnLima } from '@/shared/format'
import { useDisponibilidad, useHorarios, type Cita } from './api-agenda'
import { mensajeError } from './errores'
import { aMinutos, deMinutos, minutosAhoraEnLima } from './fechas'
import { avisoDiaSinTurnos } from './horarioDoctor'
import { PASO } from './rejilla'

interface Props {
  especialistaId: string
  fecha: string
  sedeId: string | null
  duracion: number
  valor: string
  onElegir: (hora: string) => void
  /** Cita que se reprograma: sus propias franjas cuentan como libres. */
  propia?: Cita
  error?: string
  idError?: string
}

/**
 * Horarios libres según GET /especialistas/{id}/disponibilidad (franjas de 30 min). Para duraciones mayores se exigen
 * franjas consecutivas libres; hoy se ocultan las horas que ya pasaron en Lima.
 */
export function SelectorHueco({ especialistaId, fecha, sedeId, duracion, valor, onElegir, propia, error, idError }: Props) {
  const disp = useDisponibilidad(especialistaId, fecha, sedeId)
  const horarios = useHorarios(especialistaId)

  if (disp.isPending) {
    return (
      <div aria-busy="true" className="flex flex-wrap gap-2">
        <span className="sr-only" role="status">
          Buscando horarios libres…
        </span>
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-10 w-20" />
        ))}
      </div>
    )
  }
  if (disp.isError) {
    return (
      <Alert tono="error" titulo="No se pudo consultar la disponibilidad">
        {mensajeError(disp.error)}{' '}
        <Button size="sm" variant="enlace" onClick={() => void disp.refetch()}>
          Reintentar
        </Button>
      </Alert>
    )
  }
  const d = disp.data
  if (!d.atiendeEseDia) {
    // Aviso claro con los días que sí atiende; si aún no llegó su horario se usa el mensaje del servidor.
    const aviso = avisoDiaSinTurnos(horarios.data, sedeId, fecha)
    return (
      <p role="status" className="flex items-center gap-2 rounded-panel bg-surface-container p-3 text-sm">
        <CalendarX aria-hidden className="size-5 shrink-0 text-on-surface-variant" />
        {aviso || d.mensaje || 'El especialista no atiende ese día.'}
      </p>
    )
  }

  const propias = new Set<number>()
  if (propia && propia.fecha === fecha && propia.doctorId === especialistaId) {
    const ini = aMinutos(propia.hora)
    for (let m = ini; m < ini + (propia.duracionMinutos || 30); m += PASO) propias.add(m)
  }
  const libres = new Map<number, boolean>()
  for (const s of d.slots ?? []) {
    const m = aMinutos(s.hora)
    libres.set(m, !!s.disponible || propias.has(m))
  }
  const pasos = Math.max(1, Math.ceil(duracion / PASO))
  const corte = fecha === hoyEnLima() ? minutosAhoraEnLima() : -1
  const huecos = [...libres.keys()]
    .sort((a, b) => a - b)
    .filter((m) => m > corte)
    .filter((m) => Array.from({ length: pasos }, (_, i) => libres.get(m + i * PASO)).every((v) => v === true))

  if (huecos.length === 0) {
    return (
      <p role="status" className="rounded-panel bg-surface-container p-3 text-sm">
        No quedan horarios libres de {duracion} min ese día. Prueba otra fecha o una duración menor.
      </p>
    )
  }

  const manana = huecos.filter((m) => m < 13 * 60)
  const tarde = huecos.filter((m) => m >= 13 * 60)

  const renderSlots = (lista: number[]) => (
    <div className="flex flex-wrap gap-2">
      {lista.map((m) => {
        const hora = deMinutos(m)
        const elegido = valor === hora
        return (
          <label
            key={m}
            className={cn(
              'inline-flex h-9 cursor-pointer items-center rounded-pill border px-3.5 text-xs font-bold transition-all has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
              elegido
                ? 'border-primary bg-acento text-on-acento shadow-sm'
                : 'border-outline-variant bg-surface-container-lowest text-on-surface hover:border-primary/60 hover:bg-primary-fixed/40',
            )}
          >
            <input
              type="radio"
              name={`hueco-${especialistaId}`}
              value={hora}
              checked={elegido}
              onChange={() => onElegir(hora)}
              className="sr-only"
            />
            {hora}
          </label>
        )
      })}
    </div>
  )

  return (
    <fieldset aria-describedby={error ? idError : undefined} aria-invalid={error ? true : undefined} className="space-y-3">
      <legend className="sr-only">Horario de la cita</legend>
      {manana.length > 0 && (
        <div className="space-y-1.5">
          <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider flex items-center gap-1.5">
            ☀️ Turno Mañana ({manana.length} libres)
          </span>
          {renderSlots(manana)}
        </div>
      )}
      {tarde.length > 0 && (
        <div className="space-y-1.5">
          <span className="text-xs font-bold text-on-surface-variant uppercase tracking-wider flex items-center gap-1.5">
            🌙 Turno Tarde ({tarde.length} libres)
          </span>
          {renderSlots(tarde)}
        </div>
      )}
    </fieldset>
  )
}
