import { Clock } from 'lucide-react'
import { cn } from '@/design-system/cn'
import { formatFecha } from '@/shared/format'
import type { Cita, Horario } from './api-agenda'
import { deMinutos, diaSemanaIso, nombreDiaCorto } from './fechas'
import { citasQueEmpiezan, dentroDeHorario, franjas, horariosDelDia } from './rejilla'
import { TarjetaCita } from './TarjetaCita'

interface Props {
  dias: string[]
  hoy: string
  citas: Cita[]
  inicio: number
  fin: number
  /** Horarios del especialista filtrado (para sombrear lo que está fuera de horario); null = todos. */
  horarios: Horario[] | null
  sedeId: string | null
  citaSeleccionada?: string
  onAbrir?: (cita: Cita) => void
  onIrADia: (fecha: string) => void
  leyenda: string
}

/** B3 · Vista semanal (lunes a domingo): GET /citas?desde&hasta. */
export function VistaSemana({ dias, hoy, citas, inicio, fin, horarios, sedeId, citaSeleccionada, onAbrir, onIrADia, leyenda }: Props) {
  const filas = franjas(inicio, fin)
  return (
    <div className="overflow-x-auto rounded-card bg-surface-container-lowest shadow-card">
      <table className="sin-apilar w-full min-w-[56rem] table-fixed border-separate border-spacing-1 p-2">
        <caption className="sr-only">{leyenda}</caption>
        <thead>
          <tr>
            <th scope="col" className="w-20 px-2 py-3 text-left text-xs font-bold tracking-etiqueta text-on-surface-variant uppercase">
              <span className="flex items-center gap-1.5">
                <Clock aria-hidden className="size-4" />
                Hora
              </span>
            </th>
            {dias.map((dia) => {
              const n = citas.filter((c) => c.fecha === dia && c.estado !== 'CANCELADA').length
              return (
                <th key={dia} scope="col" className="p-0">
                  <button
                    type="button"
                    onClick={() => onIrADia(dia)}
                    aria-label={`Ver el día ${nombreDiaCorto(dia)} ${formatFecha(dia)} (${n} citas)`}
                    className={cn(
                      'flex w-full flex-col items-start rounded-panel px-3 py-2 text-left hover:bg-surface-container-high focus-visible:outline-2 focus-visible:outline-focus',
                      dia === hoy ? 'bg-acento text-on-acento hover:brightness-105' : 'bg-surface-container-low',
                    )}
                  >
                    <span className="font-bold">
                      {nombreDiaCorto(dia)} {formatFecha(dia).slice(0, 5)}
                    </span>
                    <span className="text-xs font-normal">{n} citas</span>
                  </button>
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody>
          {filas.map((franja) => (
            <tr key={franja}>
              <th scope="row" className="px-2 py-1 text-left align-top text-sm font-bold">
                {deMinutos(franja)}
              </th>
              {dias.map((dia) => {
                const delDia = citas.filter((c) => c.fecha === dia)
                const empiezan = citasQueEmpiezan(delDia, franja)
                const fuera = horarios !== null && !dentroDeHorario(horariosDelDia(horarios, diaSemanaIso(dia), sedeId), franja)
                return (
                  <td key={dia} className={cn('h-14 rounded-control align-top', fuera && 'bg-surface-container-high/60')}>
                    {fuera && empiezan.length === 0 && <span className="sr-only">Fuera de horario</span>}
                    <div className="flex flex-col gap-1">
                      {empiezan.map((c) => (
                        <TarjetaCita key={c.id} cita={c} compacta onAbrir={onAbrir} seleccionada={c.id === citaSeleccionada} />
                      ))}
                    </div>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
