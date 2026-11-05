import { textoDeError } from '@/shared/http'
import { useState, useId } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import { AlertCircle } from 'lucide-react'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/design-system/components/Dialog'
import {
  useSolicitarTransferenciaMutation,
  type ProductoInventarioDTO,
  type TransferenciaCreateRequestDTO,
} from './api-inventario'

interface SolicitarTransferenciaModalProps {
  abierto: boolean
  onCerrar: () => void
  productoInicial?: ProductoInventarioDTO | null
  productos: ProductoInventarioDTO[]
  sedeOrigenDefecto: string
  sedesDisponibles: Array<{ id: string; nombre: string }>
}

export function SolicitarTransferenciaModal({
  abierto,
  onCerrar,
  productoInicial,
  productos,
  sedeOrigenDefecto,
  sedesDisponibles,
}: SolicitarTransferenciaModalProps) {
  const uid = useId()
  const [productoElegido, setProductoId] = useState('')
  // Mientras no se elija insumo, se propone el primero de la lista.
  const productoId = productoElegido || (productos[0]?.id ?? '')
  const [sedeOrigenId, setSedeOrigenId] = useState(sedeOrigenDefecto)
  const [sedeDestinoId, setSedeDestinoId] = useState('')
  const [cantidad, setCantidad] = useState('1')
  const [motivo, setMotivo] = useState('')
  const [errorValidacion, setErrorValidacion] = useState<string | null>(null)

  const solicitarMutation = useSolicitarTransferenciaMutation()

  const clavesSedes = sedesDisponibles.map((s) => s.id).join('|')
  useAlCambiar([abierto, productoInicial?.id, sedeOrigenDefecto, clavesSedes], () => {
    if (abierto) {
      if (productoInicial) {
        setProductoId(productoInicial.id)
      }

      setSedeOrigenId(sedeOrigenDefecto)
      // Escoger una sede de destino diferente a la de origen
      const otraSede = sedesDisponibles.find((s) => s.id !== sedeOrigenDefecto)
      setSedeDestinoId(otraSede?.id || '')
      setCantidad('1')
      setMotivo('')
      setErrorValidacion(null)
    }
  })

  const productoSeleccionado = productos.find((p) => p.id === productoId)
  const stockOrigen = productoSeleccionado?.stockPorSede?.[sedeOrigenId] ?? 0

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorValidacion(null)

    if (!productoId) {
      setErrorValidacion('Seleccione el insumo a trasladar.')
      return
    }

    if (sedeOrigenId === sedeDestinoId) {
      setErrorValidacion('La sede de origen y la de destino deben ser diferentes.')
      return
    }

    const cantNum = parseInt(cantidad, 10)
    if (isNaN(cantNum) || cantNum <= 0) {
      setErrorValidacion('La cantidad debe ser mayor a cero.')
      return
    }

    if (cantNum > stockOrigen) {
      setErrorValidacion(
        `Stock insuficiente en la sede de origen (${sedeOrigenId}). Stock actual: ${stockOrigen}, solicitado: ${cantNum}.`
      )
      return
    }

    const payload: TransferenciaCreateRequestDTO = {
      producto_id: productoId,
      sede_origen_id: sedeOrigenId,
      sede_destino_id: sedeDestinoId,
      cantidad: cantNum,
      motivo: motivo.trim() || undefined,
    }

    try {
      await solicitarMutation.mutateAsync(payload)
      onCerrar()
    } catch (err) {
      setErrorValidacion(textoDeError(err, 'Ocurrió un error al solicitar la transferencia.'))
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Solicitar Traslado entre Sedes</DialogTitle>
          <DialogDescription>
            Envío de insumos e instrumental odontológico entre sedes de OdontoMax
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4 py-2">
          {errorValidacion && (
            <div className="flex items-center gap-2 rounded-xl border border-error/30 bg-error-container/20 p-3 text-body-sm text-error">
              <AlertCircle className="size-4 shrink-0" />
              <span>{errorValidacion}</span>
            </div>
          )}

          {/* Insumo a trasladar */}
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${uid}-insumo`} className="text-label-md font-semibold text-on-surface">
              Insumo <span className="text-error">*</span>
            </label>
            <select
              id={`${uid}-insumo`}
              value={productoId}
              onChange={(e) => setProductoId(e.target.value)}
              className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
              required
            >
              {productos.map((prod) => (
                <option key={prod.id} value={prod.id}>
                  {prod.codigo} - {prod.nombre} ({prod.unidad || 'Uds'})
                </option>
              ))}
            </select>
          </div>

          {/* Sede Origen y Sede Destino */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${uid}-sede-de-origen-despacha`} className="text-label-md font-semibold text-on-surface">
                Sede de Origen (Despacha) <span className="text-error">*</span>
              </label>
              <select
                id={`${uid}-sede-de-origen-despacha`}
                value={sedeOrigenId}
                onChange={(e) => setSedeOrigenId(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required
              >
                {sedesDisponibles.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nombre}
                  </option>
                ))}
              </select>
              <span className="text-label-sm text-on-surface-variant">
                Disponible en origen: <strong>{stockOrigen} uds</strong>
              </span>
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${uid}-sede-de-destino-recibe`} className="text-label-md font-semibold text-on-surface">
                Sede de Destino (Recibe) <span className="text-error">*</span>
              </label>
              <select
                id={`${uid}-sede-de-destino-recibe`}
                value={sedeDestinoId}
                onChange={(e) => setSedeDestinoId(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required
              >
                {sedesDisponibles
                  .filter((s) => s.id !== sedeOrigenId)
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.nombre}
                    </option>
                  ))}
              </select>
            </div>
          </div>

          {/* Cantidad y Motivo */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="flex flex-col gap-1.5 sm:col-span-1">
              <label htmlFor={`${uid}-cantidad-a-trasladar`} className="text-label-md font-semibold text-on-surface">
                Cantidad a trasladar <span className="text-error">*</span>
              </label>
              <input
                id={`${uid}-cantidad-a-trasladar`}
                type="number"
                min="1"
                max={stockOrigen > 0 ? stockOrigen : 1}
                step="1"
                value={cantidad}
                onChange={(e) => setCantidad(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required
              />
            </div>

            <div className="flex flex-col gap-1.5 sm:col-span-2">
              <label htmlFor={`${uid}-motivo-justificacion`} className="text-label-md font-semibold text-on-surface">
                Motivo / Justificación
              </label>
              <input
                id={`${uid}-motivo-justificacion`}
                type="text"
                placeholder="Ej. Reabastecimiento urgente de sillón 3"
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
              />
            </div>
          </div>

          <DialogFooter className="mt-2">
            <Button
              variant="fantasma"
              type="button"
              onClick={onCerrar}
              disabled={solicitarMutation.isPending}
            >
              Cancelar
            </Button>
            <Button
              variant="primario"
              type="submit"
              disabled={solicitarMutation.isPending || stockOrigen <= 0}
            >
              {solicitarMutation.isPending ? 'Enviando solicitud...' : 'Iniciar Traslado'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
