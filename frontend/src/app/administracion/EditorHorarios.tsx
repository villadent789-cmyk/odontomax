import { Plus, Trash2 } from 'lucide-react'
import { Button } from '@/design-system'
import { DIAS, nuevaClave, type FilaHorario } from './horarios'

export interface OpcionSede {
  id: string
  nombre: string
  sillones?: number
}

interface Props {
  filas: FilaHorario[]
  onChange: (filas: FilaHorario[]) => void
  sedes: readonly OpcionSede[]
  /** Sede que se propone al agregar un turno nuevo. */
  sedePorDefecto?: string
}

const CONTROL =
  'w-full rounded-control border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'

/** Editor del horario semanal: un turno = sede + día + hora de inicio y de fin (un turno por sede y día). */
export function EditorHorarios({ filas, onChange, sedes, sedePorDefecto }: Props) {
  function cambiar(clave: string, campo: keyof Omit<FilaHorario, 'clave'>, valor: string | number) {
    onChange(filas.map((f) => (f.clave === clave ? { ...f, [campo]: valor } : f)))
  }

  function agregar() {
    onChange([
      ...filas,
      {
        clave: nuevaClave(),
        sedeId: sedePorDefecto ?? sedes[0]?.id ?? '',
        diaSemana: siguienteDia(filas),
        horaInicio: '09:00',
        horaFin: '20:00',
      },
    ])
  }

  return (
    <div className="flex flex-col gap-3">
      {filas.length === 0 ? (
        <p className="rounded-panel bg-surface-container-low p-4 text-sm text-on-surface-variant">
          Sin turnos todavía. Agrega al menos un turno para que el doctor aparezca con horario en la agenda.
        </p>
      ) : (
        <ul className="flex flex-col gap-2">
          {filas.map((f, i) => (
            <li
              key={f.clave}
              className="grid grid-cols-2 items-end gap-2 rounded-panel bg-surface-container-low p-3 sm:grid-cols-[1.4fr_1fr_0.8fr_0.8fr_auto]"
            >
              <label className="col-span-2 flex flex-col gap-1 text-xs font-bold text-on-surface-variant sm:col-span-1">
                Sede
                <select
                  aria-label={`Sede del turno ${i + 1}`}
                  className={CONTROL}
                  value={f.sedeId}
                  onChange={(e) => cambiar(f.clave, 'sedeId', e.target.value)}
                >
                  {sedes.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
                Día
                <select
                  aria-label={`Día del turno ${i + 1}`}
                  className={CONTROL}
                  value={f.diaSemana}
                  onChange={(e) => cambiar(f.clave, 'diaSemana', Number(e.target.value))}
                >
                  {DIAS.map((d) => (
                    <option key={d.valor} value={d.valor}>
                      {d.nombre}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
                Desde
                <input
                  type="time"
                  aria-label={`Hora de inicio del turno ${i + 1}`}
                  className={CONTROL}
                  value={f.horaInicio}
                  onChange={(e) => cambiar(f.clave, 'horaInicio', e.target.value)}
                />
              </label>
              <label className="flex flex-col gap-1 text-xs font-bold text-on-surface-variant">
                Hasta
                <input
                  type="time"
                  aria-label={`Hora de fin del turno ${i + 1}`}
                  className={CONTROL}
                  value={f.horaFin}
                  onChange={(e) => cambiar(f.clave, 'horaFin', e.target.value)}
                />
              </label>
              <Button
                type="button"
                variant="secundario"
                size="sm"
                aria-label={`Quitar el turno ${i + 1}`}
                onClick={() => onChange(filas.filter((x) => x.clave !== f.clave))}
              >
                <Trash2 aria-hidden />
              </Button>
            </li>
          ))}
        </ul>
      )}
      <div>
        <Button type="button" variant="secundario" size="sm" onClick={agregar} disabled={sedes.length === 0}>
          <Plus aria-hidden />
          Agregar turno
        </Button>
      </div>
    </div>
  )
}

/** Propone el primer día (lunes a domingo) aún sin turno en la última sede usada. */
function siguienteDia(filas: readonly FilaHorario[]): number {
  const ultima = filas[filas.length - 1]
  if (!ultima) return 1
  const usados = new Set(filas.filter((f) => f.sedeId === ultima.sedeId).map((f) => f.diaSemana))
  return DIAS.find((d) => !usados.has(d.valor))?.valor ?? 1
}
