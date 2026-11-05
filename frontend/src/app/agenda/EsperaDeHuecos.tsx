import { Hourglass, MessageCircle } from 'lucide-react'
import { useState } from 'react'
import { Badge, Button, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, Field, Input } from '@/design-system'
import { textoDeError } from '@/shared/http'
import { Seccion } from './Seccion'
import { BuscadorPaciente } from './BuscadorPaciente'
import { useAgregarEspera, useCambiarEstadoEspera, useEsperaDeHuecos } from './api-espera-hueco'
import { useEspecialistas, type ItemTareaBandeja, type Paciente } from './api-agenda'

interface Props {
  sedeId: string
  /** Huecos libres que la bandeja ya emparejó con alguien de la lista (tipo HUECO_DISPONIBLE). */
  huecos: ItemTareaBandeja[]
}

/** Bandeja: a quién ofrecerle los horarios que se liberan y quiénes esperan un hueco. */
export function EsperaDeHuecos({ sedeId, huecos }: Props) {
  const espera = useEsperaDeHuecos(sedeId)
  const especialistas = useEspecialistas(sedeId)
  const agregar = useAgregarEspera()
  const cambiar = useCambiarEstadoEspera()
  const [abierto, setAbierto] = useState(false)
  const [paciente, setPaciente] = useState<Paciente | null>(null)
  const [especialistaId, setEspecialistaId] = useState('')
  const [motivo, setMotivo] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function guardar(evento: React.FormEvent) {
    evento.preventDefault()
    setError(null)
    if (!paciente?.id) {
      setError('Elige al paciente.')
      return
    }
    try {
      await agregar.mutateAsync({ sedeId, pacienteId: paciente.id, especialistaId: especialistaId || undefined, motivo })
      setAbierto(false)
      setPaciente(null)
      setEspecialistaId('')
      setMotivo('')
    } catch (e) {
      setError(textoDeError(e, 'No se pudo agregar a la lista de espera.'))
    }
  }

  const lista = espera.data ?? []
  return (
    <Seccion
      id="sec-espera-huecos"
      titulo="Lista de espera"
      icono={<Hourglass />}
      tono="accion"
      contador={huecos.length > 0 ? `${huecos.length} ${huecos.length === 1 ? 'hueco para ofrecer' : 'huecos para ofrecer'}` : `${lista.length} en espera`}
      descripcion="Pacientes que esperan un horario. Si se libera uno, te sugerimos a quién ofrecérselo."
      acciones={
        <Button size="sm" variant="accion" onClick={() => setAbierto(true)}>
          Agregar paciente
        </Button>
      }
    >
      {huecos.length > 0 && (
        <ul className="flex flex-col gap-3">
          {huecos.map((h) => (
            <li key={h.id} className="flex flex-wrap items-center justify-between gap-2 rounded-panel border border-outline-variant/70 bg-surface-container-low p-4">
              <div>
                <h3 className="font-bold text-on-surface">{h.pacienteNombre}</h3>
                <p className="text-xs text-on-surface-variant">
                  {h.motivoConsulta}
                  {h.doctorNombre ? ` · ${h.doctorNombre}` : ''}
                </p>
              </div>
              {h.enlaceWhatsapp && (
                <a
                  href={h.enlaceWhatsapp}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 rounded-full bg-[#16a34a] px-3.5 py-1.5 text-xs font-bold text-white hover:bg-[#15803d]"
                >
                  <MessageCircle className="size-3.5" aria-hidden />
                  Ofrecer por WhatsApp
                </a>
              )}
            </li>
          ))}
        </ul>
      )}

      {lista.length === 0 ? (
        <p className="text-sm text-on-surface-variant">Nadie espera un hueco por ahora.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-outline-variant/50">
          {lista.map((e) => (
            <li key={e.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
              <div className="flex flex-col gap-0.5">
                <span className="text-sm font-semibold text-on-surface">{e.pacienteNombre}</span>
                <span className="text-xs text-on-surface-variant">{e.motivo}</span>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Badge tono={e.estado === 'OFRECIDA' ? 'accion' : 'neutro'}>{e.estado === 'OFRECIDA' ? 'Ofrecido' : 'En espera'}</Badge>
                {e.estado === 'ACTIVA' && (
                  <Button size="sm" variant="secundario" onClick={() => void cambiar.mutateAsync({ id: e.id!, estado: 'OFRECIDA' })}>
                    Marcar ofrecido
                  </Button>
                )}
                <Button size="sm" variant="secundario" onClick={() => void cambiar.mutateAsync({ id: e.id!, estado: 'AGENDADA' })}>
                  Ya agendó
                </Button>
                <Button size="sm" variant="fantasma" onClick={() => void cambiar.mutateAsync({ id: e.id!, estado: 'CANCELADA' })}>
                  Quitar
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={abierto} onOpenChange={setAbierto}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Agregar a la lista de espera</DialogTitle>
            <DialogDescription>Cuando se libere un horario compatible, aparecerá aquí para ofrecérselo.</DialogDescription>
          </DialogHeader>
          <form onSubmit={guardar} className="flex flex-col gap-4">
            <BuscadorPaciente sedeId={sedeId} seleccionado={paciente} onSeleccionar={setPaciente} />
            <Field label="Doctor preferido (opcional)">
              <select
                aria-label="Doctor preferido"
                value={especialistaId}
                onChange={(e) => setEspecialistaId(e.target.value)}
                className="w-full rounded-control border border-outline bg-surface-container-lowest px-3 py-2 text-sm text-on-surface"
              >
                <option value="">Cualquiera</option>
                {(especialistas.data ?? []).map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.nombre}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="¿Para qué espera? *">
              <Input value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={200} placeholder="Ej. limpieza dental" />
            </Field>
            {error && (
              <p role="alert" className="text-sm text-error">
                {error}
              </p>
            )}
            <DialogFooter>
              <Button type="button" variant="secundario" onClick={() => setAbierto(false)}>
                Cancelar
              </Button>
              <Button type="submit" variant="accion" disabled={agregar.isPending}>
                {agregar.isPending ? 'Guardando…' : 'Agregar'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </Seccion>
  )
}
