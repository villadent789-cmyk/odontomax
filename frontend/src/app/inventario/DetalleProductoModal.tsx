import {
  ArrowDownLeft,
  ArrowUpRight,
  SlidersHorizontal,
  ArrowRightLeft,
  Building2,
  Clock,
} from 'lucide-react'
import { Badge } from '@/design-system/components/Badge'
import { Button } from '@/design-system/components/Button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/design-system/components/Dialog'
import { Skeleton } from '@/design-system/components/Skeleton'
import { formatFecha, formatFechaHora, formatSoles } from '@/shared/format'
import { useMovimientosInventario, type ProductoInventarioDTO } from './api-inventario'
import { VencimientoProducto } from './VencimientoProducto'

interface DetalleProductoModalProps {
  abierto: boolean
  onCerrar: () => void
  producto: ProductoInventarioDTO | null
  sedesDisponibles: Array<{ id: string; nombre: string }>
  onAbrirMovimiento: (tipo: 'ENTRADA' | 'SALIDA' | 'AJUSTE') => void
  onAbrirTransferencia: () => void
}

export function DetalleProductoModal({
  abierto,
  onCerrar,
  producto,
  sedesDisponibles,
  onAbrirMovimiento,
  onAbrirTransferencia,
}: DetalleProductoModalProps) {
  const { data: movimientos = [], isLoading: cargandoMovimientos } = useMovimientosInventario(
    producto?.id,
    undefined
  )

  if (!producto) return null

  return (
    <Dialog open={abierto} onOpenChange={(open) => !open && onCerrar()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <span className="font-mono text-label-md font-bold text-primary">
              {producto.codigo}
            </span>
            <Badge
              tono={producto.stockTotal <= 0 ? 'peligro' : producto.alertaStockBajo ? 'accion' : 'primario'}
              punto
            >
              {producto.stockTotal <= 0 ? 'Agotado' : producto.alertaStockBajo ? 'Stock Bajo' : 'Óptimo'}
            </Badge>
          </div>
          <DialogTitle>{producto.nombre}</DialogTitle>
          <DialogDescription>
            {producto.categoria || 'Insumo'} • Presentación: {producto.unidad || 'Unidad'}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-6 py-2">
          {/* Fila de datos clave */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <div className="rounded-xl border border-outline-variant/30 bg-surface-container/40 p-3">
              <span className="text-label-sm text-on-surface-variant">Stock Total</span>
              <p className="font-display text-headline-sm font-bold text-on-surface">
                {producto.stockTotal}
              </p>
            </div>
            <div className="rounded-xl border border-outline-variant/30 bg-surface-container/40 p-3">
              <span className="text-label-sm text-on-surface-variant">Stock Mínimo</span>
              <p className="font-display text-headline-sm font-bold text-on-surface">
                {producto.stock_minimo}
              </p>
            </div>
            <div className="rounded-xl border border-outline-variant/30 bg-surface-container/40 p-3">
              <span className="text-label-sm text-on-surface-variant">Precio Ref.</span>
              <p className="font-display text-headline-sm font-bold text-on-surface">
                {producto.precio_unitario != null ? formatSoles(producto.precio_unitario) : '—'}
              </p>
            </div>
            <div className="rounded-xl border border-outline-variant/30 bg-surface-container/40 p-3">
              <span className="text-label-sm text-on-surface-variant">Estado</span>
              <p className="font-display text-title-md font-bold text-on-surface">
                {producto.activo ? 'Activo' : 'De baja'}
              </p>
            </div>
            <div className="rounded-xl border border-outline-variant/30 bg-surface-container/40 p-3">
              <span className="text-label-sm text-on-surface-variant">Próximo vencimiento</span>
              <p className="mt-1">
                <VencimientoProducto fecha={producto.proximoVencimiento} />
              </p>
            </div>
            <div className="rounded-xl border border-outline-variant/30 bg-surface-container/40 p-3">
              <span className="text-label-sm text-on-surface-variant">Última entrega</span>
              <p className="font-display text-title-md font-bold text-on-surface">
                {formatFecha(producto.ultimaEntrega)}
              </p>
            </div>
          </div>

          {/* Existencias por sede clínica */}
          <div className="flex flex-col gap-2.5">
            <h4 className="flex items-center gap-2 font-display text-title-sm font-bold text-primary">
              <Building2 className="size-4" />
              Disponibilidad por Sede Clínica
            </h4>
            <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
              {sedesDisponibles.map((sede) => {
                const cant = producto.stockPorSede?.[sede.id] ?? 0
                const bajo = cant <= producto.stock_minimo
                const agot = cant <= 0

                return (
                  <div
                    key={sede.id}
                    className="flex flex-col justify-between rounded-xl border border-outline-variant/40 bg-surface-container-lowest p-3 shadow-sm"
                  >
                    <div>
                      <span className="text-body-sm font-semibold text-on-surface line-clamp-1">
                        {sede.nombre}
                      </span>
                      <span className="text-label-sm text-on-surface-variant">
                        ID: {sede.id}
                      </span>
                    </div>

                    <div className="mt-3 flex items-end justify-between">
                      <span className="text-headline-sm font-bold text-on-surface">
                        {cant}
                      </span>
                      <Badge
                        tono={agot ? 'peligro' : bajo ? 'accion' : 'primario'}
                        punto
                        className="text-xs"
                      >
                        {agot ? 'Agotado' : bajo ? 'Bajo' : 'OK'}
                      </Badge>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Historial de movimientos del insumo */}
          <div className="flex flex-col gap-2.5">
            <h4 className="flex items-center gap-2 font-display text-title-sm font-bold text-primary">
              <Clock className="size-4" />
              Historial de Movimientos
            </h4>
            {cargandoMovimientos ? (
              <div className="flex flex-col gap-2">
                <Skeleton className="h-10 w-full rounded-xl" />
                <Skeleton className="h-10 w-full rounded-xl" />
              </div>
            ) : movimientos.length === 0 ? (
              <p className="py-4 text-center text-body-sm text-on-surface-variant">
                No hay movimientos registrados para este insumo.
              </p>
            ) : (
              <div className="max-h-56 overflow-y-auto rounded-xl border border-outline-variant/30 divide-y divide-outline-variant/20">
                {movimientos.map((mov) => {
                  const esEntrada = mov.tipo === 'ENTRADA' || mov.tipo === 'TRANSFERENCIA_ENTRADA'
                  const esSalida = mov.tipo === 'SALIDA' || mov.tipo === 'TRANSFERENCIA_SALIDA'

                  return (
                    <div
                      key={mov.id}
                      className="flex items-center justify-between gap-3 p-3 text-body-sm hover:bg-surface-container/20"
                    >
                      <div className="flex flex-col gap-0.5">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-on-surface">
                            {mov.tipo}
                          </span>
                          <span className="text-label-sm text-on-surface-variant">
                            • {mov.sede_id}
                          </span>
                        </div>
                        {mov.motivo && (
                          <span className="text-label-sm text-on-surface-variant">
                            Motivo: {mov.motivo}
                          </span>
                        )}
                        {(mov.lote || mov.fecha_vencimiento) && (
                          <span className="text-label-sm text-on-surface-variant">
                            {mov.lote ? `Lote: ${mov.lote} ` : ''}
                            {mov.fecha_vencimiento ? `• Vence: ${mov.fecha_vencimiento}` : ''}
                          </span>
                        )}
                      </div>

                      <div className="text-right">
                        <span
                          className={`font-bold ${
                            esEntrada
                              ? 'text-primary'
                              : esSalida
                              ? 'text-error'
                              : 'text-on-surface'
                          }`}
                        >
                          {mov.cantidad > 0 ? `+${mov.cantidad}` : mov.cantidad}
                        </span>
                        <span className="block text-label-sm text-on-surface-variant">
                          {formatFechaHora(mov.creado_en ?? '')}
                        </span>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        <DialogFooter className="flex-wrap gap-2">
          <Button
            variant="secundario"
            size="sm"
            onClick={() => onAbrirMovimiento('ENTRADA')}
            className="gap-1.5"
          >
            <ArrowDownLeft className="size-4" />
            Registrar Entrada
          </Button>
          <Button
            variant="secundario"
            size="sm"
            onClick={() => onAbrirMovimiento('SALIDA')}
            className="gap-1.5"
          >
            <ArrowUpRight className="size-4" />
            Consumo / Salida
          </Button>
          <Button
            variant="secundario"
            size="sm"
            onClick={() => onAbrirMovimiento('AJUSTE')}
            className="gap-1.5"
          >
            <SlidersHorizontal className="size-4" />
            Ajustar
          </Button>
          <Button
            variant="secundario"
            size="sm"
            onClick={onAbrirTransferencia}
            className="gap-1.5"
          >
            <ArrowRightLeft className="size-4" />
            Trasladar
          </Button>
          <Button variant="fantasma" size="sm" onClick={onCerrar}>
            Cerrar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
