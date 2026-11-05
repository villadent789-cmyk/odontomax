import { textoDeError } from '@/shared/http'
import { useState, useId } from 'react'
import { useAlCambiar } from '@/shared/useAlCambiar'
import {
  ArrowDownLeft,
  ArrowUpRight,
  SlidersHorizontal,
  Stethoscope,
  AlertCircle,
} from 'lucide-react'
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
  useRegistrarMovimientoMutation,
  type ProductoInventarioDTO,
  type InventarioMovimientoRequestDTO,
} from './api-inventario'

type TipoForm = 'ENTRADA' | 'SALIDA' | 'CONSUMO_CLINICO' | 'AJUSTE'

interface RegistrarMovimientoModalProps {
  abierto: boolean
  onCerrar: () => void
  productoInicial?: ProductoInventarioDTO | null
  tipoInicial?: 'ENTRADA' | 'SALIDA' | 'AJUSTE'
  productos: ProductoInventarioDTO[]
  sedeDefecto: string
  sedesDisponibles: Array<{ id: string; nombre: string }>
}

export function RegistrarMovimientoModal({
  abierto,
  onCerrar,
  productoInicial,
  tipoInicial = 'ENTRADA',
  productos,
  sedeDefecto,
  sedesDisponibles,
}: RegistrarMovimientoModalProps) {
  const uid = useId()
  const [productoElegido, setProductoId] = useState('')
  // Mientras no se elija insumo, se propone el primero de la lista.
  const productoId = productoElegido || (productos[0]?.id ?? '')
  const [sedeId, setSedeId] = useState(sedeDefecto)
  const [tipo, setTipo] = useState<TipoForm>(tipoInicial)
  const [tipoAjuste, setTipoAjuste] = useState<'POSITIVO' | 'NEGATIVO'>('POSITIVO')
  const [cantidad, setCantidad] = useState<string>('1')
  const [motivo, setMotivo] = useState('')
  const [lote, setLote] = useState('')
  const [fechaVencimiento, setFechaVencimiento] = useState('')
  const [fechaEntrega, setFechaEntrega] = useState('')
  const [proveedor, setProveedor] = useState('')
  const [errorValidacion, setErrorValidacion] = useState<string | null>(null)

  const registrarMutation = useRegistrarMovimientoMutation()

  // Sincronizar producto y tipo al abrir
  useAlCambiar([abierto, productoInicial?.id, tipoInicial, sedeDefecto], () => {
    if (abierto) {
      if (productoInicial) {
        setProductoId(productoInicial.id)
      }
      setTipo(tipoInicial)
      setSedeId(sedeDefecto)
      setCantidad('1')
      setMotivo('')
      setLote('')
      setFechaVencimiento('')
      setFechaEntrega('')
      setProveedor('')
      setErrorValidacion(null)
    }
  })

  const productoSeleccionado = productos.find((p) => p.id === productoId)
  const stockEnSede = productoSeleccionado?.stockPorSede?.[sedeId] ?? 0

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErrorValidacion(null)

    if (!productoId) {
      setErrorValidacion('Seleccione un insumo odontológico.')
      return
    }

    if (!sedeId) {
      setErrorValidacion('La sede es obligatoria.')
      return
    }

    const cantNum = parseInt(cantidad, 10)
    if (isNaN(cantNum) || cantNum <= 0) {
      setErrorValidacion('La cantidad debe ser un número entero mayor a cero.')
      return
    }

    // Validación de stock para salidas y consumos clínicos
    if ((tipo === 'SALIDA' || tipo === 'CONSUMO_CLINICO') && cantNum > stockEnSede) {
      setErrorValidacion(
        `Stock insuficiente en esta sede. Disponible: ${stockEnSede}, solicitado: ${cantNum}.`
      )
      return
    }

    // Validación de motivo para ajustes
    if (tipo === 'AJUSTE' && (!motivo || motivo.trim().length === 0)) {
      setErrorValidacion('El motivo o justificación es obligatorio para los ajustes de stock.')
      return
    }

    // Mapeo del tipo para el backend
    let tipoBackend: 'ENTRADA' | 'SALIDA' | 'AJUSTE'
    let cantidadBackend: number
    let motivoFinal = motivo.trim()

    if (tipo === 'ENTRADA') {
      tipoBackend = 'ENTRADA'
      cantidadBackend = cantNum
    } else if (tipo === 'SALIDA') {
      tipoBackend = 'SALIDA'
      cantidadBackend = cantNum
    } else if (tipo === 'CONSUMO_CLINICO') {
      tipoBackend = 'SALIDA'
      cantidadBackend = cantNum
      motivoFinal = motivoFinal ? `Consumo clínico: ${motivoFinal}` : 'Consumo en atención clínica'
    } else {
      // AJUSTE
      tipoBackend = 'AJUSTE'
      cantidadBackend = tipoAjuste === 'POSITIVO' ? cantNum : -cantNum

      if (stockEnSede + cantidadBackend < 0) {
        setErrorValidacion(
          `El ajuste dejaría el stock en negativo. Stock actual: ${stockEnSede}, ajuste: ${cantidadBackend}.`
        )
        return
      }
    }

    const payload: InventarioMovimientoRequestDTO = {
      producto_id: productoId,
      sede_id: sedeId,
      tipo: tipoBackend,
      cantidad: cantidadBackend,
      motivo: motivoFinal || undefined,
      lote: lote.trim() || undefined,
      fecha_vencimiento: fechaVencimiento || undefined,
      fecha_entrega: fechaEntrega || undefined,
      proveedor: proveedor.trim() || undefined,
    }

    try {
      await registrarMutation.mutateAsync(payload)
      onCerrar()
    } catch (err) {
      setErrorValidacion(textoDeError(err, 'Ocurrió un error al registrar el movimiento.'))
    }
  }

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Registrar Movimiento de Inventario</DialogTitle>
          <DialogDescription>
            Control de ingresos, salidas, consumo odontológico y ajustes de almacén
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4 py-2">
          {errorValidacion && (
            <div className="flex items-center gap-2 rounded-xl border border-error/30 bg-error-container/20 p-3 text-body-sm text-error">
              <AlertCircle className="size-4 shrink-0" />
              <span>{errorValidacion}</span>
            </div>
          )}

          {/* Selector de Insumo */}
          <div className="flex flex-col gap-1.5">
            <label htmlFor={`${uid}-insumo-odontologico-radiologico`} className="text-label-md font-semibold text-on-surface">
              Insumo Odontológico / Radiológico <span className="text-error">*</span>
            </label>
            <select
              id={`${uid}-insumo-odontologico-radiologico`}
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

          {/* Sede y Stock disponible */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <label htmlFor={`${uid}-sede-clinica`} className="text-label-md font-semibold text-on-surface">
                Sede Clínica <span className="text-error">*</span>
              </label>
              <select
                id={`${uid}-sede-clinica`}
                value={sedeId}
                onChange={(e) => setSedeId(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required
              >
                {sedesDisponibles.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.nombre}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col justify-end">
              <div className="flex h-10 items-center justify-between rounded-xl border border-outline-variant/40 bg-surface-container/40 px-3 text-body-sm">
                <span className="text-on-surface-variant">Stock en sede:</span>
                <span className="font-bold text-on-surface">{stockEnSede} uds</span>
              </div>
            </div>
          </div>

          {/* Selector de Tipo de Movimiento */}
          <div className="flex flex-col gap-1.5">
            <span id={`${uid}-tipo-operacion`} className="text-label-md font-semibold text-on-surface">
              Tipo de Operación <span className="text-error">*</span>
            </span>
            <div role="group" aria-labelledby={`${uid}-tipo-operacion`} className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              <button
                type="button"
                onClick={() => setTipo('ENTRADA')}
                className={`flex flex-col items-center gap-1 rounded-xl border p-2.5 text-center text-label-sm font-medium transition-colors ${
                  tipo === 'ENTRADA'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-outline-variant/40 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                <ArrowDownLeft className="size-4" />
                <span>Entrada</span>
              </button>

              <button
                type="button"
                onClick={() => setTipo('CONSUMO_CLINICO')}
                className={`flex flex-col items-center gap-1 rounded-xl border p-2.5 text-center text-label-sm font-medium transition-colors ${
                  tipo === 'CONSUMO_CLINICO'
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-outline-variant/40 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                <Stethoscope className="size-4" />
                <span>Consumo Clínico</span>
              </button>

              <button
                type="button"
                onClick={() => setTipo('SALIDA')}
                className={`flex flex-col items-center gap-1 rounded-xl border p-2.5 text-center text-label-sm font-medium transition-colors ${
                  tipo === 'SALIDA'
                    ? 'border-error bg-error/10 text-error'
                    : 'border-outline-variant/40 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                <ArrowUpRight className="size-4" />
                <span>Salida / Merma</span>
              </button>

              <button
                type="button"
                onClick={() => setTipo('AJUSTE')}
                className={`flex flex-col items-center gap-1 rounded-xl border p-2.5 text-center text-label-sm font-medium transition-colors ${
                  tipo === 'AJUSTE'
                    ? 'border-secondary-fixed text-on-secondary-fixed bg-secondary-fixed/20'
                    : 'border-outline-variant/40 bg-surface-container-lowest text-on-surface-variant hover:bg-surface-container'
                }`}
              >
                <SlidersHorizontal className="size-4" />
                <span>Ajuste (+/-)</span>
              </button>
            </div>
          </div>

          {/* Opciones de Ajuste si aplica */}
          {tipo === 'AJUSTE' && (
            <div className="flex items-center gap-3 rounded-xl border border-secondary-fixed/40 bg-surface-container/40 p-3 text-body-sm">
              <span className="text-on-surface-variant font-medium">Sentido del ajuste:</span>
              <label className="flex items-center gap-1.5 cursor-pointer text-on-surface">
                <input
                  type="radio"
                  name="sentidoAjuste"
                  checked={tipoAjuste === 'POSITIVO'}
                  onChange={() => setTipoAjuste('POSITIVO')}
                  className="accent-primary"
                />
                Incremento (+)
              </label>
              <label className="flex items-center gap-1.5 cursor-pointer text-on-surface">
                <input
                  type="radio"
                  name="sentidoAjuste"
                  checked={tipoAjuste === 'NEGATIVO'}
                  onChange={() => setTipoAjuste('NEGATIVO')}
                  className="accent-primary"
                />
                Reducción (-)
              </label>
            </div>
          )}

          {/* Cantidad y Motivo */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="flex flex-col gap-1.5 sm:col-span-1">
              <label htmlFor={`${uid}-cantidad`} className="text-label-md font-semibold text-on-surface">
                Cantidad <span className="text-error">*</span>
              </label>
              <input
                id={`${uid}-cantidad`}
                type="number"
                min="1"
                step="1"
                value={cantidad}
                onChange={(e) => setCantidad(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required
              />
            </div>

            <div className="flex flex-col gap-1.5 sm:col-span-2">
              <label className="text-label-md font-semibold text-on-surface">
                Motivo / Justificación {tipo === 'AJUSTE' && <span className="text-error">*</span>}
              </label>
              <input
                type="text"
                placeholder={
                  tipo === 'AJUSTE'
                    ? 'Obligatorio: Ej. Conteo físico mensual, rotura...'
                    : tipo === 'CONSUMO_CLINICO'
                    ? 'Ej. Sillón 2, Dr. Ríos, atención ortodoncia'
                    : 'Ej. Recepción de factura F001-492'
                }
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                required={tipo === 'AJUSTE'}
              />
            </div>
          </div>

          {/* Campos clínicos / logísticos opcionales para entradas */}
          {tipo === 'ENTRADA' && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <div className="flex flex-col gap-1.5">
                <label htmlFor={`${uid}-lote`} className="text-label-md font-semibold text-on-surface-variant">
                  Lote
                </label>
                <input
                  id={`${uid}-lote`}
                  type="text"
                  placeholder="Ej. L-2026-A"
                  value={lote}
                  onChange={(e) => setLote(e.target.value)}
                  className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor={`${uid}-vencimiento`} className="text-label-md font-semibold text-on-surface-variant">
                  Vencimiento
                </label>
                <input
                  id={`${uid}-vencimiento`}
                  type="date"
                  value={fechaVencimiento}
                  onChange={(e) => setFechaVencimiento(e.target.value)}
                  className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor={`${uid}-entrega`} className="text-label-md font-semibold text-on-surface-variant">
                  Fecha de Entrega
                </label>
                <input
                  id={`${uid}-entrega`}
                  type="date"
                  value={fechaEntrega}
                  onChange={(e) => setFechaEntrega(e.target.value)}
                  className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor={`${uid}-proveedor`} className="text-label-md font-semibold text-on-surface-variant">
                  Proveedor
                </label>
                <input
                  id={`${uid}-proveedor`}
                  type="text"
                  placeholder="Ej. Dental Huancayo SAC"
                  value={proveedor}
                  onChange={(e) => setProveedor(e.target.value)}
                  className="h-10 rounded-xl border border-outline-variant/60 bg-surface-container-lowest px-3 text-body-sm text-on-surface focus:border-primary focus:outline-none"
                />
              </div>
            </div>
          )}

          <DialogFooter className="mt-2">
            <Button variant="fantasma" type="button" onClick={onCerrar} disabled={registrarMutation.isPending}>
              Cancelar
            </Button>
            <Button variant="primario" type="submit" disabled={registrarMutation.isPending}>
              {registrarMutation.isPending ? 'Guardando...' : 'Confirmar Movimiento'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
