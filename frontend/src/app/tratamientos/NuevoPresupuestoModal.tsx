import { Plus, Trash2 } from 'lucide-react'
import { esSoloOdontologo } from '@/shared/auth/sesion'
import { useState } from 'react'
import { Alert, Button, Dialog, DialogContent, DialogDescription, DialogTitle, Field, Input } from '@/design-system'
import { useEspecialistas } from '../agenda/api-agenda'
import { mensajeError } from '@/shared/errores'
import { formatSoles } from '@/shared/format'
import { useSesion } from '@/shared/auth'
import { useCrearPresupuesto, type Gravedad } from './api-presupuestos'
import { useServiciosCatalogo, type ServicioCatalogo } from './api-tratamientos'

interface Linea {
  clave: number
  servicioId: string
  nombre: string
  piezaFdi: string
  gravedad: '' | Gravedad
  cantidad: string
  costo: string
}

function costoDe(s: ServicioCatalogo | undefined, g: '' | Gravedad): number {
  if (!s) return 0
  const porGravedad = g === 'LEVE' ? s.precioLeve : g === 'MODERADO' ? s.precioModerado : g === 'SEVERO' ? s.precioSevero : undefined
  return porGravedad ?? s.precio ?? 0
}

let contador = 0
const lineaVacia = (): Linea => ({ clave: ++contador, servicioId: '', nombre: '', piezaFdi: '', gravedad: '', cantidad: '1', costo: '' })

/**
 * Presupuesto de lo que se puede hacer por el paciente: varias líneas, cada una con su tratamiento, pieza, gravedad,
 * cantidad de piezas y costo por pieza (se rellena con el catálogo y se puede ajustar).
 */
export function NuevoPresupuestoModal({
  abierto,
  alCerrar,
  pacienteId,
  pacienteNombre,
  sedeId,
}: {
  abierto: boolean
  alCerrar: () => void
  pacienteId: string
  pacienteNombre?: string
  sedeId: string
}) {
  const sesion = useSesion()
  const { data: catalogo = [] } = useServiciosCatalogo(sedeId, abierto)
  const { data: especialistas = [] } = useEspecialistas(sedeId, abierto)
  const crear = useCrearPresupuesto(pacienteId)
  const [lineas, setLineas] = useState<Linea[]>(() => [lineaVacia()])
  const [motivo, setMotivo] = useState('')
  const [notas, setNotas] = useState('')
  const [doctor, setDoctor] = useState('')
  const [error, setError] = useState<string | null>(null)

  const esDoctor = esSoloOdontologo(sesion?.usuario)
  const miEspecialista = esDoctor ? especialistas.find((e) => e.usuarioId === sesion?.usuario.id) : undefined
  const doctorElegido = esDoctor ? (miEspecialista?.id ?? '') : doctor
  const total = lineas.reduce((suma, l) => suma + (Number(l.cantidad) || 0) * (Number(l.costo) || 0), 0)

  const cambiar = (clave: number, parcial: Partial<Linea>) =>
    setLineas((ls) => ls.map((l) => (l.clave === clave ? { ...l, ...parcial } : l)))

  const elegirServicio = (clave: number, id: string, gravedad: '' | Gravedad) => {
    const servicio = catalogo.find((s) => s.id === id)
    cambiar(clave, { servicioId: id, nombre: servicio?.nombre ?? '', gravedad, costo: servicio ? costoDe(servicio, gravedad).toFixed(2) : '' })
  }

  /** Otro caso del mismo tratamiento (p. ej. una pieza leve y otra severa): copia la línea para elegir otra gravedad. */
  const otroCaso = (origen: Linea) =>
    setLineas((ls) => {
      const servicio = catalogo.find((s) => s.id === origen.servicioId)
      const nueva: Linea = { ...lineaVacia(), servicioId: origen.servicioId, nombre: origen.nombre, costo: servicio ? costoDe(servicio, '').toFixed(2) : origen.costo }
      const i = ls.findIndex((x) => x.clave === origen.clave)
      return [...ls.slice(0, i + 1), nueva, ...ls.slice(i + 1)]
    })

  const guardar = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    const validas = lineas.filter((l) => l.nombre.trim())
    if (validas.length === 0) return setError('Agrega al menos un tratamiento al presupuesto.')
    if (validas.some((l) => !(Number(l.cantidad) >= 1) || !Number.isInteger(Number(l.cantidad)) || !(Number(l.costo) >= 0) || l.costo.trim() === '')) {
      return setError('Cada tratamiento necesita su cantidad de piezas y su costo por pieza.')
    }
    try {
      await crear.mutateAsync({
        pacienteId,
        sedeId,
        especialistaId: doctorElegido || undefined,
        motivo: motivo.trim() || undefined,
        notas: notas.trim() || undefined,
        items: validas.map((l) => ({
          procedimientoId: l.servicioId || undefined,
          nombre: l.nombre.trim(),
          piezaFdi: l.piezaFdi.trim() ? Number(l.piezaFdi) : undefined,
          gravedad: l.gravedad || undefined,
          cantidad: Number(l.cantidad),
          precioUnitario: Number(l.costo),
        })),
      })
      setLineas([lineaVacia()])
      setMotivo('')
      setNotas('')
      alCerrar()
    } catch (err) {
      setError(mensajeError(err))
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(v) => !v && alCerrar()}>
      <DialogContent className="max-w-3xl max-h-[92vh] overflow-y-auto">
        <DialogTitle className="font-display text-lg font-bold text-primary">Nuevo presupuesto</DialogTitle>
        <DialogDescription>
          Lo que se puede hacer por {pacienteNombre ?? 'el paciente'}. Después podrá aceptar todo o solo una parte.
        </DialogDescription>
        <form onSubmit={guardar} noValidate className="flex flex-col gap-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Field label="Motivo de la consulta">
              <Input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej. Vino por una curación, tiene más piezas por tratar" />
            </Field>
            <Field label="Doctor">
              <select aria-label="Doctor" value={doctorElegido} onChange={(e) => setDoctor(e.target.value)} disabled={esDoctor} className="w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm">
                {!esDoctor && <option value="">Elige un doctor</option>}
                {(esDoctor ? (miEspecialista ? [miEspecialista] : []) : especialistas).map((e) => (
                  <option key={e.id} value={e.id}>{e.nombre}</option>
                ))}
              </select>
            </Field>
          </div>

          <ul className="flex flex-col gap-3">
            {lineas.map((l, i) => {
              const subtotal = (Number(l.cantidad) || 0) * (Number(l.costo) || 0)
              return (
                <li key={l.clave} className="flex flex-col gap-3 rounded-panel border border-outline-variant/50 p-3">
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-bold text-primary">Tratamiento {i + 1}</p>
                    <button
                      type="button"
                      aria-label={`Quitar tratamiento ${i + 1}`}
                      onClick={() => setLineas((ls) => (ls.length > 1 ? ls.filter((x) => x.clave !== l.clave) : ls))}
                      className="inline-flex size-9 items-center justify-center rounded-control text-on-surface-variant hover:bg-surface-container"
                    >
                      <Trash2 aria-hidden className="size-4" />
                    </button>
                  </div>
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <Field label="Tratamiento del catálogo">
                      <select
                        aria-label={`Tratamiento ${i + 1}`}
                        value={l.servicioId}
                        onChange={(e) => elegirServicio(l.clave, e.target.value, l.gravedad)}
                        className="w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm"
                      >
                        <option value="">Elige del catálogo o escribe el nombre</option>
                        {catalogo.map((s) => (
                          <option key={s.id} value={s.id}>{s.nombre}</option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Nombre en el presupuesto">
                      <Input aria-label={`Nombre ${i + 1}`} value={l.nombre} onChange={(e) => cambiar(l.clave, { nombre: e.target.value })} />
                    </Field>
                  </div>
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <Field label="Pieza dental">
                      <Input aria-label={`Pieza ${i + 1}`} placeholder="Ej. 16" inputMode="numeric" value={l.piezaFdi} onChange={(e) => cambiar(l.clave, { piezaFdi: e.target.value })} />
                    </Field>
                    <Field label="Gravedad">
                      <select
                        aria-label={`Gravedad ${i + 1}`}
                        value={l.gravedad}
                        onChange={(e) => elegirServicio(l.clave, l.servicioId, e.target.value as '' | Gravedad)}
                        className="w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm"
                      >
                        <option value="">No aplica</option>
                        <option value="LEVE">Leve</option>
                        <option value="MODERADO">Moderado</option>
                        <option value="SEVERO">Severo</option>
                      </select>
                    </Field>
                    <Field label="Piezas o unidades">
                      <Input aria-label={`Piezas ${i + 1}`} type="number" min={1} step={1} inputMode="numeric" value={l.cantidad} onChange={(e) => cambiar(l.clave, { cantidad: e.target.value })} />
                    </Field>
                    <Field label="Costo por pieza (S/)">
                      <Input aria-label={`Costo por pieza ${i + 1}`} type="number" min={0} step="0.01" inputMode="decimal" value={l.costo} onChange={(e) => cambiar(l.clave, { costo: e.target.value })} />
                    </Field>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    {l.servicioId ? (
                      <Button type="button" variant="secundario" size="sm" onClick={() => otroCaso(l)} aria-label={`Otro caso del tratamiento ${i + 1}`}>
                        <Plus aria-hidden />
                        Otro caso de este tratamiento
                      </Button>
                    ) : <span />}
                    <p className="text-sm text-on-surface-variant">
                      Subtotal: <strong className="text-on-surface">{formatSoles(subtotal)}</strong>
                    </p>
                  </div>
                </li>
              )
            })}
          </ul>
          <Button type="button" variant="secundario" size="sm" onClick={() => setLineas((ls) => [...ls, lineaVacia()])} className="self-start">
            <Plus aria-hidden />
            Agregar tratamiento
          </Button>

          <Field label="Notas (opcional)">
            <Input value={notas} onChange={(e) => setNotas(e.target.value)} />
          </Field>
          <p className="text-right text-sm font-bold text-on-surface">Total del presupuesto: {formatSoles(total)}</p>
          {error && <Alert tono="error" titulo={error} />}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secundario" onClick={alCerrar} disabled={crear.isPending}>Cancelar</Button>
            <Button type="submit" cargando={crear.isPending}>Guardar presupuesto</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
