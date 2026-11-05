import { Clock, Plus } from 'lucide-react'
import type { ReactNode } from 'react'
import { Avatar, Badge } from '@/design-system'
import { cn } from '@/design-system/cn'
import type { Cita } from './api-agenda'
import { deMinutos } from './fechas'
import { PASO, citaQueContinua, citasQueEmpiezan, franjas } from './rejilla'
import { TarjetaCita } from './TarjetaCita'

export type EstadoFranja = boolean | 'PASADO'

export interface ColumnaDia {
  id: string
  titulo: string
  subtitulo?: string
  /** Muestra avatar con iniciales (columnas de especialista). */
  avatar?: boolean
  citas: Cita[]
  /**
   * Estado de cada franja según GET /especialistas/{id}/disponibilidad: true libre, false ocupada, 'PASADO' hora pasada; una franja que no
   * figura está fuera del horario del especialista. `null` = sin datos de disponibilidad (columnas de sillón).
   */
  franjasLibres: Map<number, EstadoFranja> | null
}

interface Props {
  columnas: ColumnaDia[]
  inicio: number
  fin: number
  /** Minutos de "ahora" si la fecha mostrada es hoy (línea de hora actual). */
  ahora: number | null
  citaSeleccionada?: string
  onAbrir?: (cita: Cita) => void
  /** Si se indica, las franjas libres ofrecen "Nueva cita" (B5). */
  onCrear?: (columna: ColumnaDia, hora: string) => void
  leyenda: string
}

/** B3 · Vista diaria: filas de 30 min y una columna por especialista o por sillón. */
export function VistaDia({ columnas, inicio, fin, ahora, citaSeleccionada, onAbrir, onCrear, leyenda }: Props) {
  const filas = franjas(inicio, fin)
  return (
    <div className="overflow-x-auto rounded-card bg-surface-container-lowest shadow-card">
      <table className="sin-apilar w-full min-w-[40rem] table-fixed border-separate border-spacing-1.5 p-2">
        <caption className="sr-only">{leyenda}</caption>
        <thead>
          <tr>
            <th scope="col" className="w-24 px-2 py-3 text-left text-xs font-bold tracking-etiqueta text-on-surface-variant uppercase">
              <span className="flex items-center gap-1.5">
                <Clock aria-hidden className="size-4" />
                Hora
              </span>
            </th>
            {columnas.map((col) => (
              <th key={col.id} scope="col" className="rounded-panel bg-surface-container-low px-3 py-2 text-left align-top">
                <span className="flex items-center gap-2">
                  {col.avatar && <Avatar nombre={col.titulo} tamano="sm" />}
                  <span className="flex min-w-0 flex-col">
                    <span className="truncate font-bold text-on-surface">{col.titulo}</span>
                    {col.subtitulo && <span className="truncate text-xs font-normal text-on-surface-variant">{col.subtitulo}</span>}
                  </span>
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((franja) => {
            const esAhora = ahora !== null && ahora >= franja && ahora < franja + PASO
            return (
              <tr key={franja} className={cn(esAhora && '[&>*]:border-t-2 [&>*]:border-error')} aria-current={esAhora ? 'time' : undefined}>
                <th scope="row" className="px-2 py-2 text-left align-top text-sm font-bold text-on-surface">
                  {deMinutos(franja)}
                  {esAhora && (
                    <Badge tono="peligro" className="mt-1 px-2 py-0.5 text-xs">
                      Ahora {deMinutos(ahora)}
                    </Badge>
                  )}
                </th>
                {columnas.map((col) => (
                  <td key={col.id} className="h-20 align-top">
                    <Celda
                      columna={col}
                      franja={franja}
                      citaSeleccionada={citaSeleccionada}
                      onAbrir={onAbrir}
                      onCrear={ahora !== null && franja <= ahora ? undefined : onCrear}
                    />
                  </td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function Celda({
  columna,
  franja,
  citaSeleccionada,
  onAbrir,
  onCrear,
}: {
  columna: ColumnaDia
  franja: number
  citaSeleccionada?: string
  onAbrir?: (cita: Cita) => void
  onCrear?: (columna: ColumnaDia, hora: string) => void
}) {
  const empiezan = citasQueEmpiezan(columna.citas, franja)
  if (empiezan.length > 0) {
    return (
      <div className="flex flex-col gap-1">
        {empiezan.map((c) => (
          <TarjetaCita key={c.id} cita={c} onAbrir={onAbrir} seleccionada={c.id === citaSeleccionada} />
        ))}
      </div>
    )
  }
  const continua = citaQueContinua(columna.citas, franja)
  if (continua) {
    return (
      <Marco className="bg-surface-container text-on-surface-variant">
        Continúa: {continua.pacienteNombre || 'cita'}
      </Marco>
    )
  }
  const libre = columna.franjasLibres?.get(franja)
  if (columna.franjasLibres === null) {
    return <span className="sr-only">Sin citas</span>
  }
  if (libre === undefined) {
    return <Marco className="bg-surface-container-high/60 text-on-surface-variant">Fuera de horario</Marco>
  }
  if (libre === 'PASADO') {
    return <Marco className="bg-surface-container-low text-on-surface-variant/50">Pasado</Marco>
  }
  if (!libre) {
    return <Marco className="bg-surface-container text-on-surface-variant">Ocupado</Marco>
  }
  const hora = deMinutos(franja)
  if (onCrear) {
    return (
      <button
        type="button"
        onClick={() => onCrear(columna, hora)}
        aria-label={`Nueva cita a las ${hora} con ${columna.titulo}`}
        className="flex size-full min-h-16 items-center justify-center rounded-control border border-dashed border-outline-variant text-on-surface-variant hover:bg-primary-fixed/40 hover:text-primary focus-visible:outline-2 focus-visible:outline-focus"
      >
        <Plus aria-hidden className="size-5" />
      </button>
    )
  }
  return <Marco className="border border-dashed border-outline-variant text-on-surface-variant">Libre</Marco>
}

function Marco({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <div className={cn('flex size-full min-h-16 items-center justify-center rounded-control px-2 text-center text-xs', className)}>
      {children}
    </div>
  )
}
