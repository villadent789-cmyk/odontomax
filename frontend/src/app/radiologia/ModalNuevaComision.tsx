import { useId, useState } from 'react'
import { AlertCircle } from 'lucide-react'
import { Button, Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, Input } from '@/design-system'
import { useSesion } from '@/shared/auth'
import { formatSoles } from '@/shared/format'
import { BuscadorPaciente } from '../agenda/BuscadorPaciente'
import { nombrePaciente, type Paciente } from '../agenda/api-agenda'
import { useServiciosCatalogo } from '../tratamientos/api-tratamientos'
import type { ComisionRadiologicaCreateRequestDTO, DoctorDerivadorDTO } from './types'

const SEDE_VILLAMAX = 'sede-villamax-chilca'

interface ModalNuevaComisionProps {
  abierto: boolean
  onCerrar: () => void
  doctores: DoctorDerivadorDTO[]
  onGuardar: (req: ComisionRadiologicaCreateRequestDTO) => Promise<void>
}

/**
 * Derivación de un doctor externo: paciente, estudio del catálogo de Villamax y doctor que lo envió. El precio es la
 * tarifa del catálogo y la comisión la calcula el servidor con el porcentaje del estudio.
 */
export function ModalNuevaComision({ abierto, onCerrar, doctores, onGuardar }: ModalNuevaComisionProps) {
  const uid = useId()
  const sedeActiva = useSesion()?.sedeActivaId
  const sedeId = sedeActiva ?? SEDE_VILLAMAX
  const estudios = useServiciosCatalogo(undefined, abierto)
  const [paciente, setPaciente] = useState<Paciente | null>(null)
  const [estudioId, setEstudioId] = useState('')
  const [doctorDerivadorId, setDoctorDerivadorId] = useState('')
  const [cantidad, setCantidad] = useState('1')
  const [esEstudiante, setEsEstudiante] = useState(false)
  const [precioManual, setPrecioManual] = useState('')
  const [nota, setNota] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Los doctores llegan después de montar el modal: mientras no elijas otro, vale el primero.
  const doctorElegido = doctorDerivadorId || doctores[0]?.id || ''

  const estudio = estudios.data?.find((e) => e.id === estudioId)
  const unidades = Math.max(1, Math.floor(Number(cantidad) || 1))
  const precio = estudio?.precio != null ? estudio.precio * unidades : null
  // Monto fijo por estudio × cantidad; si el estudio no tiene monto fijo, el porcentaje del precio.
  const comision = esEstudiante
    ? 0
    : estudio?.comisionFija != null
      ? estudio.comisionFija * unidades
      : precio != null && estudio?.comisionPorcentaje != null
        ? (precio * estudio.comisionPorcentaje) / 100
        : null

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!paciente) return setError('Elige al paciente o regístralo.')
    if (!estudio) return setError('Elige el estudio radiológico.')
    if (!doctorElegido) return setError('Elige al doctor que derivó al paciente.')
    if (!Number.isInteger(Number(cantidad)) || Number(cantidad) < 1) return setError('La cantidad de estudios debe ser al menos 1.')
    if (esEstudiante && !(Number(precioManual) > 0)) return setError('Para un paciente estudiante indica el precio convenido.')
    try {
      setGuardando(true)
      setError(null)
      await onGuardar({
        doctorDerivadorId: doctorElegido,
        pacienteId: paciente.id,
        pacienteNombre: nombrePaciente(paciente),
        estudioId,
        cantidad: unidades,
        esEstudiante,
        precioCobrado: esEstudiante ? Number(precioManual) : undefined,
        nota: nota.trim() || undefined,
        sedeId,
      })
      onCerrar()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo registrar la derivación.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Registrar Comisión de Derivación</DialogTitle>
          <DialogDescription>
            Un odontólogo externo envió al paciente con su orden. La comisión queda pendiente hasta que se liquide.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4 pt-2">
          {error && (
            <div role="alert" className="flex items-center gap-2 rounded-panel bg-error-container/20 p-3 text-xs text-error">
              <AlertCircle className="size-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex flex-col gap-1">
            <span className="text-xs font-bold text-on-surface">Paciente *</span>
            <BuscadorPaciente sedeId={sedeId} seleccionado={paciente} onSeleccionar={setPaciente} />
          </div>

          <div>
            <label htmlFor={`${uid}-estudio`} className="mb-1 block text-xs font-bold text-on-surface">Estudio radiológico *</label>
            <select
              id={`${uid}-estudio`}
              value={estudioId}
              onChange={(e) => setEstudioId(e.target.value)}
              className="w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-focus"
            >
              <option value="">{estudios.isPending ? 'Cargando estudios…' : 'Elige un estudio'}</option>
              {estudios.data?.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.nombre}
                </option>
              ))}
            </select>
            {estudio && (
              <p className="mt-1 text-xs text-on-surface-variant">
                Tarifa {precio != null ? formatSoles(precio) : 'sin precio'}
                {esEstudiante
                  ? ' · estudiante con convenio: sin comisión'
                  : comision != null &&
                    (estudio.comisionFija != null
                      ? ` · comisión ${formatSoles(estudio.comisionFija)} × ${unidades} = ${formatSoles(comision)}`
                      : ` · comisión ${estudio.comisionPorcentaje} % = ${formatSoles(comision)}`)}
              </p>
            )}
          </div>

          <div>
            <label htmlFor={`${uid}-cantidad`} className="mb-1 block text-xs font-bold text-on-surface">Cantidad de estudios</label>
            <Input id={`${uid}-cantidad`} type="number" min={1} max={200} step={1} inputMode="numeric" value={cantidad} onChange={(e) => setCantidad(e.target.value)} />
          </div>

          <div className="flex flex-col gap-2 rounded-panel border border-outline-variant/50 p-3">
            <label className="flex items-center gap-2 text-sm font-semibold text-on-surface">
              <input type="checkbox" checked={esEstudiante} onChange={(e) => setEsEstudiante(e.target.checked)} className="size-4" />
              Paciente estudiante (convenio)
            </label>
            {esEstudiante && (
              <div>
                <label htmlFor={`${uid}-precio-manual`} className="mb-1 block text-xs font-bold text-on-surface">Precio convenido (S/) *</label>
                <Input id={`${uid}-precio-manual`} type="number" min={0} step="0.01" inputMode="decimal" value={precioManual} onChange={(e) => setPrecioManual(e.target.value)} />
                <p className="mt-1 text-xs text-on-surface-variant">El precio de estudiantes es menor y se escribe a mano. No genera comisión.</p>
              </div>
            )}
          </div>

          <div>
            <label htmlFor={`${uid}-derivador`} className="mb-1 block text-xs font-bold text-on-surface">Doctor derivador *</label>
            <select
              id={`${uid}-derivador`}
              value={doctorElegido}
              onChange={(e) => setDoctorDerivadorId(e.target.value)}
              className="w-full rounded-control border border-outline-variant/60 bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus-visible:outline-2 focus-visible:outline-focus"
            >
              {doctores.length === 0 ? (
                <option value="">No hay doctores registrados aún</option>
              ) : (
                doctores.map((doc) => (
                  <option key={doc.id} value={doc.id}>
                    {doc.nombre} {doc.cop ? `(${doc.cop})` : ''} - {doc.clinicaOrigen ?? 'Consultorio Particular'}
                  </option>
                ))
              )}
            </select>
          </div>

          <div>
            <label htmlFor={`${uid}-nota`} className="mb-1 block text-xs font-bold text-on-surface">Nota (opcional)</label>
            <Input id={`${uid}-nota`} value={nota} onChange={(e) => setNota(e.target.value)} />
          </div>

          <div className="flex items-center justify-end gap-2">
            <Button type="button" variant="secundario" onClick={onCerrar} disabled={guardando}>
              Cancelar
            </Button>
            <Button type="submit" variant="accion" disabled={guardando || doctores.length === 0}>
              {guardando ? 'Registrando...' : 'Registrar derivación'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
